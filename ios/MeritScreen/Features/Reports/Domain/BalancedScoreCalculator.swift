import Foundation

/// Calculates balanced score (0–100) representing healthy screen time and learning habits.
/// Matches Android `calculateBalancedScore` and `balancedScoreCopy`.
public enum BalancedScoreCalculator {

    public static func calculate(report: ChildReportsSnapshot, educationalPercent: Int?) -> Int? {
        if !report.hasAnyData { return nil }
        let accuracy = report.quiz.accuracyPercent
        let edu = educationalPercent

        if let accuracy, let edu {
            return min(100, max(0, ((accuracy * 50 + edu * 35) / 100 + 15)))
        } else if let accuracy {
            return min(100, max(0, ((accuracy * 70) / 100 + 20)))
        } else if let edu {
            return min(100, max(0, ((edu * 70) / 100 + 15)))
        } else if report.totalMinutes > 0 {
            return 50
        } else {
            return nil
        }
    }

    public static func copy(score: Int?, report: ChildReportsSnapshot) -> (label: String, description: String) {
        guard let score else {
            return ("—", "No quiz or usage data in this period yet")
        }

        let label: String
        switch score {
        case 80...: label = "Great!"
        case 65..<80: label = "Good"
        case 45..<65: label = "Fair"
        default: label = "Needs attention"
        }

        let description: String
        if let accuracy = report.quiz.accuracyPercent, let edu = report.educationalPercent {
            description = "\(score)/100 · \(edu)% learning apps · \(accuracy)% quiz pass"
        } else if let edu = report.educationalPercent {
            description = "\(score)/100 · \(edu)% educational screen time"
        } else if let accuracy = report.quiz.accuracyPercent {
            description = "\(score)/100 · \(accuracy)% quiz pass rate"
        } else {
            description = "\(score)/100 · Based on recorded activity"
        }

        return (label, description)
    }
}
