-- Add locked_by column for multi-instance claim identification
alter table skill_translation_task
    add column if not exists locked_by varchar(128);
