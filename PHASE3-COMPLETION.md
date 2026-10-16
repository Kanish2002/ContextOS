# Phase 3 completion pass — personalized suggestions

This branch completes the missing user-requested Phase 3 functions on top of
`feature/phase4-encrypted-personal-memory`. Android application version 0.5.0.

## Rules + personalization
- Existing deterministic, explainable InsightEngine remains the only source of recommendations.
- Ratings: Helpful boosts the suggestion; Not helpful suppresses the suggestion.
  Feedback survives restarts in private app preferences; reset available in Settings.
- A user can tap **Explain with offline AI** to send the rule and its source/evidence
  to LiteRT-LM. Explanation is a user-initiated chat message; no automatic inference.
  AI explanations may be fallible, and the rule's original provenance remains visible.

## Notifications
- OFF by default. Android 13+ requires explicit POST_NOTIFICATIONS permission.
- WorkManager schedules inexact periodic checks (about hourly), not exact alarms.
- The worker does not collect sensors, location or usage data, and never calls an LLM.
- It only reminds about a suggestion the user explicitly rated **Helpful**, for
  up to seven days after rating. It clearly says it is an older suggestion, not a
  real-time context reading.
- User-configured quiet hours (including overnight windows) skip notifications.
- User-configured daily cap 0-3, default 1; enforced local-day accounting and
  a six-hour cooldown; normal OS notification controls always take precedence.
- Same quiet-hour start/end is interpreted as silent all day.
- Disabling cancels periodic work; feedback reset deletes reminder candidate.
- WorkManager is inexact under Doze/battery saver; skipped quiet-hour notifications
  are not requeued, so "delivery at 8 AM" is not promised.

## Safety and remaining verification
- No Internet permission, background sensing or remote AI inference.
- No full behavioral/activity detection; movement/light are estimates.
- CI unit tests include quiet-hour/cooldown/cap boundaries and rule ordering.
- **Physical Android 16 / Samsung tests are still needed** for notification permission,
  quiet hours, real delivery under battery optimization, and all appearance modes.
- A real local LLM model must be imported or pre-bundled to use AI explanations.
