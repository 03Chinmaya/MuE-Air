/**
 * MuE Backend Entry Point
 * Hono API + Socket.io Server via Node.js 22 LTS
 * Conforms to docs/00_STACK_LOCK.md Section 5 & 6.
 */

// Required for security fix: ensure JWT_SECRET and env vars are loaded before ESM routes evaluate
import 'dotenv/config';
import { serve } from '@hono/node-server';
import { app, setShuttingDown } from './app.js';
import { initializeRealtimeServer } from './realtime/socketServer.js';
import { closeConnections } from './db/connection.js';

const PORT = Number(process.env.PORT) || 4000;

const server = serve(
  {
    fetch: app.fetch,
    port: PORT,
    hostname: '0.0.0.0',
  },
  (info) => {
    console.log(`[MuE Server] Hono API running on http://0.0.0.0:${info.port}`);
  }
);

// Mount Socket.io on the Node HTTP server
const io = initializeRealtimeServer(server as any);

let isShuttingDown = false;

const shutdown = async (signal: string) => {
  if (isShuttingDown) return;
  isShuttingDown = true;
  setShuttingDown(true);

  console.log(`[MuE Server] Received ${signal}. Starting graceful shutdown...`);

  // Hard-kill fallback timer (15 seconds)
  const forceKillTimer = setTimeout(() => {
    console.error('[MuE Server] Graceful shutdown timed out after 15s. Forcing exit.');
    process.exit(1);
  }, 15000);
  forceKillTimer.unref();

  try {
    // 1. Close Socket.io realtime connections (disconnects active client sockets)
    if (io) {
      await new Promise<void>((resolve) => {
        io.close((err: any) => {
          if (err && err.code !== 'ERR_SERVER_NOT_RUNNING') {
            console.warn('[MuE Server] Error closing Socket.io server:', err);
          } else {
            console.log('[MuE Server] Socket.io server closed cleanly.');
          }
          resolve();
        });
      });
    }

    // 2. Drain in-flight HTTP requests and stop accepting new ones
    await new Promise<void>((resolve) => {
      server.close((err: any) => {
        if (err && err.code !== 'ERR_SERVER_NOT_RUNNING') {
          console.warn('[MuE Server] Error closing HTTP server:', err);
        } else {
          console.log('[MuE Server] HTTP server closed cleanly.');
        }
        resolve();
      });
    });

    // 3. Close database connection pools
    console.log('[MuE Server] Closing database connection pools...');
    await closeConnections();
    console.log('[MuE Server] Database connections closed cleanly.');

    console.log('[MuE Server] Graceful shutdown completed cleanly.');
    process.exit(0);
  } catch (err) {
    console.error('[MuE Server] Error during graceful shutdown:', err);
    process.exit(1);
  }
};

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

export default app;
