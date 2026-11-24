/**
 * seed-social-demo.ts
 * Seeds demo users for visual verification of Gap 2 & Gap 3.
 */

import { authDb } from '../src/db/connection.js';
import { users, follows, followRequests, notifications } from '../src/db/schema.js';
import { hashCredential } from '../src/services/authService.js';
import { eq, or } from 'drizzle-orm';

async function seed() {
  console.log('Seeding demo users for Phase 10B verification...');

  const passHash = await hashCredential('Password123!');

  // Clean existing demo users if any
  try {
    await authDb.delete(users).where(
      or(
        eq(users.username, 'usera_demo'),
        eq(users.username, 'userb_demo'),
        eq(users.username, 'userc_private')
      )
    );
  } catch (e) {
    // Ignore cleanup error
  }

  // 1. Create User A
  const [userA] = await authDb
    .insert(users)
    .values({
      username: 'usera_demo',
      email: 'usera_demo@mue.app',
      passwordHash: passHash,
      displayName: 'User A Demo',
      bio: 'Music enthusiast & producer 🎵',
      avatarUrl: 'https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=512&q=80',
      isPrivate: false,
      followerCount: 1,
      followingCount: 2,
    })
    .returning();

  // 2. Create User B
  const [userB] = await authDb
    .insert(users)
    .values({
      username: 'userb_demo',
      email: 'userb_demo@mue.app',
      passwordHash: passHash,
      displayName: 'User B Demo',
      bio: 'Electronic & Synthwave Lover 🎹',
      avatarUrl: 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=512&q=80',
      isPrivate: false,
      followerCount: 1,
      followingCount: 1,
    })
    .returning();

  // 3. Create User C (Private Account)
  const [userC] = await authDb
    .insert(users)
    .values({
      username: 'userc_private',
      email: 'userc_private@mue.app',
      passwordHash: passHash,
      displayName: 'User C Private',
      bio: 'Private sound experiments 🔒',
      avatarUrl: 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=512&q=80',
      isPrivate: true,
      followerCount: 0,
      followingCount: 0,
    })
    .returning();

  console.log(`Created users:\nUser A: ${userA.id}\nUser B: ${userB.id}\nUser C: ${userC.id}`);

  // User B follows User A
  await authDb.insert(follows).values({
    followerId: userB.id,
    followeeId: userA.id,
  });

  // User A follows User B
  await authDb.insert(follows).values({
    followerId: userA.id,
    followeeId: userB.id,
  });

  // Incoming follow request to User A from User C
  await authDb.insert(followRequests).values({
    fromUserId: userC.id,
    toUserId: userA.id,
    status: 'pending',
    expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
  });

  // Outgoing follow request from User A to User C
  await authDb.insert(followRequests).values({
    fromUserId: userA.id,
    toUserId: userC.id,
    status: 'pending',
    expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000),
  });

  // Notifications for User A
  await authDb.insert(notifications).values([
    {
      userId: userA.id,
      actorId: userB.id,
      type: 'follow',
      preview: 'started following you',
      read: false,
    },
    {
      userId: userA.id,
      actorId: userC.id,
      type: 'follow_request',
      preview: 'requested to follow you',
      read: false,
    },
  ]);

  console.log('Seeded follows, follow requests, and notifications successfully.');
  process.exit(0);
}

seed().catch((err) => {
  console.error(err);
  process.exit(1);
});
