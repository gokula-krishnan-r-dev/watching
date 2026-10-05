import SwiftUI

/// Cold-start role router keying the root navigation flow.
/// Reconciles persisted session on cold-start and orchestrates onboarding, auth, and pairing.
/// Mirrors `com.meritscreen.app.navigation.RoleGate` in Android.
public struct RoleGateView: View {
    public enum ActiveFlow: Equatable {
        case none
        case parentOnboarding
        case parentSignIn
        case parentOtp(email: String)
        case parentPairing(childId: String)
        case childPairing
        case childPermissionsOnboarding
    }

    @State private var currentRole: DeviceRole = .unassigned
    @State private var showingSplash: Bool
    @State private var activeFlow: ActiveFlow
    @State private var pendingChildCredential: ChildPairingCredential?

    private let parentSessionRepo = ParentSessionRepository.shared
    private let childPairingStore = ChildPairingStore.shared

    public init() {
        let isTesting = ProcessInfo.processInfo.arguments.contains("-testParentOnboarding") ||
            ProcessInfo.processInfo.environment["TEST_ONBOARDING"] != nil
        let isTestingParentDashboard = ProcessInfo.processInfo.arguments.contains("-testParentDashboard") ||
            ProcessInfo.processInfo.environment["TEST_PARENT_DASHBOARD"] != nil

        let isTestingChildPairing = ProcessInfo.processInfo.arguments.contains("-testChildPairing") ||
            ProcessInfo.processInfo.environment["TEST_CHILD_PAIRING"] != nil
        let isTestingChildHub = ProcessInfo.processInfo.arguments.contains("-testChildHub") ||
            ProcessInfo.processInfo.environment["TEST_CHILD_HUB"] != nil

        if isTestingParentDashboard {
            _showingSplash = State(initialValue: false)
            _currentRole = State(initialValue: .parent)
            _activeFlow = State(initialValue: .none)
            if ParentSessionRepository.shared.current() == nil {
                ParentSessionRepository.shared.set(ParentSession(
                    uid: "parent_demo",
                    familyId: "sample_family",
                    childIds: ["child_1"]
                ))
            }
            Task {
                if let user = try? await FirebaseAuthClient.shared.ensureAuthenticatedParent() {
                    let famId = (try? await FirestoreFamilyStore.shared.getUserFamilyId(uid: user.uid)) ?? "sample_family"
                    ParentSessionRepository.shared.set(ParentSession(
                        uid: user.uid,
                        familyId: famId,
                        childIds: ["child_1"]
                    ))
                }
            }
        } else if isTestingChildHub {
            _showingSplash = State(initialValue: false)
            _currentRole = State(initialValue: .child)
            _activeFlow = State(initialValue: .none)
            if ChildPairingStore.shared.current() == nil {
                ChildPairingStore.shared.set(ChildPairingCredential(
                    familyId: "sample_family",
                    childId: "child_demo",
                    deviceId: "device_demo",
                    parentPinHash: "1234",
                    displayName: "Alex",
                    ageBand: "elementary"
                ))
            }
            if ProcessInfo.processInfo.arguments.contains("-testDailyLimitLock") {
                var pol = ChildLocalStore.shared.getCachedPolicy()
                pol.dailyCeilingMinutes = 30
                ChildLocalStore.shared.saveCachedPolicy(pol)

                var snap = ChildLocalStore.shared.getSessionSnapshot()
                snap.minutesUsedToday = 45
                snap.phase = .dailyLimitLock
                snap.quizGraceUntilEpochMs = nil
                ChildLocalStore.shared.saveSessionSnapshot(snap)
            } else if ProcessInfo.processInfo.arguments.contains("-resetLimits") {
                var snap = ChildLocalStore.shared.getSessionSnapshot()
                snap.minutesUsedToday = 5
                snap.phase = .idle
                ChildLocalStore.shared.saveSessionSnapshot(snap)
            }
        } else if isTestingChildPairing {
            _showingSplash = State(initialValue: false)
            _currentRole = State(initialValue: .unassigned)
            _activeFlow = State(initialValue: .childPairing)
        } else if ProcessInfo.processInfo.arguments.contains("-testParentSignIn") {
            _showingSplash = State(initialValue: false)
            _currentRole = State(initialValue: .unassigned)
            _activeFlow = State(initialValue: .parentSignIn)
        } else if ProcessInfo.processInfo.arguments.contains("-testRoleSelect") {
            _showingSplash = State(initialValue: false)
            _currentRole = State(initialValue: .unassigned)
            _activeFlow = State(initialValue: .none)
        } else if isTesting {
            _showingSplash = State(initialValue: false)
            _activeFlow = State(initialValue: .parentOnboarding)
        } else {
            _showingSplash = State(initialValue: true)
            _activeFlow = State(initialValue: .none)
        }
    }

