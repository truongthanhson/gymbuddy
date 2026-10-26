# Phone ↔ watch protocol

The watch app is a remote for the workout running on the phone. The phone owns all data; the watch only
shows the active workout and sends actions back. Both platforms use the same JSON.

## State (phone → watch)

Android: Data Layer item at path `/gymbuddy/state`, string key `json`.
iOS: WatchConnectivity application context, string key `json`.

```json
{
  "workout": {
    "name": "Push day",
    "startedAt": 1761465600000,
    "restEndsAt": 1761465690000,
    "exercises": [
      {
        "name": "Bench Press",
        "sets": [
          { "id": "s1", "label": "W", "weight": "40", "reps": "10", "unit": "kg", "done": true },
          { "id": "s2", "label": "1", "weight": "60", "reps": "8", "unit": "kg", "done": false }
        ]
      }
    ]
  }
}
```

- `workout` is `null` when no workout is in progress.
- Times are milliseconds since the epoch. `restEndsAt` is `null` when no rest timer runs.
- `label` is `W`, `D`, `F` for warm-up, drop and failure sets, otherwise the number of the normal set.
- `weight` and `reps` are already formatted in the user's unit; an empty string means not filled in yet.
  For an empty set the phone shows the previous session's values as a hint, and the watch shows them too:
  `hintWeight`, `hintReps` (optional).
- Exercise names are in the phone's language.

## Actions (watch → phone)

Android: message to path `/gymbuddy/action`. iOS: `sendMessage` with string key `json`.

```json
{ "action": "completeSet", "setId": "s2" }
{ "action": "skipRest" }
```

`completeSet` behaves like ticking the set on the phone: an empty set is filled from the previous session
and the rest timer starts.

The cases in `test-vectors/watch.json` are run by both apps.
