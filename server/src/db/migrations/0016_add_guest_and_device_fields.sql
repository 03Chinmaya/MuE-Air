ALTER TABLE "users" ADD COLUMN "is_guest" boolean NOT NULL DEFAULT false;
ALTER TABLE "users" ADD COLUMN "merged_into" uuid REFERENCES "users"("id");
ALTER TABLE "users" ADD COLUMN "merged_at" timestamp with time zone;
ALTER TABLE "users" ADD COLUMN "device_fingerprint" text;
ALTER TABLE "users" ADD COLUMN "ip_at_creation" inet;
ALTER TABLE "user_devices" ADD COLUMN "device_fingerprint_hash" text;
CREATE INDEX "idx_users_merged_into" ON "users" ("merged_into");
CREATE INDEX "idx_users_device_fingerprint" ON "users" ("device_fingerprint");
CREATE INDEX "idx_user_devices_fingerprint" ON "user_devices" ("device_fingerprint_hash");

