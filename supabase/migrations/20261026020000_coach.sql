-- Buddy AI (Pro): chat history, cached reports and monthly usage, written by the `coach` Edge Function.
-- The function uses the service role; people can read (and delete) only their own rows.

create table public.coach_messages (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  role text not null check (role in ('user', 'assistant')),
  content text not null,
  created_at timestamptz not null default now()
);
create index on public.coach_messages (user_id, created_at);

-- Weekly reports and workout summaries, so opening one again costs nothing.
-- ref is the week's start date (weekly), the workout id (summary) or a request hash (routine).
create table public.coach_reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users (id) on delete cascade,
  kind text not null check (kind in ('weekly', 'summary', 'routine')),
  ref text not null,
  content jsonb not null,
  created_at timestamptz not null default now(),
  unique (user_id, kind, ref)
);

create table public.coach_usage (
  user_id uuid not null references auth.users (id) on delete cascade,
  month text not null, -- 'YYYY-MM', UTC
  kind text not null,
  requests integer not null default 0,
  input_tokens bigint not null default 0,
  output_tokens bigint not null default 0,
  primary key (user_id, month, kind)
);

alter table public.coach_messages enable row level security;
alter table public.coach_reports enable row level security;
alter table public.coach_usage enable row level security;

create policy "own messages" on public.coach_messages for select using (user_id = auth.uid());
create policy "delete own messages" on public.coach_messages for delete using (user_id = auth.uid());
create policy "own reports" on public.coach_reports for select using (user_id = auth.uid());
create policy "delete own reports" on public.coach_reports for delete using (user_id = auth.uid());
create policy "own usage" on public.coach_usage for select using (user_id = auth.uid());

-- Counts one request if the month's count is under p_limit; returns whether it was allowed.
create function public.coach_consume(p_user uuid, p_month text, p_kind text, p_limit integer)
returns boolean
language plpgsql security definer set search_path = '' as $$
declare
  used integer;
begin
  insert into public.coach_usage (user_id, month, kind) values (p_user, p_month, p_kind)
    on conflict do nothing;
  select requests into used from public.coach_usage
    where user_id = p_user and month = p_month and kind = p_kind for update;
  if used >= p_limit then
    return false;
  end if;
  update public.coach_usage set requests = requests + 1
    where user_id = p_user and month = p_month and kind = p_kind;
  return true;
end;
$$;

-- Token counts after a request, for cost tracking.
create function public.coach_add_tokens(p_user uuid, p_month text, p_kind text, p_input bigint, p_output bigint)
returns void
language sql security definer set search_path = '' as $$
  update public.coach_usage
    set input_tokens = input_tokens + p_input, output_tokens = output_tokens + p_output
    where user_id = p_user and month = p_month and kind = p_kind;
$$;

revoke execute on function public.coach_consume from public, anon, authenticated;
revoke execute on function public.coach_add_tokens from public, anon, authenticated;
