import type { AppBridge } from "../bridge";

export function mockBridge(
  over: Partial<AppBridge> = {},
  impl?: (cmd: string, args?: Record<string, unknown>) => unknown,
): AppBridge {
  return {
    invoke: vi.fn(async (cmd: string, args?: Record<string, unknown>) => (impl ? impl(cmd, args) : undefined)) as AppBridge["invoke"],
    navigate: vi.fn(async () => {}),
    toast: vi.fn(),
    devShortcuts: false,
    liveBackend: true,
    appleSignInAvailable: false,
    ...over,
  };
}
