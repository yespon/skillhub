alter table skill_translation
    add column if not exists source_type varchar(16) not null default 'USER';

alter table skill_translation
    add column if not exists source_hash varchar(64);

create table if not exists skill_translation_task (
    id bigserial primary key,
    skill_id bigint not null references skill(id) on delete cascade,
    locale varchar(16) not null,
    source_text varchar(200) not null,
    source_hash varchar(64) not null,
    status varchar(16) not null,
    attempt_count integer not null default 0,
    last_error varchar(1000),
    next_attempt_at timestamptz not null,
    locked_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists idx_skill_translation_task_status_next_attempt
    on skill_translation_task (status, next_attempt_at, created_at);

create index if not exists idx_skill_translation_task_skill_locale
    on skill_translation_task (skill_id, locale);