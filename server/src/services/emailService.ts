/**
 * Email Service
 * Handles sending emails such as welcome emails upon account creation.
 * Connects to provider API (e.g. Resend) with fallback and test hooks.
 */

export interface EmailPayload {
  to: string;
  subject: string;
  html: string;
  text?: string;
}

export type MockEmailHandler = (payload: EmailPayload) => Promise<boolean>;

let mockEmailHandler: MockEmailHandler | null = null;
const sentEmails: EmailPayload[] = [];

export function __setMockEmailHandler(handler: MockEmailHandler | null): void {
  if (process.env.NODE_ENV !== 'test') {
    throw new Error('test-only hook');
  }
  mockEmailHandler = handler;
}

export function __getSentEmails(): EmailPayload[] {
  return [...sentEmails];
}

export function __clearSentEmails(): void {
  sentEmails.length = 0;
}

export async function sendEmail(payload: EmailPayload): Promise<boolean> {
  sentEmails.push(payload);
  if (mockEmailHandler) {
    return mockEmailHandler(payload);
  }

  const resendApiKey = process.env.RESEND_API_KEY;
  if (resendApiKey) {
    try {
      const res = await fetch('https://api.resend.com/emails', {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${resendApiKey}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          from: process.env.EMAIL_FROM || 'MuE <welcome@mue.app>',
          to: payload.to,
          subject: payload.subject,
          html: payload.html,
          text: payload.text,
        }),
      });
      return res.ok;
    } catch {
      return false;
    }
  }

  // Graceful no-op fallback when no external email credentials configured
  return true;
}

export async function sendWelcomeEmail(to: string, displayName: string, username: string): Promise<boolean> {
  const subject = `Welcome to MuE, ${displayName || username}!`;
  const html = `
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #000000; color: #FFFFFF; padding: 32px; border-radius: 12px; max-width: 560px; margin: 0 auto;">
      <h1 style="color: #6C5CE7; font-size: 24px; margin-bottom: 16px;">Welcome to MuE</h1>
      <p style="font-size: 16px; line-height: 1.5; color: #E0E0E0;">
        Hi <strong>${displayName || username}</strong> (@${username}),
      </p>
      <p style="font-size: 15px; line-height: 1.5; color: #A0A0A0;">
        We're thrilled to have you join MuE. Your account is officially created and ready to use.
      </p>
      <p style="font-size: 15px; line-height: 1.5; color: #A0A0A0;">
        Start discovering music, creating playlists, and streaming high-fidelity audio today.
      </p>
      <hr style="border: 0; border-top: 1px solid #222222; margin: 24px 0;" />
      <p style="font-size: 13px; color: #666666;">
        — The MuE Team
      </p>
    </div>
  `;
  const text = `Welcome to MuE, ${displayName || username}!\n\nYour account (@${username}) is ready. Start discovering music and streaming with MuE today.\n\n— The MuE Team`;

  return sendEmail({
    to,
    subject,
    html,
    text,
  });
}

export async function sendPasswordResetEmail(to: string, resetLink: string): Promise<boolean> {
  const subject = 'Reset your MuE password';
  const html = `
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #000000; color: #FFFFFF; padding: 32px; border-radius: 12px; max-width: 560px; margin: 0 auto;">
      <h1 style="color: #6C5CE7; font-size: 24px; margin-bottom: 16px;">Password Reset Request</h1>
      <p style="font-size: 16px; line-height: 1.5; color: #E0E0E0;">
        You recently requested to reset the password for your MuE account. Click the button below to reset it:
      </p>
      <div style="margin: 28px 0; text-align: center;">
        <a href="${resetLink}" style="background-color: #1C39BB; color: #FFFFFF; text-decoration: none; padding: 14px 28px; border-radius: 8px; font-weight: bold; display: inline-block;">
          Reset Password
        </a>
      </div>
      <p style="font-size: 14px; line-height: 1.5; color: #A0A0A0;">
        Or copy and paste this link into your browser:<br/>
        <a href="${resetLink}" style="color: #7FC8F8; word-break: break-all;">${resetLink}</a>
      </p>
      <p style="font-size: 13px; line-height: 1.4; color: #888888; margin-top: 20px;">
        This link is single-use and will expire in 30 minutes. If you did not request a password reset, please ignore this email or contact support.
      </p>
      <hr style="border: 0; border-top: 1px solid #222222; margin: 24px 0;" />
      <p style="font-size: 13px; color: #666666;">
        — The MuE Team
      </p>
    </div>
  `;
  const text = `Reset your MuE password\n\nYou recently requested to reset your password. Use the link below to set a new password:\n\n${resetLink}\n\nThis link expires in 30 minutes. If you did not make this request, you can safely ignore this email.\n\n— The MuE Team`;

  return sendEmail({
    to,
    subject,
    html,
    text,
  });
}

export async function sendOtpEmail(to: string, otp: string, purpose: 'signup' | 'reset'): Promise<boolean> {
  const isSignup = purpose === 'signup';
  const subject = isSignup ? 'Verify your email for MuE' : 'Your MuE password reset code';
  const title = isSignup ? 'Verify Your Email' : 'Reset Your Password';
  const intro = isSignup
    ? 'Welcome to MuE! Use the verification code below to complete your registration:'
    : 'You requested to reset your MuE account password. Use the verification code below to set a new password:';
  const warning = 'This code is single-use and will expire in 10 minutes. If you did not make this request, please safely ignore this email.';

  const html = `
    <div style="font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #000000; color: #FFFFFF; padding: 32px; border-radius: 12px; max-width: 560px; margin: 0 auto;">
      <h1 style="color: #6C5CE7; font-size: 24px; margin-bottom: 16px;">${title}</h1>
      <p style="font-size: 16px; line-height: 1.5; color: #E0E0E0;">
        ${intro}
      </p>
      <div style="margin: 28px 0; text-align: center;">
        <span style="display: inline-block; font-size: 36px; font-weight: bold; letter-spacing: 8px; color: #FFFFFF; background-color: #121212; border: 1px solid #333333; padding: 16px 32px; border-radius: 10px; font-family: monospace;">
          ${otp}
        </span>
      </div>
      <p style="font-size: 13px; line-height: 1.4; color: #888888; margin-top: 20px;">
        ${warning}
      </p>
      <hr style="border: 0; border-top: 1px solid #222222; margin: 24px 0;" />
      <p style="font-size: 13px; color: #666666;">
        — The MuE Team
      </p>
    </div>
  `;

  const text = `${title}\n\n${intro}\n\nYour code: ${otp}\n\n${warning}\n\n— The MuE Team`;

  return sendEmail({
    to,
    subject,
    html,
    text,
  });
}

