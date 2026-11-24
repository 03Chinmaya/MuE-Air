-- Phase A.1.3: Grant app_user scoped RLS write policies for chat_attachments and chat_push_hints_sent
GRANT SELECT, INSERT, UPDATE, DELETE ON chat_attachments TO app_user;

CREATE POLICY chat_attachments_insert ON chat_attachments
  FOR INSERT TO app_user
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM chat_messages m
      WHERE m.id = chat_attachments.message_id
        AND is_chat_participant(m.conversation_id, auth.uid())
    )
  );

CREATE POLICY chat_attachments_update ON chat_attachments
  FOR UPDATE TO app_user
  USING (
    EXISTS (
      SELECT 1 FROM chat_messages m
      WHERE m.id = chat_attachments.message_id
        AND is_chat_participant(m.conversation_id, auth.uid())
    )
  )
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM chat_messages m
      WHERE m.id = chat_attachments.message_id
        AND is_chat_participant(m.conversation_id, auth.uid())
    )
  );

CREATE POLICY chat_attachments_delete ON chat_attachments
  FOR DELETE TO app_user
  USING (
    EXISTS (
      SELECT 1 FROM chat_messages m
      WHERE m.id = chat_attachments.message_id
        AND m.sender_id = auth.uid()
    )
  );

GRANT SELECT, INSERT ON chat_push_hints_sent TO app_user;

CREATE POLICY chat_push_hints_sent_insert ON chat_push_hints_sent
  FOR INSERT TO app_user
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM chat_messages m
      WHERE m.id = chat_push_hints_sent.message_id
        AND is_chat_participant(m.conversation_id, auth.uid())
    )
  );

CREATE POLICY chat_push_hints_sent_select ON chat_push_hints_sent
  FOR SELECT TO app_user
  USING (
    EXISTS (
      SELECT 1 FROM chat_messages m
      WHERE m.id = chat_push_hints_sent.message_id
        AND is_chat_participant(m.conversation_id, auth.uid())
    )
  );

