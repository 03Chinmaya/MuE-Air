CREATE TABLE "guest_merge_log" (
  "id" uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  "guest_id" uuid NOT NULL,
  "auth_id" uuid NOT NULL,
  "device_id" uuid,
  "status" text NOT NULL,
  "error" text,
  "client_batch_id" text UNIQUE,
  "merged_at" timestamp with time zone DEFAULT now()
);
CREATE INDEX "idx_guest_merge_log_guest" ON "guest_merge_log" ("guest_id");

