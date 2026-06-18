create type user_role as enum ('SUPERADMIN', 'ADMIN', 'TEACHER', 'STUDENT');
create type schedule_type as enum ('CLASSES', 'EXAMS');
create type plan_status as enum (
  'DRAFT', 'VALIDATING', 'INVALID_INPUT', 'GENERATING',
  'GENERATED', 'GENERATED_WITH_CONFLICTS', 'APPROVED',
  'LOCKED', 'ARCHIVED'
);
create type issue_severity as enum ('ERROR', 'WARNING');
create type assignment_status as enum ('ASSIGNED', 'UNASSIGNED');
create type room_type as enum ('THEORY', 'LAB', 'MIXED');
create type schedule_run_status as enum (
  'RUNNING', 'COMPLETED', 'COMPLETED_WITH_CONFLICTS', 'FAILED'
);
create type manual_edit_status as enum (
  'APPLIED_CLEAN', 'APPLIED_WITH_REPAIR',
  'APPLIED_WITH_REMAINING_CONFLICTS',
  'REJECTED_BY_STATE', 'REJECTED_BY_INPUT'
);
create type import_batch_status as enum (
  'UPLOADED', 'VALIDATING', 'VALID', 'INVALID', 'IMPORTED', 'FAILED'
);

create table career (
  id bigserial primary key,
  code text not null unique,
  name text not null,
  active boolean not null default true
);

create table journey (
  id bigserial primary key,
  code text not null unique,
  name text not null,
  block_minutes int not null check (block_minutes > 0),
  start_time time not null,
  end_time time not null,
  check (start_time < end_time)
);

create table time_block (
  id bigserial primary key,
  journey_id bigint not null references journey(id),
  day_of_week smallint not null check (day_of_week between 1 and 7),
  block_index int not null check (block_index >= 0),
  start_time time not null,
  end_time time not null,
  unique (journey_id, day_of_week, block_index),
  check (start_time < end_time)
);

create table fixed_break (
  id bigserial primary key,
  journey_id bigint not null references journey(id),
  day_of_week smallint not null check (day_of_week between 1 and 7),
  start_block int not null check (start_block >= 0),
  duration_blocks int not null check (duration_blocks > 0),
  reason text
);

create table curriculum (
  id bigserial primary key,
  career_id bigint not null references career(id),
  code text not null,
  year int not null,
  is_active boolean not null default true,
  valid_from date,
  valid_until date,
  unique (career_id, code),
  unique (id, career_id),
  check (valid_until is null or valid_from is null or valid_from <= valid_until)
);

create table course (
  id bigserial primary key,
  code text not null unique,
  name text not null,
  requires_lab boolean not null default false,
  weekly_blocks_min int not null check (weekly_blocks_min > 0),
  weekly_blocks_max int not null check (weekly_blocks_max >= weekly_blocks_min),
  preferences jsonb not null default '{}'::jsonb
);

create table curriculum_course (
  id bigserial primary key,
  curriculum_id bigint not null references curriculum(id),
  course_id bigint not null references course(id),
  semester_number int not null check (semester_number > 0),
  unique (curriculum_id, course_id),
  unique (curriculum_id, semester_number, course_id)
);

create table cohort (
  id bigserial primary key,
  career_id bigint not null references career(id),
  curriculum_id bigint not null,
  semester_number int not null check (semester_number > 0),
  section text not null,
  journey_id bigint not null references journey(id),
  expected_students int not null check (expected_students >= 0),
  active boolean not null default true,
  foreign key (curriculum_id, career_id) references curriculum(id, career_id),
  unique (career_id, curriculum_id, semester_number, section, journey_id)
);

create table teacher (
  id bigserial primary key,
  code text not null unique,
  full_name text not null,
  priority int not null default 0,
  min_courses int not null default 1 check (min_courses >= 0),
  max_courses int not null default 6 check (max_courses >= min_courses),
  preferences jsonb not null default '{}'::jsonb,
  active boolean not null default true
);

create table teacher_course (
  teacher_id bigint not null references teacher(id),
  course_id bigint not null references course(id),
  preference int not null default 0,
  primary key (teacher_id, course_id)
);

create table teacher_availability (
  id bigserial primary key,
  teacher_id bigint not null references teacher(id),
  journey_id bigint references journey(id),
  day_of_week smallint not null check (day_of_week between 1 and 7),
  start_block int not null check (start_block >= 0),
  duration_blocks int not null check (duration_blocks > 0),
  preference int not null default 0,
  source text not null default 'PORTAL'
);

create table room (
  id bigserial primary key,
  code text not null unique,
  capacity int not null check (capacity > 0),
  type room_type not null,
  floor int not null,
  number int not null,
  active boolean not null default true
);

create table resource (
  id bigserial primary key,
  code text not null unique,
  name text not null
);

