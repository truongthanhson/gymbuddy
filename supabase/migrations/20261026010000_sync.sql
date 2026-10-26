-- Incremental sync.
-- Clients push rows with upsert (on conflict id) and pull rows whose synced_at is newer than their cursor.
-- synced_at is the server's clock, so a row pushed late by an offline device is still picked up by other devices.
-- The sync_guard trigger enforces last-write-wins on the client's updated_at:
-- an upsert that is not newer than the stored row is skipped, so re-pushing a row is a no-op.

create function public.sync_guard() returns trigger
language plpgsql set search_path = '' as $$
begin
  if tg_op = 'UPDATE' and new.updated_at <= old.updated_at then
    return null;
  end if;
  new.synced_at := clock_timestamp();
  return new;
end;
$$;

do $$
declare
  t text;
begin
  foreach t in array array[
    'custom_exercises', 'folders', 'templates', 'template_exercises', 'template_sets',
    'workouts', 'workout_exercises', 'workout_sets', 'measurements'
  ] loop
    execute format('alter table public.%I add column synced_at timestamptz not null default clock_timestamp()', t);
    execute format('create index on public.%I (user_id, synced_at)', t);
    execute format(
      'create trigger sync_guard before insert or update on public.%I '
      'for each row execute function public.sync_guard()', t);
  end loop;
end $$;
