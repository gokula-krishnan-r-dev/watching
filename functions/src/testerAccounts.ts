/**
 * Closed-tester parent accounts for sideload / QA waves.
 *
 * These emails always use a fixed OTP (never emailed via Resend). Keep the list
 * short and remove entries when the wave ends. Do not use real customer inboxes.
 */

/** Shared OTP for every allowlisted tester email. Spoken as 1-2-3-4-5-6. */
export const TESTER_STATIC_OTP = "123456";

/**
 * Parent emails that accept [TESTER_STATIC_OTP]. Normalized lowercase.
 * Display names are used when seeding Auth / Firestore profiles.
 */
export const TESTER_PARENT_ACCOUNTS = [
  {
    email: "tester1.parent@anajyo.com",
    displayName: "Tester One",
  },
  {
    email: "tester2.parent@anajyo.com",
    displayName: "Tester Two",
  },
  {
    email: "tester3.parent@anajyo.com",
    displayName: "Tester Three",
  },
  {
    email: "tester4.parent@anajyo.com",
    displayName: "Tester Four",
  },
  {
    email: "tester5.parent@anajyo.com",
    displayName: "Tester Five",
  },
] as const;

const TESTER_EMAIL_SET = new Set(
  TESTER_PARENT_ACCOUNTS.map((account) => account.email.toLowerCase()),
);

export function isTesterParentEmail(email: string): boolean {
  return TESTER_EMAIL_SET.has(email.toLowerCase().trim());
}
