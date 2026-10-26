# Supabase backend

The apps work offline without it. To turn on accounts and sync:

1. Create a project on [supabase.com](https://supabase.com).
2. In **SQL Editor**, run the files in `migrations/` in order (oldest first).
3. In **Authentication → Sign In / Providers**, keep **Email** enabled. For testing you can turn off
   "Confirm email" so new accounts can sign in straight away.
4. Copy **Project URL** and the **anon / publishable key** (Project Settings → API) into `shared/supabase.json`:

   ```json
   { "url": "https://xxxx.supabase.co", "anonKey": "eyJ..." }
   ```

   The anon key is meant to ship inside apps; row level security limits every user to their own rows.
   Never put the `service_role` key in this file.

## How sync works

- Every row has a client-made UUID, `updated_at` from the device that last changed it, and a soft-delete `deleted_at`.
- **Push:** each device upserts the rows it changed since its last push (parents before children).
  The `sync_guard` trigger keeps whichever version has the newer `updated_at` (last write wins).
- **Pull:** each device reads rows whose `synced_at` (the server's clock) is newer than its last pull,
  and applies the ones that are newer than its local copy.
- Signing out syncs first, then removes that account's data from the device.

## Buddy AI (Pro)

Buddy's AI answers come from the `coach` Edge Function, which calls the Claude API. The API key stays in
Supabase; the apps never see it.

1. Run `migrations/20261026020000_coach.sql` (chat history, cached reports, monthly usage).
2. Create an API key at [console.anthropic.com](https://console.anthropic.com) and store it as a secret:

   ```sh
   supabase secrets set ANTHROPIC_API_KEY=sk-ant-...
   ```

3. Deploy the function: `supabase functions deploy coach`.

Models: Claude Haiku 5.5 for chat and workout notes, Claude Sonnet 5.5 for weekly reports and routine design.
Monthly limits per account (in `functions/coach/index.ts`): free 5 chat messages; Pro 300 chat messages,
60 workout notes, 6 weekly reports, 10 routines; lifetime Pro 50 chat messages and 5 routines.
`coach_usage` records requests and tokens per user and month, for checking the bill.

Pro is bought through Google Play / the App Store and is not yet verified on the server: the function trusts
the plan the app sends, and the monthly limits cap what one account can cost.