    public var body: some View {
        Group {
            if showingSplash {
                S00_SplashView {
                    reconcileRole()
                    showingSplash = false
                }
            } else {
                switch currentRole {
                case .unassigned:
                    switch activeFlow {
                    case .none:
                        S01_RoleSelectView(
                            onSelectParent: {
                                activeFlow = .parentSignIn
                            },
                            onSelectChild: {
                                activeFlow = .childPairing
                            },
                            onSignIn: {
                                activeFlow = .parentSignIn
                            }
                        )

                    case .parentOnboarding:
                        OnboardingWizardView(
                            onFinished: { draft in
                                Task {
                                    await commitOnboardingDraftAndLaunch(draft)
                                }
                            },
                            onCancel: {
                                activeFlow = .none
                            }
                        )

                    case .parentSignIn:
                        P02_SignInView(
                            onCodeSent: { email in
                                activeFlow = .parentOtp(email: email)
                            },
                            onSignedIn: { authResult in
                                handleAuthSuccess(authResult)
                            },
                            onBack: {
                                activeFlow = .none
                            }
                        )

                    case .parentOtp(let email):
                        P03_OtpVerificationView(
                            email: email,
                            onVerified: { authResult in
                                handleAuthSuccess(authResult)
                            },
                            onChangeEmail: {
                                activeFlow = .parentSignIn
                            }
                        )

                    case .parentPairing(let childId):
                        P08_ParentPairingView(
                            childId: childId,
                            onFinished: {
                                currentRole = .parent
                                activeFlow = .none
                            },
                            onBack: {
                                currentRole = .parent
                                activeFlow = .none
                            }
                        )

                    case .childPairing:
                        C01_ChildPairingView(
                            onPaired: { credential in
                                // Route to mandatory permissions onboarding before hub
                                pendingChildCredential = credential
                                activeFlow = .childPermissionsOnboarding
                            },
                            onBack: {
                                activeFlow = .none
                            }
                        )

                    case .childPermissionsOnboarding:
                        if let credential = pendingChildCredential {
                            C02b_PermissionsOnboardingView(
                                credential: credential,
                                policy: ChildLocalStore.shared.getCachedPolicy(),
                                appRules: ChildLocalStore.shared.getCachedAppRules(),
                                onComplete: {
                                    currentRole = .child
                                    activeFlow = .none
                                    pendingChildCredential = nil
                                }
                            )
                        } else {
                            // Safety fallback: no credential available
                            C01_ChildPairingView(
                                onPaired: { credential in
                                    pendingChildCredential = credential
                                    activeFlow = .childPermissionsOnboarding
                                },
                                onBack: { activeFlow = .none }
                            )
                        }

                    }

                case .parent:
                    ParentMainTabView(
                        initialTab: ProcessInfo.processInfo.arguments.contains("-testReports") ? .reports : .home,
                        onSignOut: {
                            SignOutParentUseCase().execute()
                            currentRole = .unassigned
                            activeFlow = .none
                        }
                    )

                case .child:
                    C05_ChildHubView {
                        childPairingStore.clear()
                        currentRole = .unassigned
                        activeFlow = .none
                    }
                }
            }
        }
        .meritAnimation(.easeInOut(duration: 0.25), value: showingSplash)
        .meritAnimation(.easeInOut(duration: 0.25), value: activeFlow)
        .meritAnimation(.easeInOut(duration: 0.25), value: currentRole)
    }

