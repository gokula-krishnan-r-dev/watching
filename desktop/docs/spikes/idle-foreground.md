# Spike: Idle + foreground process APIs (no Accessibility)

**Result:** PASS — documented OS APIs cover active-use clock without AccessibilityService / hidden hooks.

## Windows

| Need | API |
| --- | --- |
| Idle | `GetLastInputInfo` |
| Foreground | `GetForegroundWindow` + `GetWindowThreadProcessId` + process image path / AUMID |
| Monotonic clock | `QueryUnbiasedInterruptTime` |
| Session agent | `WTSQueryUserToken` + `CreateProcessAsUser` (Guardian → Agent) |

## macOS

| Need | API |
| --- | --- |
| Idle | `CGEventSourceSecondsSinceLastEventType` (HID system state) |
| Foreground | `NSWorkspace.shared.frontmostApplication` (bundle id) |
| Monotonic clock | `mach_continuous_time` |
| Inventory | `/Applications` + Launch Services bundle ids |

Input Monitoring permission may be required on newer macOS for some event APIs — if required, disclose in C03 and prefer the least-privilege path. Do not use Accessibility for trapping the user.

## Linux

| Need | API |
| --- | --- |
| Idle (X11) | XScreenSaver / `xss` idle query |
| Idle (Wayland) | ext-idle-notify or desktop portal — best-effort |
| Foreground | `_NET_ACTIVE_WINDOW` (X11); Wayland compositor-specific |
| Monotonic clock | `CLOCK_BOOTTIME` |

## Product decision

- Active-use clock uses these APIs only (threshold: `IDLE_THRESHOLD_SECONDS` in `app_config`).
- No keyloggers, no clipboard monitors, no screen capture of other apps.
- Clock rollback → fail closed + `tamperFlags.clockRollback`.
