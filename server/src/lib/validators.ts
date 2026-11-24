/**
 * Centralized Zod Validation Schemas and zValidator Hooks
 * Conforms to docs/00_STACK_LOCK.md and Phase 6.5 standards.
 */

import { z } from 'zod';
import { validateUsername } from '../utils/username.js';

/**
 * Standard validation error hook for zValidator.
 * Produces consistent HTTP 400 response with error message and details.
 */
export function validationHook(defaultError = 'invalid_request') {
  return (result: any, c: any) => {
    if (!result.success) {
      const firstIssue = result.error.issues?.[0];
      const isUsername = firstIssue?.path?.includes('username');
      const isGeneric = firstIssue?.code === 'invalid_type' && firstIssue?.message?.toLowerCase().includes('expected');
      const msg = (!isGeneric && firstIssue?.message && firstIssue.message !== 'Required')
        ? firstIssue.message
        : defaultError;
      return c.json(
        {
          error: msg,
          ...(isUsername ? { code: 'invalid_username' } : {}),
          issues: result.error.issues,
          details: result.error.format ? result.error.format() : result.error.flatten(),
        },
        400
      );
    }
  };
}

// -------------------------------------------------------------
// Auth Schemas
// -------------------------------------------------------------

export const guestSetupSchema = z.object({
  deviceId: z.string().min(1, 'deviceId is required').max(256),
  deviceName: z.string().min(1).max(100).default('Android Device'),
  platform: z.string().min(1).max(50).default('android'),
  deviceFingerprint: z.string().optional(),
});

export const mergeGuestSchema = z
  .object({
    guestId: z.string().uuid('guestId must be a valid UUID').optional(),
    guestUserId: z.string().uuid('guestUserId must be a valid UUID').optional(),
    clientBatchId: z.string().min(1).max(256).optional(),
    options: z
      .object({
        conflictResolution: z.enum(['keep_account', 'keep_guest', 'merge']).optional(),
        transferEntities: z.array(z.string()).optional(),
      })
      .optional(),
  })
  .refine((data) => Boolean(data.guestId || data.guestUserId), {
    message: 'guestId is required',
    path: ['guestId'],
  });

export const signupSchema = z
  .object({
    username: z.string().optional(),
    identifier: z.string().optional(),
    password: z.string().optional(),
    secret: z.string().optional(),
    pin: z.string().optional(),
    email: z.string().optional(),
    displayName: z.string().optional(),
    deviceId: z.string().optional(),
    deviceName: z.string().optional(),
  })
  .refine(
    (data) => Boolean(data.username || data.identifier),
    { message: 'Username and either password or PIN are required.', path: ['username'] }
  )
  .refine(
    (data) => Boolean(data.password || data.secret || data.pin),
    { message: 'Username and either password or PIN are required.', path: ['password'] }
  );

export const loginSchema = z
  .object({
    usernameOrEmail: z.string().optional(),
    identifier: z.string().optional(),
    username: z.string().optional(),
    password: z.string().optional(),
    secret: z.string().optional(),
    pin: z.string().optional(),
    deviceName: z.string().optional(),
    deviceId: z.string().optional(),
  })
  .refine(
    (data) => Boolean(data.usernameOrEmail || data.identifier || data.username),
    { message: 'Username/email and credentials required.', path: ['usernameOrEmail'] }
  )
  .refine(
    (data) => Boolean(data.password || data.secret || data.pin),
    { message: 'Username/email and credentials required.', path: ['password'] }
  );

export const refreshSchema = z.object({
  refreshToken: z.string().min(1, 'Refresh token is required.'),
});

export const deviceRegisterSchema = z.object({
  deviceName: z.string().optional(),
  platform: z.enum(['android', 'ios', 'web']).optional(),
  pushToken: z.string().optional(),
  pushEndpoint: z.string().optional(),
});

// -------------------------------------------------------------
// Push Notification Schemas
// -------------------------------------------------------------

export const pushRegisterSchema = z
  .object({
    pushEndpoint: z.string().optional(),
    pushToken: z.string().optional(),
    platform: z.enum(['android', 'ios', 'web']).default('android').optional(),
    deviceId: z.string().optional(),
    deviceName: z.string().optional(),
  })
  .refine(
    (data) => Boolean(data.pushEndpoint || data.pushToken),
    { message: 'Push endpoint required', path: ['pushEndpoint'] }
  );

// -------------------------------------------------------------
// Chat Schemas
// -------------------------------------------------------------

