# Phase 3 (initial slice) — on-device insights + Android keyboard/layout fixes

Status: Source implementation on feature branch. The app must still be installed on a real phone
to validate Samsung/Android 15 IME behavior and local LiteRT-LM inference.

## Delivered

- Deterministic, auditable local InsightEngine with unit tests (opt-out, signal confidence, ordering, capacity).
- Up to 3 ranked recommendations on Home based only on available opted-in context:
  recent foreground ambient light, last observed motion, on-demand 24-hour app usage,
  session-only steps, and presence of saved SQLite snapshots.
- A persisted, OFF-by-default "Enable explainable recommendations" switch. This is
  **separate** from the OFF-by-default "Allow AI to use live context" switch.
- Recommendations display their source and reason. No health inference or promises of
  walking/driving recognition; absence of data yields a non-sensitive check-in.
- Foreground sensor observations are time-stamped and accepted for Home insight
  evaluation only if observed within five minutes. No sensor polling on Home.
- Chat/status keyboard fixes: window edge-to-edge explicitly configured,
  status bar padding for custom toolbar, composer shifted above Android IME,
  bottom navigation hidden while typing, compact empty state while IME is visible,
  draft preservation across Compose recreation, send action via keyboard,
  focusable suggestion chips and message scroll on IME show.
- Inference system instruction accurately describes explicitly provided contextual
  summaries without pretending to have live device access.
- Version 0.3.0 (Phase 3 preview). No INTERNET permission added.

## Privacy model

Everything runs on-device. Insight calculation does not make a model call and does
not introduce a service, daemon, scheduler, persistent activity tracker or notification.
Android UsageStats are approximate and may double count apps with overlapping use.
Sensor readings are only acquired while the Context tab is visible and enabled.
Saved snapshots remain user-initiated in app-private SQLite, unencrypted at rest.
Data deletion in Context remains available.

## Device QA checklist (especially Samsung / Android 15)

1. Open Home and Settings in dark and light modes: verify title/toolbar clears
   the clock, camera cutout and status bar icons.
2. Chat empty state, tap composer: Gboard opens, nav bar disappears, composer
   stays above keyboard. Suggestions should not obscure input.
3. Test multi-line messages, keyboard Send, button Send, rotate device, back-dismiss
   keyboard, switch tabs, 3-button navigation, gesture navigation, and largest font.
4. Load the bundled/previously imported LiteRT-LM model and send multiple
   prompts; confirm replies still stream with no network.
5. Disable insights and verify there are no recommendations. Enable insights,
   refresh each source through Context, then return Home to inspect provenance.
6. Wait more than five minutes since sensor reading; do not use its old light/motion
   value for Home recommendations. Revoke permissions and retest.
7. Run `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`.

## Next Phase 3 increments

- Explicit user-managed local routines and explainable "why now" preferences.
- Reminder scheduling only after a separate, informed notification-permission UX;
  never imply background monitoring has been implemented.
- Export/delete controls for any future personal memory, with encryption before
  storing sensitive user-authored memory and tests for consent / state migration.
- Physical-device profiling, accessibility tests, IME animation smoothness and
  scroll-to-latest behavior under long streaming responses.
