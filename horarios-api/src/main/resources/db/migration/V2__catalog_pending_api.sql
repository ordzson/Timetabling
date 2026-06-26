alter table teacher_course
  add column id bigserial unique;

alter table common_area_career
  add column id bigserial unique;

create table teacher_career_journey (
  id bigserial primary key,
  teacher_id bigint not null references teacher(id),
  career_id bigint not null references career(id),
  journey_id bigint not null references journey(id),
  active boolean not null default true,
  unique (teacher_id, career_id, journey_id)
);

create index idx_teacher_career_journey_teacher
  on teacher_career_journey (teacher_id, active);