create table room_resource (
  room_id bigint not null references room(id),
  resource_id bigint not null references resource(id),
  primary key (room_id, resource_id)
);

create table course_required_resource (
  course_id bigint not null references course(id),
  resource_id bigint not null references resource(id),
  primary key (course_id, resource_id)
);

create table app_user (
  id bigserial primary key,
  email text not null unique,
  password_hash text not null,
  full_name text not null,
  role user_role not null,
  teacher_id bigint references teacher(id),
  cohort_id bigint references cohort(id),
  active boolean not null default true,
  created_at timestamptz not null default now(),
  check (
    (role = 'TEACHER' and teacher_id is not null and cohort_id is null)
    or (role = 'STUDENT' and teacher_id is null and cohort_id is not null)
    or (role in ('SUPERADMIN', 'ADMIN') and teacher_id is null and cohort_id is null)
  )
);

create table common_area_rule (
  id bigserial primary key,
  code text not null unique,
  course_id bigint not null references course(id),
  journey_id bigint not null references journey(id),
  semester_number int not null check (semester_number > 0),
  name text,
  active boolean not null default true
);

create table common_area_career (
  common_area_rule_id bigint not null references common_area_rule(id),
  career_id bigint not null references career(id),
  curriculum_id bigint not null,
  foreign key (curriculum_id, career_id) references curriculum(id, career_id),
  primary key (common_area_rule_id, career_id, curriculum_id)
);

create unique index uq_common_area_rule_active
  on common_area_rule (course_id, journey_id, semester_number, code)
  where active;

create table schedule_plan (
  id bigserial primary key,
  code text not null unique,
  name text not null,
  schedule_type schedule_type not null default 'CLASSES',
  status plan_status not null default 'DRAFT',
  start_date date not null,
  end_date date not null,
  config jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  check (start_date <= end_date)
);

create table schedule_session_group (
  id bigserial primary key,
  plan_id bigint not null references schedule_plan(id),
  course_id bigint not null references course(id),
  common_area_rule_id bigint references common_area_rule(id),
  continuity_teacher boolean not null default true,
  label text,
  unique (id, plan_id)
);

create table schedule_session (
  id bigserial primary key,
  plan_id bigint not null references schedule_plan(id),
  session_group_id bigint not null,
  course_id bigint not null references course(id),
  common_area_rule_id bigint references common_area_rule(id),
  duration_blocks int not null check (duration_blocks > 0),
  weekly_index int not null default 1 check (weekly_index > 0),
  is_common_area boolean not null default false,
  required_room_type room_type,
  metadata jsonb not null default '{}'::jsonb,
  unique (id, plan_id),
  foreign key (session_group_id, plan_id) references schedule_session_group(id, plan_id),
  check (
    (is_common_area and common_area_rule_id is not null)
    or (not is_common_area and common_area_rule_id is null)
  )
);

create table schedule_session_cohort (
  session_id bigint not null references schedule_session(id) on delete cascade,
  cohort_id bigint not null references cohort(id),
  primary key (session_id, cohort_id)
);

create table schedule_run (
  id bigserial primary key,
  plan_id bigint not null references schedule_plan(id),
  run_number int not null,
  solver_mode text not null default 'NORMAL',
  seed bigint,
  engine_version text not null,
  status schedule_run_status not null,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  config jsonb not null default '{}'::jsonb,
  input_snapshot jsonb not null,
  output_snapshot jsonb not null default '{}'::jsonb,
  score_total numeric,
  score_breakdown jsonb not null default '{}'::jsonb,
  unique (id, plan_id),
  unique (plan_id, run_number)
);

create table schedule_assignment (
  id bigserial primary key,
  plan_id bigint not null references schedule_plan(id),
  run_id bigint not null,
  session_id bigint not null,
  teacher_id bigint references teacher(id),
  room_id bigint references room(id),
  start_time_block_id bigint references time_block(id),
  duration_blocks int check (duration_blocks > 0),
  status assignment_status not null default 'ASSIGNED',
  pinned boolean not null default false,
  unassigned_reason text,
  unique (run_id, session_id),
  foreign key (run_id, plan_id) references schedule_run(id, plan_id),
  foreign key (session_id, plan_id) references schedule_session(id, plan_id),
  check (
    (
      status = 'ASSIGNED'
      and teacher_id is not null
      and room_id is not null
      and start_time_block_id is not null
      and duration_blocks is not null
      and unassigned_reason is null
    )
    or
    (
      status = 'UNASSIGNED'
      and teacher_id is null
      and room_id is null
      and start_time_block_id is null
      and duration_blocks is null
      and pinned = false
      and unassigned_reason is not null
    )
  )
);

