import Foundation
import FirebaseFirestore

/// Production Firestore client for uploading child screen time usage records and quiz attempts.
public final class FirestoreUsageUploadClient: @unchecked Sendable, UsageUploadClientProtocol {
    public static let shared = FirestoreUsageUploadClient()

    private let firestore: Firestore

    public init(firestore: Firestore = Firestore.firestore()) {
        self.firestore = firestore
    }

    public func uploadUsage(
        familyId: String,
        childId: String,
        dateString: String,
        minutesUsed: Int,
        appMinutes: [String: Int]
    ) async throws {
        let payload: [String: Any] = [
            "day": dateString,
            "minutesUsed": minutesUsed,
            "appMinutes": appMinutes,
            "lastUpdatedAt": FieldValue.serverTimestamp()
        ]

        try await firestore.collection("families")
            .document(familyId)
            .collection("children")
            .document(childId)
            .collection("usageDays")
            .document(dateString)
            .setData(payload, merge: true)
    }

    public func uploadQuizAttempt(
        familyId: String,
        childId: String,
        attempt: RemoteQuizAttempt
    ) async throws {
        let payload: [String: Any] = [
            "attemptId": attempt.attemptId,
            "appId": attempt.appId,
            "passed": attempt.passed,
            "scorePercent": attempt.scorePercent,
            "questionsAnswered": attempt.questionsAnswered,
            "ageBand": attempt.ageBand,
            "completedAt": FieldValue.serverTimestamp()
        ]

        try await firestore.collection("families")
            .document(familyId)
            .collection("children")
            .document(childId)
            .collection("quizAttempts")
            .document(attempt.attemptId)
            .setData(payload, merge: true)
    }
}
