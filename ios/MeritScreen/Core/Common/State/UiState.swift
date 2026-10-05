import Foundation

/// Unified UI state representing the asynchronous lifecycle of screen destinations and components.
/// Mirrors `com.meritscreen.core.common.ui.UiState` in the Android codebase.
public enum UiState<T: Sendable>: Sendable {
    case idle
    case loading
    case empty
    case success(T)
    case error(AppError)

    public var isLoading: Bool {
        if case .loading = self { return true }
        return false
    }

    public var isEmpty: Bool {
        if case .empty = self { return true }
        return false
    }

    public var dataOrNull: T? {
        if case .success(let data) = self { return data }
        return nil
    }

    public var data: T? {
        dataOrNull
    }

    public var errorOrNull: AppError? {
        if case .error(let error) = self { return error }
        return nil
    }
}