create table pre_validation_issue (
  id bigserial primary key,
  plan_id bigint not null references schedule_plan(id),
  severity issue_severity not null,
  code text not null,
  entity_type text,
  entity_id bigint,
  message text not null,
  suggested_action text,
  source jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now()
);

create table schedule_violation (
  id bigserial primary key,
  run_id bigint not null references schedule_run(id),
  severity issue_severity not null,
  code text not null,
  message text not null,
  affected_entities jsonb not null default '[]'::jsonb,
  cost numeric not null default 0
);

create table section_suggestion (
  id bigserial primary key,
  plan_id bigint not null references schedule_plan(id),
  cohort_id bigint not null references cohort(id),
  demand int not null check (demand >= 0),
  max_capacity int not null check (max_capacity >= 0),
  suggested_section text not null,
  reason text not null,
  approved boolean,
  approved_by bigint references app_user(id),
  approved_at timestamptz
);

create table manual_edit (
  id bigserial primary key,
  plan_id bigint not null references schedule_plan(id),
  base_run_id bigint,
  result_run_id bigint,
  session_id bigint,
  requested_by bigint not null references app_user(id),
  client_request_id text,
  request_payload jsonb not null default '{}'::jsonb,
  target_teacher_id bigint references teacher(id),
  target_room_id bigint references room(id),
  target_time_block_id bigint references time_block(id),
  status manual_edit_status not null,
  pinned_session_ids jsonb not null default '[]'::jsonb,
  neighborhood_session_ids jsonb not null default '[]'::jsonb,
  moved_session_ids jsonb not null default '[]'::jsonb,
  remaining_violations jsonb not null default '[]'::jsonb,
  repair_metadata jsonb not null default '{}'::jsonb,
  score_before numeric,
  score_after numeric,
  repair_time_ms int,
  created_at timestamptz not null default now(),
  foreign key (base_run_id, plan_id) references schedule_run(id, plan_id),
  foreign key (result_run_id, plan_id) references schedule_run(id, plan_id),
  foreign key (session_id, plan_id) references schedule_session(id, plan_id),
  check (repair_time_ms is null or repair_time_ms >= 0),
  check (
    (
      status in (
        'APPLIED_CLEAN',
        'APPLIED_WITH_REPAIR',
        'APPLIED_WITH_REMAINING_CONFLICTS'
      )
      and result_run_id is not null
      and base_run_id is not null
      and session_id is not null
      and (
        target_teacher_id is not null
        or target_room_id is not null
        or target_time_block_id is not null
      )
    )
    or
    (
      status in ('REJECTED_BY_STATE', 'REJECTED_BY_INPUT')
      and result_run_id is null
    )
  )
);

create table substitution_event (
  id bigserial primary key,
  assignment_id bigint not null references schedule_assignment(id),
  original_teacher_id bigint not null references teacher(id),
  substitute_teacher_id bigint not null references teacher(id),
  starts_at timestamptz not null,
  ends_at timestamptz,
  is_permanent boolean not null default false,
  reason text,
  created_by bigint not null references app_user(id),
  created_at timestamptz not null default now(),
  check (ends_at is null or starts_at <= ends_at)
);

create table import_batch (
  id bigserial primary key,
  uploaded_by bigint not null references app_user(id),
  filename text not null,
  status import_batch_status not null,
  started_at timestamptz not null default now(),
  finished_at timestamptz,
  input_snapshot jsonb not null default '{}'::jsonb,
  summary jsonb not null default '{}'::jsonb
);

create table import_error (
  id bigserial primary key,
  import_batch_id bigint not null references import_batch(id) on delete cascade,
  sheet_name text,
  row_number int,
  column_name text,
  raw_value text,
  code text not null,
  message text not null,
  suggested_action text
);

create index idx_cohort_lookup
  on cohort (career_id, curriculum_id, semester_number, journey_id);

create index idx_teacher_availability_teacher
  on teacher_availability (teacher_id, day_of_week, start_block);

create index idx_schedule_session_plan
  on schedule_session (plan_id);

create index idx_schedule_assignment_run
  on schedule_assignment (run_id);

create index idx_schedule_assignment_teacher_slot
  on schedule_assignment (run_id, teacher_id, start_time_block_id);

create index idx_schedule_assignment_room_slot
  on schedule_assignment (run_id, room_id, start_time_block_id);

create index idx_schedule_violation_run
  on schedule_violation (run_id);

create index idx_pre_validation_plan
  on pre_validation_issue (plan_id, severity);

create index idx_manual_edit_plan_created
  on manual_edit (plan_id, created_at);

create index idx_manual_edit_base_run
  on manual_edit (base_run_id);

create unique index idx_manual_edit_idempotency
  on manual_edit (plan_id, client_request_id)
  where client_request_id is not null;

create view exam_plan as
select *
from schedule_plan
where schedule_type = 'EXAMS';
