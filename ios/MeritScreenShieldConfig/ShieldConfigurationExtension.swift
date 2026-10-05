import Foundation
import ManagedSettings
import ManagedSettingsUI
import UIKit

/// Shield configuration extension providing calm, non-punitive system shield copy.
/// Displayed by iOS SpringBoard over shielded applications.
final class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    private let sharedStore = ScreenTimeSharedStore.shared

    override func configuration(shielding application: Application) -> ShieldConfiguration {
        let state = sharedStore.getShieldState()

        switch state {
        case .failLock(let deadlineEpochMs, _):
            let now = Int64(Date().timeIntervalSince1970 * 1000)
            let remainingSec = max(0, (deadlineEpochMs - now) / 1000)
            let mins = remainingSec / 60
            let timeText = mins > 0 ? "\(mins) minutes remaining" : "ending soon"

            return ShieldConfiguration(
                backgroundBlurStyle: .systemMaterial,
                backgroundColor: UIColor.systemBackground,
                title: ShieldConfiguration.Label(
                    text: "🌙 Rest Your Eyes & Brain",
                    color: UIColor.label
                ),
                subtitle: ShieldConfiguration.Label(
                    text: "Non-emergency apps are resting (\(timeText)). Stretch, look outside, or try the quiz again.",
                    color: UIColor.secondaryLabel
                ),
                primaryButtonLabel: ShieldConfiguration.Label(
                    text: "Retry Quiz Now",
                    color: UIColor.white
                ),
                primaryButtonBackgroundColor: UIColor.systemTeal,
                secondaryButtonLabel: ShieldConfiguration.Label(
                    text: "Take a Rest",
                    color: UIColor.secondaryLabel
                )
            )

        case .quizDue(let appName, let blockMinutes):
            return ShieldConfiguration(
                backgroundBlurStyle: .systemMaterial,
                backgroundColor: UIColor.systemBackground,
                title: ShieldConfiguration.Label(
                    text: "Time for a Learning Break!",
                    color: UIColor.label
                ),
                subtitle: ShieldConfiguration.Label(
                    text: "You used your \(blockMinutes)-minute block for \(appName). Take a quick 3-question quiz to unlock more time!",
                    color: UIColor.secondaryLabel
                ),
                primaryButtonLabel: ShieldConfiguration.Label(
                    text: "Take Quiz",
                    color: UIColor.white
                ),
                primaryButtonBackgroundColor: UIColor.systemTeal,
                secondaryButtonLabel: ShieldConfiguration.Label(
                    text: "Done",
                    color: UIColor.secondaryLabel
                )
            )

        case .bedtimeLock(let endLabel):
            return ShieldConfiguration(
                backgroundBlurStyle: .systemMaterialDark,
                backgroundColor: UIColor.black,
                title: ShieldConfiguration.Label(
                    text: "Goodnight 😴",
                    color: UIColor.white
                ),
                subtitle: ShieldConfiguration.Label(
                    text: "Bedtime hours are active until \(endLabel). Apps will unlock in the morning.",
                    color: UIColor.lightGray
                ),
                primaryButtonLabel: nil,
                primaryButtonBackgroundColor: nil,
                secondaryButtonLabel: nil
            )

        case .dailyLimitLock(let minutesUsed, let ceilingMinutes):
            return ShieldConfiguration(
                backgroundBlurStyle: .systemMaterialDark,
                backgroundColor: UIColor.black,
                title: ShieldConfiguration.Label(
                    text: "🌅 Daily Limit Reached",
                    color: UIColor.white
                ),
                subtitle: ShieldConfiguration.Label(
                    text: "You have used your daily screen time (\(minutesUsed)/\(ceilingMinutes)m). Apps are resting until tomorrow morning.",
                    color: UIColor.lightGray
                ),
                primaryButtonLabel: ShieldConfiguration.Label(
                    text: "Open MeritScreen Hub",
                    color: UIColor.white
                ),
                primaryButtonBackgroundColor: UIColor.systemTeal,
                secondaryButtonLabel: nil
            )

        case .none:
            return ShieldConfiguration(
                backgroundBlurStyle: .systemMaterial,
                backgroundColor: UIColor.systemBackground,
                title: ShieldConfiguration.Label(
                    text: "App Paused",
                    color: UIColor.label
                ),
                subtitle: ShieldConfiguration.Label(
                    text: "This app is supervised by MeritScreen.",
                    color: UIColor.secondaryLabel
                ),
                primaryButtonLabel: ShieldConfiguration.Label(
                    text: "Open MeritScreen",
                    color: UIColor.white
                ),
                primaryButtonBackgroundColor: UIColor.systemTeal,
                secondaryButtonLabel: nil
            )
        }
    }

    override func configuration(
        shielding application: Application,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        configuration(shielding: application)
    }

    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration {
        ShieldConfiguration(
            backgroundBlurStyle: .systemMaterial,
            backgroundColor: UIColor.systemBackground,
            title: ShieldConfiguration.Label(
                text: "Website Paused",
                color: UIColor.label
            ),
            subtitle: ShieldConfiguration.Label(
                text: "This website is paused by MeritScreen.",
                color: UIColor.secondaryLabel
            ),
            primaryButtonLabel: ShieldConfiguration.Label(
                text: "Take Quiz",
                color: UIColor.white
            ),
            primaryButtonBackgroundColor: UIColor.systemTeal,
            secondaryButtonLabel: nil
        )
    }

    override func configuration(
        shielding webDomain: WebDomain,
        in category: ActivityCategory
    ) -> ShieldConfiguration {
        configuration(shielding: webDomain)
    }
}
