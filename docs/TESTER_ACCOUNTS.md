# MeritScreen closed-tester parent accounts

Temporary parent logins for the **0.2.0-internal** APK wave. These are not customer accounts.

| # | Email | OTP (always) | Display name |
| --- | --- | --- | --- |
| 1 | `tester1.parent@anajyo.com` | **123456** | Tester One |
| 2 | `tester2.parent@anajyo.com` | **123456** | Tester Two |
| 3 | `tester3.parent@anajyo.com` | **123456** | Tester Three |
| 4 | `tester4.parent@anajyo.com` | **123456** | Tester Four |
| 5 | `tester5.parent@anajyo.com` | **123456** | Tester Five |

## How to sign in (app)

1. Open MeritScreen → Parent → Email sign-in.
2. Enter one of the emails above → **Send code** (no real email is sent for these).
3. Enter OTP **`123456`**.
4. Continue onboarding (create family / child) as usual — each tester gets their own Firebase Auth user.

You can also enter **`123456`** without waiting for an inbox; for these five addresses the code is fixed server-side (Send code still works and skips Resend).

## Notes

- Real parent emails still get a unique OTP via Resend (`noreply@anajyo.com`).
- Tester allowlist + OTP live in `functions/src/testerAccounts.ts`. Remove entries when the wave ends and redeploy `sendEmailOtp` / `verifyEmailOtp`.
- Accounts are already seeded in Firebase Auth + `users/{uid}` with `role=parent` and `isTesterAccount=true`.
- Do **not** share this file publicly. Treat it as a closed QA handout.
