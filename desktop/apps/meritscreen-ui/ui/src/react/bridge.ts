/** Contract between the legacy router (main.ts) and React screens. */
export type InvokeFn = <T = unknown>(cmd: string, args?: Record<string, unknown>) => Promise<T>;

export interface AppBridge {
  invoke: InvokeFn;
  /** Navigate using legacy route objects (kept loose to avoid a circular import). */
  navigate: (route: { name: string; [k: string]: unknown }) => Promise<void>;
  toast: (message: string, type?: "success" | "info" | "error") => void;
  /** Dev-only shortcuts (demo codes, tester chips). False in release builds. */
  devShortcuts: boolean;
  liveBackend: boolean;
  appleSignInAvailable: boolean;
}
