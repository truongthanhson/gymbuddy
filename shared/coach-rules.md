# Coach Engine rules

The Coach Engine is the rule-based half of Buddy. It runs on the phone, offline, for every user (free and Pro).
Android (`domain/Coach.kt`) and iOS (`Domain/Coach.swift`) implement the same rules and both run
`test-vectors/coach.json`. Buddy AI (Pro) only explains and personalises these results; it never computes weights itself.

Times are epoch milliseconds in the vectors, weights are kg. `D` is a day, `H` an hour.

## Working sets

A working set is a set that is not a warm-up, with `reps > 0` and `weightKg > 0`. A session is one exercise in one
finished workout. Sessions without working sets are ignored by every rule below.

## Next target (`nextTarget`)

Only for exercises logged with weight and reps: `barbell`, `dumbbell`, `machine`, `cable`, `weighted_bodyweight`.
Other categories (assisted, reps only, cardio, duration) get no target.

From the latest session:

- **Top sets** are the working sets at the heaviest weight. `topReps` is their highest rep count, `minReps` their lowest.
- **Rep range** comes from `topReps`: up to 6 → 4–6, up to 12 → 8–12, more → 12–20.
- **Step**: `dumbbell` 2 kg, otherwise `legs` 5 kg, otherwise 2.5 kg.

Then, in order:

1. **Deload** when the exercise is stalled (below): weight = 90% of the top weight rounded *down* to the step, reps = `minReps`.
2. **Increase** when `minReps` reached the top of the range: weight + step, reps = bottom of the range.
3. **Repeat** otherwise: same weight, reps = `minReps + 1` (at most the top of the range).

### Stalled

Either of:

- **Fatigue**: the last two sessions each have an average RPE of 9 or more over their working sets that logged RPE
  (a session with no RPE does not count as fatigued).
- **Plateau**: at least 4 sessions, and the best estimated 1RM (Epley, `one_rep_max.json`) of the last 3 sessions
  is not higher than the best of all earlier sessions.

## Recovery (`recovery`)

Per body part, from completed non-warm-up sets of the last 28 days (`startedAt` in `[now - 28D, now]`). `cardio`,
`other` and unknown body parts are left out. `hoursSince` = whole hours since the latest such set. Status is
`recovering` under 48 hours, else `ready`. Most rested first; ties keep the body part order of `body_part_sets.json`.

## Volume (`volume`)

Per body part (same order, `cardio` and `other` left out), count non-warm-up sets in three windows:
this week `(now - 7D, now]`, last week `(now - 14D, now - 7D]`, and before `(now - 42D, now - 14D]`.

- `volume_spike` (value = this week's sets) when last week had at least 6 sets and this week has more than 1.3× last week.
- `neglected` (value = whole days since that body part's latest set) when this week and last week are both 0
  and the window before had at least 6 sets.

Spikes come first, then neglects, each in body part order.

## Streak (`streak`)

Blocks of 7 days counted back from now: block `i` is `(now - 7(i+1)D, now - 7iD]`. A block is met when it holds at
least `goalPerWeek` workouts. If the current block is not met yet, counting starts at the previous block. The streak is
the number of met blocks in a row.

## Insights (`insights`)

The list Buddy shows, most urgent first:

1. `inactive` (value = whole days since the latest workout) when that is 7 days or more.
2. `deload` for each exercise whose next target is a deload and whose latest session is at most 14 days old, most
   recent first, with `exerciseId`, `weightKg`, `reps`.
3. `volume_spike` entries, then `neglected` entries, from Volume.
4. `streak` (value = weeks) when the streak is 2 or more.

## Training reminder (`reminder`)

Buddy nudges the person when they fall behind their weekly goal. With `daysPerWeek` clamped to 1–7, the gap is
`max(2, 7 / daysPerWeek + 1)` whole days (integer division): 3 a week → 3 days, 4 → 2, 1 → 8. The reminder is due at
`lastWorkout + gap × D`; with no workout yet there is none. The apps show it at the first 09:00 local time at or after
that moment, at most once per gap, and only when reminders are on.
