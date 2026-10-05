import Foundation

/// Functional outcome representation for domain operations.
public enum Outcome<T: Sendable>: Sendable {
    case success(T)
    case failure(AppError)

    public var isSuccess: Bool {
        if case .success = self { return true }
        return false
    }

    public var valueOrNil: T? {
        if case .success(let val) = self { return val }
        return nil
    }

    public var errorOrNil: AppError? {
        if case .failure(let err) = self { return err }
        return nil
    }
}