    private func reconcileRole() {
        let isTesting = ProcessInfo.processInfo.arguments.contains("-testParentOnboarding") ||
            ProcessInfo.processInfo.environment["TEST_ONBOARDING"] != nil
        if isTesting {
            currentRole = .unassigned
            activeFlow = .parentOnboarding
            return
        }
        if childPairingStore.current() != nil {
            currentRole = .child
        } else if parentSessionRepo.current() != nil {
            currentRole = .parent
        } else {
            currentRole = .unassigned
        }
    }

    private func handleAuthSuccess(_ result: ParentAuthResult) {
        if result.needsOnboarding {
            // First time user or no children yet: launch onboarding wizard with child creation
            activeFlow = .parentOnboarding
        } else {
            // Existing parent with children: navigate directly to home page
            currentRole = .parent
            activeFlow = .none
        }
    }

    private func commitOnboardingDraftAndLaunch(_ draft: OnboardingDraft) async {
        guard let user = FirebaseAuthClient.shared.currentUser else {
            activeFlow = .parentSignIn
            return
        }

        do {
            let uid = user.uid
            let email = user.email

            // 1. Check if user already has an existing family
            let existingFamilyId = try? await FirestoreFamilyStore.shared.getUserFamilyId(uid: uid)
            let familyId: String
            var childIds: [String] = []

            let pinHash = draft.parentPinHash ?? Pbkdf2PinHasher.shared.hash(pin: "1234")

            if let existingFamilyId, !existingFamilyId.isEmpty, existingFamilyId != "sample_family" {
                familyId = existingFamilyId
                try? await FirestoreFamilyStore.shared.updateParentPinHash(familyId: familyId, pinHash: pinHash)
                for child in draft.children {
                    let createdChild = try await FirestoreParentControlStore.shared.addChild(
                        familyId: familyId,
                        child: FamilyDraftChild(
                            localId: child.localId,
                            name: child.name,
                            ageBand: child.ageBand,
                            avatar: child.avatar,
                            language: child.language.isEmpty ? "en" : child.language
                        )
                    )
                    childIds.append(createdChild.childId)
                }
            } else {
                let familyDraft = FamilyDraft(
                    familyName: draft.familyName.isEmpty ? "My Family" : draft.familyName,
                    parentPinHash: pinHash,
                    children: draft.children.map { child in
                        FamilyDraftChild(
                            localId: child.localId,
                            name: child.name,
                            ageBand: child.ageBand,
                            avatar: child.avatar,
                            language: child.language.isEmpty ? "en" : child.language
                        )
                    }
                )
                let created = try await FirestoreFamilyStore.shared.createFamilyFromDraft(
                    uid: uid,
                    email: email,
                    draft: familyDraft
                )
                familyId = created.familyId
                childIds = created.children.map { $0.childId }
            }

            for childId in childIds {
                var policy = ChildPolicy()
                policy.dailyCeilingMinutes = draft.dailyBudgetMinutes
                policy.bedtimeEnabled = draft.bedtimeEnabled
                policy.bedtimeStartLabel = draft.bedtimeStart
                policy.bedtimeEndLabel = draft.bedtimeEnd
                policy.defaultCooldownMinutes = draft.cooldownMinutes
                try? await FirestoreParentControlStore.shared.updatePolicy(familyId: familyId, childId: childId, policy: policy)
            }

            let session = ParentSession(
                uid: uid,
                familyId: familyId,
                childIds: childIds
            )
            parentSessionRepo.set(session)
            OnboardingDraftRepository.shared.clear()

            currentRole = .parent
            activeFlow = .none
        } catch {
            print("[RoleGateView] Warning: Failed to commit onboarding draft to Firestore: \(error.localizedDescription)")
            currentRole = .parent
            activeFlow = .none
        }
    }
}
