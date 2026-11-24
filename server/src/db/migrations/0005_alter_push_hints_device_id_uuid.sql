TRUNCATE chat_push_hints_sent;
ALTER TABLE chat_push_hints_sent
  ALTER COLUMN device_id TYPE UUID USING device_id::UUID;