export const SendMessageSchema = z
  .object({
    conversationId: z.string().uuid().optional(),
    recipientId: z.string().uuid().optional(),
    contentCiphertext: z.string().min(1, 'contentCiphertext is required'),
    contentNonce: z.string().default(''),
    contentAlgorithm: z.string().default('signal-double-ratchet'),
    contentType: z.string().default('text'),
    senderDeviceId: z.string().uuid().optional(),
    recipientDeviceId: z.string().uuid().optional(),
    replyToId: z.string().uuid().optional(),
    forwardFromId: z.string().uuid().optional(),
    mediaUrl: z.string().optional(),
    mediaType: z.string().optional(),
    mediaSize: z.number().int().nonnegative().optional(),
    mediaKey: z.string().optional(),
    mediaNonce: z.string().optional(),
    mediaHash: z.string().optional(),
    thumbnailUrl: z.string().optional(),
    durationMs: z.number().int().nonnegative().optional(),
    waveform: z.array(z.number()).optional(),
    storyReplyId: z.string().uuid().optional(),
    e2eSessionVersion: z.number().int().optional(),
    disappearingInSeconds: z.number().int().positive().optional(),
    ephemeralTtl: z.number().int().positive().optional(),
    ephemeralExpireAt: z.string().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.contentCiphertext && Buffer.byteLength(data.contentCiphertext, 'utf8') > 12_288) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'Payload exceeds 12KB limit',
        path: ['contentCiphertext'],
      });
    }
  });

export const conversationMessageSchema = z.object({
  id: z.string().optional(),
  contentCiphertext: z.string().min(1, 'contentCiphertext is required'),
  contentNonce: z.string().optional(),
  contentAlgorithm: z.string().optional(),
  contentType: z.string().optional(),
  senderDeviceId: z.string().optional(),
  replyToId: z.string().optional(),
});

export const presignAttachmentSchema = z.object({
  mimeType: z.string().min(1, 'mimeType is required'),
  fileSize: z
    .number()
    .int()
    .positive('fileSize must be a positive number')
    .max(25 * 1024 * 1024, 'fileSize exceeds 25MB limit'),
  fileName: z.string().min(1).max(255).optional(),
  isTestUpload: z.boolean().optional(),
});

// -------------------------------------------------------------
// Profile Schemas
// -------------------------------------------------------------

export const patchProfileSchema = z
  .object({
    displayName: z.string().min(1).max(40).optional(),
    username: z.string().optional(),
    bio: z.string().max(160).optional(),
    avatarUrl: z.string().nullable().optional(),
    isPrivate: z.boolean().optional(),
  })
  .superRefine((data, ctx) => {
    if (data.username !== undefined) {
      const res = validateUsername(data.username);
      if (!res.ok) {
        ctx.addIssue({
          code: z.ZodIssueCode.custom,
          message: res.reason,
          path: ['username'],
        });
      }
    }
  });

// -------------------------------------------------------------
// E2E Key Exchange Schemas
// -------------------------------------------------------------

export const RotateSignedPrekeySchema = z.object({
  deviceId: z.string().uuid(),
  signedPrekey: z.object({
    keyId: z.number().int().positive(),
    publicKey: z.string().min(10),
    signature: z.string().min(10),
  }),
});

export const SenderKeyDistributionSchema = z.object({
  conversationId: z.string().uuid(),
  senderDeviceId: z.string().uuid(),
  recipientUserId: z.string().uuid(),
  recipientDeviceId: z.string().uuid(),
  distributionCiphertext: z.string().min(1),
  distributionNonce: z.string().min(1),
});

export const registerKeysSchema = z.object({
  deviceId: z.string().uuid('Invalid device UUID'),
  identityKey: z.string().min(1, 'identityKey is required'),
  registrationId: z.number().int(),
  signedPrekey: z.object({
    keyId: z.number().int().positive(),
    publicKey: z.string().min(1),
    signature: z.string().min(1),
  }),
  oneTimePrekeys: z
    .array(
      z.object({
        keyId: z.number().int(),
        publicKey: z.string().min(1),
      })
    )
    .optional()
    .default([]),
});

export const replenishPrekeysSchema = z.object({
  deviceId: z.string().uuid('Invalid device UUID'),
  oneTimePrekeys: z
    .array(
      z.object({
        keyId: z.number().int(),
        publicKey: z.string().min(1),
      })
    )
    .min(1, 'deviceId and non-empty oneTimePrekeys array required'),
});

// -------------------------------------------------------------
// Sync Schemas
// -------------------------------------------------------------

export const syncOperationItemSchema = z.object({
  id: z.string().optional(),
  type: z.enum(['create', 'update', 'delete']),
  entityType: z.enum([
    'like',
    'playlist',
    'playlist_track',
    'history',
    'preference',
    'playlist_reorder',
    'import_playlist',
    'imported_playlist',
    'import_playlist_track',
    'imported_playlist_track',
  ]),
  entityId: z.string(),
  payload: z.any().optional(),
  createdAt: z.string().optional(),
});

export const uploadLocalSchema = z.object({
  clientBatchId: z.string().min(1).max(256),
  operations: z.array(syncOperationItemSchema).default([]),
});

export const syncPayloadSchema = z.any().superRefine((val, ctx) => {
  const isArr = Array.isArray(val);
  const isOps = Array.isArray(val?.operations);
  const isReorder = val?.entityType === 'playlist_reorder' && Boolean(val.playlistId && Array.isArray(val.orderedTrackIds));

  if (!isArr && !isOps && !isReorder) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Invalid operations format' });
    return;
  }
  const ops = isArr ? val : (isOps ? val.operations : null);
  if (ops && ops.length > 50) {
    ctx.addIssue({ code: z.ZodIssueCode.custom, message: 'Batch exceeds maximum limit of 50 operations' });
  }
});
