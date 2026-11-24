CREATE TABLE IF NOT EXISTS "user_settings" (
	"user_id" uuid NOT NULL REFERENCES "users"("id") ON DELETE cascade,
	"key" text NOT NULL,
	"value" jsonb NOT NULL,
	"device_id" text,
	"updated_at" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "user_settings_user_id_key_pk" PRIMARY KEY("user_id","key")
);
CREATE INDEX IF NOT EXISTS "idx_user_settings_user" ON "user_settings" ("user_id");
