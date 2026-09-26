import { Resend } from "resend";
import { logger } from "firebase-functions/v2";
import { HttpsError } from "firebase-functions/v2/https";

const OTP_TTL_MINUTES = 10;

/** Warm-instance cache — avoids reconstructing the SDK client on every OTP send. */
let cachedClient: { apiKey: string; resend: Resend } | null = null;

export type SendOtpMailInput = {
  apiKey: string;
  from: string;
  to: string;
  code: string;
  /** Stable key so Cloud Functions retries do not double-deliver the same code. */
  idempotencyKey: string;
};

/**
 * Sends a parent sign-in OTP via Resend.
 * Failures map to user-facing HttpsError; the plaintext code is never logged.
 */
export async function sendOtpEmail(input: SendOtpMailInput): Promise<void> {
  const apiKey = input.apiKey?.trim();
  if (!apiKey) {
    logger.error("[Email OTP] RESEND_API_KEY is not configured");
    throw new HttpsError(
      "failed-precondition",
      "Email delivery is not configured. Please try again later.",
    );
  }
  if (!apiKey.startsWith("re_")) {
    logger.error("[Email OTP] RESEND_API_KEY has unexpected format");
    throw new HttpsError(
      "failed-precondition",
      "Email delivery is misconfigured. Please contact support.",
    );
  }

  const from = input.from?.trim();
  if (!from || !from.includes("@")) {
    logger.error("[Email OTP] RESEND_FROM_EMAIL is missing or invalid");
    throw new HttpsError(
      "failed-precondition",
      "Email delivery is misconfigured. Please contact support.",
    );
  }

  const resend = getResendClient(apiKey);
  // Leading with the code helps Gmail / Autofill surface a one-tap suggestion.
  const subject = `${input.code} is your MeritScreen verification code`;

  let data: { id: string } | null = null;
  let error: { name: string; message: string } | null = null;
  try {
    const result = await resend.emails.send(
      {
        from,
        to: [input.to],
        subject,
        text: buildPlainText(input.code),
        html: buildHtml(input.code),
        // Helps deliverability / inbox classification for transactional OTP mail.
        headers: {
          "X-Entity-Ref-ID": input.idempotencyKey,
        },
        tags: [
          { name: "category", value: "email_otp" },
          { name: "app", value: "meritscreen" },
        ],
      },
      { idempotencyKey: input.idempotencyKey },
    );
    data = result.data ?? null;
    error = result.error ?? null;
  } catch (err) {
    const message = err instanceof Error ? err.message : "unknown_transport_error";
    logger.error("[Email OTP] Resend transport error", { message });
    throw new HttpsError(
      "unavailable",
      "We couldn't send the verification email. Please try again in a moment.",
    );
  }

  if (error) {
    logger.error("[Email OTP] Resend send failed", {
      resendName: error.name,
      resendMessage: error.message,
      fromDomain: extractEmailDomain(from),
    });
    throw new HttpsError("failed-precondition", userFacingSendError(error.message));
  }

  logger.info("[Email OTP] Sent via Resend", {
    emailId: data?.id ?? null,
    toDomain: extractEmailDomain(input.to),
    fromDomain: extractEmailDomain(from),
  });
}

function getResendClient(apiKey: string): Resend {
  if (cachedClient?.apiKey === apiKey) {
    return cachedClient.resend;
  }
  cachedClient = { apiKey, resend: new Resend(apiKey) };
  return cachedClient.resend;
}

function extractEmailDomain(address: string): string {
  const match = address.match(/@([A-Za-z0-9.-]+\.[A-Za-z]{2,})/);
  return match?.[1]?.toLowerCase() ?? "unknown";
}

function userFacingSendError(resendMessage: string): string {
  const testingOnly = resendMessage.match(
    /only send testing emails to your own email address \(([^)]+)\)/i,
  );
  if (testingOnly) {
    const allowed = testingOnly[1];
    return `Email delivery is still in test mode and can only reach ${allowed}. Verify your sending domain, then set RESEND_FROM_EMAIL to an address on that domain.`;
  }
  if (/domain.*(not|isn't|is not).*(verified|valid)|invalid.*from/i.test(resendMessage)) {
    return "Email sender domain is not verified yet. Please try again shortly, or contact support.";
  }
  if (/invalid.*api.?key|missing_api_key|unauthorized|forbidden/i.test(resendMessage)) {
    return "Email delivery is misconfigured. Please contact support.";
  }
  if (/rate.?limit|too many/i.test(resendMessage)) {
    return "Too many emails were requested. Please wait a minute and try again.";
  }
  return "We couldn't send the verification email. Please try again in a moment.";
}

function buildPlainText(code: string): string {
  return [
    `${code} is your MeritScreen verification code`,
    "",
    "MeritScreen parent sign-in",
    "",
    `Your verification code is: ${code}`,
    "",
    `This code expires in ${OTP_TTL_MINUTES} minutes.`,
    "If you did not request this, you can ignore this email.",
  ].join("\n");
}

function buildHtml(code: string): string {
  // Keep a plain contiguous code for Autofill / copy-paste; digit chips are decorative.
  const digits = code
    .split("")
    .map(
      (d) =>
        `<span style="display:inline-block;width:36px;height:44px;line-height:44px;margin:0 4px;border-radius:10px;background:#EEF6F3;color:#0F3D32;font-size:22px;font-weight:700;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;">${d}</span>`,
    )
    .join("");

  return `<!DOCTYPE html>
<html lang="en">
<head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/></head>
<body style="margin:0;padding:0;background:#F4F7F6;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1A2B27;">
  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#F4F7F6;padding:32px 16px;">
    <tr><td align="center">
      <table role="presentation" width="100%" style="max-width:480px;background:#FFFFFF;border-radius:16px;padding:32px 28px;box-shadow:0 1px 3px rgba(15,61,50,0.08);">
        <tr><td>
          <p style="margin:0 0 8px;font-size:13px;letter-spacing:0.04em;text-transform:uppercase;color:#5B7A71;font-weight:600;">MeritScreen</p>
          <h1 style="margin:0 0 12px;font-size:22px;line-height:1.3;color:#0F3D32;">Your verification code</h1>
          <p style="margin:0 0 16px;font-size:15px;line-height:1.5;color:#3D5A52;">
            Enter this code in the app to finish signing in. It expires in ${OTP_TTL_MINUTES} minutes.
          </p>
          <p style="margin:0 0 8px;text-align:center;font-size:28px;letter-spacing:0.28em;font-weight:700;font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;color:#0F3D32;">
            ${code}
          </p>
          <p style="margin:0 0 28px;text-align:center;">${digits}</p>
          <p style="margin:0 0 12px;font-size:14px;line-height:1.5;color:#3D5A52;">
            ${code} is your MeritScreen verification code.
          </p>
          <p style="margin:0;font-size:13px;line-height:1.5;color:#6B857C;">
            If you did not request this email, you can safely ignore it.
          </p>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>`;
}
