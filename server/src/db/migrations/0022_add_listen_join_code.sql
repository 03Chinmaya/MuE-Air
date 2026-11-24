-- Phase A.4.1: server-owned room codes for Listen Together.
-- Additive only. Backfills ALL existing rows with a charset-consistent,
-- collision-checked code. Deliberately excludes 0/O/1/I.
--
-- NOTE: the EXISTS check below sees uncommitted UPDATEs from earlier
-- loop iterations (same transaction), so collision detection is correct.
-- Do not "optimize" it into a single UPDATE.

ALTER TABLE "listen_sessions" ADD COLUMN "join_code" text;

DO $$
DECLARE
  r RECORD;
  new_code text;
  chars text := '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';
BEGIN
  FOR r IN SELECT id FROM "listen_sessions" WHERE "join_code" IS NULL LOOP
    LOOP
      new_code := (
        SELECT string_agg(substr(chars, (floor(random() * 32) + 1)::int, 1), '')
        FROM generate_series(1, 6)
      );
      EXIT WHEN NOT EXISTS (
        SELECT 1 FROM "listen_sessions" WHERE "join_code" = new_code
      );
    END LOOP;
    UPDATE "listen_sessions" SET "join_code" = new_code WHERE id = r.id;
  END LOOP;
END $$;

ALTER TABLE "listen_sessions" ALTER COLUMN "join_code" SET NOT NULL;
CREATE UNIQUE INDEX "listen_sessions_join_code_unique"
  ON "listen_sessions" ("join_code");
