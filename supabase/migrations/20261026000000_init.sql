-- GymBuddy initial schema.
-- Clients generate UUIDs and timestamps so rows can be created offline and synced later.
-- Sync rule: the row with the newer updated_at wins; deletes are soft (deleted_at).
-- Built-in exercises ship inside the apps (shared/exercises.json) and are referenced by their text id;
-- only custom exercises are stored here.

create extension if not exists moddatetime schema extensions;

-- Shared columns: id, user_id, created_at, updated_at, deleted_at.

create table public.profiles (
  user_id uuid primary key references auth.users (id) on delete cascade,
  display_name text,
  weight_unit text not null default 'kg' check (weight_unit in ('kg', 'lb')),
  distance_unit text not null default 'km' check (distance_unit in ('km', 'mi')),
  language text not null default 'vi' check (language in ('vi', 'en')),
  default_rest_seconds integer not null default 120 check (default_rest_seconds >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.custom_exercises (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  category text not null,
  body_part text not null,
  primary_muscle text,
  secondary_muscles text[] not null default '{}',
  instructions text,
  default_rest_seconds integer,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.folders (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  name text not null,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.templates (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  folder_id uuid references public.folders (id) on delete set null,
  name text not null,
  note text,
  sort_order integer not null default 0,
  last_used_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.template_exercises (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  template_id uuid not null references public.templates (id) on delete cascade,
  exercise_id text not null, -- built-in slug or custom_exercises.id
  sort_order integer not null default 0,
  superset_group integer,
  rest_seconds integer,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.template_sets (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  template_exercise_id uuid not null references public.template_exercises (id) on delete cascade,
  sort_order integer not null default 0,
  set_type text not null default 'normal' check (set_type in ('warmup', 'normal', 'drop', 'failure')),
  target_weight_kg numeric(7, 2),
  target_reps integer,
  target_duration_sec integer,
  target_distance_m numeric(10, 2),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.workouts (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  template_id uuid references public.templates (id) on delete set null,
  name text not null,
  note text,
  started_at timestamptz not null,
  ended_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.workout_exercises (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  workout_id uuid not null references public.workouts (id) on delete cascade,
  exercise_id text not null,
  sort_order integer not null default 0,
  superset_group integer,
  rest_seconds integer,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.workout_sets (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  workout_exercise_id uuid not null references public.workout_exercises (id) on delete cascade,
  sort_order integer not null default 0,
  set_type text not null default 'normal' check (set_type in ('warmup', 'normal', 'drop', 'failure')),
  weight_kg numeric(7, 2),
  reps integer,
  duration_sec integer,
  distance_m numeric(10, 2),
  rpe numeric(3, 1) check (rpe between 1 and 10),
  completed boolean not null default false,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create table public.measurements (
  id uuid primary key,
  user_id uuid not null default auth.uid() references auth.users (id) on delete cascade,
  kind text not null, -- weight, body_fat, neck, chest, waist, hips, arm_left, arm_right, thigh_left, thigh_right, calf_left, calf_right
  value numeric(8, 2) not null,
  unit text not null, -- kg, percent, cm
  measured_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

-- Indexes for incremental sync ("give me everything changed since X") and common lookups.
create index on public.custom_exercises (user_id, updated_at);
create index on public.folders (user_id, updated_at);
create index on public.templates (user_id, updated_at);
create index on public.template_exercises (user_id, updated_at);
create index on public.template_sets (user_id, updated_at);
create index on public.workouts (user_id, updated_at);
create index on public.workouts (user_id, started_at desc);
create index on public.workout_exercises (user_id, updated_at);
create index on public.workout_exercises (user_id, exercise_id);
create index on public.workout_sets (user_id, updated_at);
create index on public.measurements (user_id, updated_at);

-- Row Level Security: every user only sees and writes their own rows.
do $$
declare
  t text;
begin
  foreach t in array array[
    'custom_exercises', 'folders', 'templates', 'template_exercises', 'template_sets',
    'workouts', 'workout_exercises', 'workout_sets', 'measurements'
  ] loop
    execute format('alter table public.%I enable row level security', t);
    execute format(
      'create policy "own rows" on public.%I for all to authenticated '
      'using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()))', t);
  end loop;
end $$;

alter table public.profiles enable row level security;
create policy "own profile" on public.profiles for all to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));

-- Create a profile row when a user signs up.
create function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (user_id, display_name)
  values (new.id, new.raw_user_meta_data ->> 'full_name');
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- profiles is the only table whose updated_at the server maintains; synced tables keep the client's value.
create trigger profiles_updated_at before update on public.profiles
  for each row execute function extensions.moddatetime (updated_at);
