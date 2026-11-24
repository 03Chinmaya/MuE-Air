CREATE TABLE IF NOT EXISTS "chat_push_hints_sent" (
  "message_id" uuid NOT NULL,
  "device_id" text NOT NULL,
  "sent_at" timestamp with time zone DEFAULT now() NOT NULL,
  CONSTRAINT "chat_push_hints_sent_pkey" PRIMARY KEY("message_id", "device_id")
);
CREATE INDEX IF NOT EXISTS "idx_push_hints_sent_at" ON "chat_push_hints_sent" ("sent_at");
