CREATE INDEX IF NOT EXISTS idx_skill_label_label_id_skill_id
    ON skill_label(label_id, skill_id);