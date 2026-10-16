# ContextOS v0.6 — Merge readiness (Phases 5 and 6 + every requested idea)

**Decision: NOT MERGE-READY. Do not create a PR until full acceptance is met.**

All feature development occurs on `feature/phase5-6-all-integrations`.
The only GitHub Actions workflow remains push-to-`main` only, model-free.
Do not change the workflow to run for feature branches or pull requests.

## Feature implementation inventory

| Requirement | Current source | Remaining acceptance criteria |
|---|---|---|
| Gmail | OAuth request, previews, explicit selected full-message text import | Real Google account OAuth, proper app registration/verification, security/privacy and revocation QA |
| Google Drive | OAuth preview, selected plain text/Google Docs and CSV export import | Real account QA; additional file formats and OAuth scopes reviewed |
| Google Calendar + Android Calendar | Upcoming event previews with destination/date; optional individually saved | Real API/provider tests, access revocation and timezone QA |
| Health Connect | Consent-gated seven-day steps aggregation | Device permissions/provider tests and app-policy declaration for distribution |
| NotificationListenerService | Opt-in allowlisted package *counts only*, no bodies | Settings enable/disable and OEM/battery saver tests |
| Selected photos/documents | Android pickers, offline OCR/labels, PDF text and 5-page bounded scanned-PDF OCR | Physical-device RAM/latency tests, malformed/large files, accessibility |
| Timeline history | Opt-in import of known export JSON variants; historical trip median | Validate with real Timeline exports and changed schemas |
| WhatsApp | User-exported .txt conversation parsing grouped by date, local encryption | Locale/date-format test matrix and export QA; private app storage remains inaccessible |
| Offline searchable archive/RAG | AES-GCM encrypted 160-entry knowledge vault, temporal queries, optional semantic retrieval via imported .tflite, consent-gated chat excerpts | Device semantic relevance benchmarks, sources/accuracy testing, no-cross-leak verification |
| Smart commute | Historical median; Google Routes traffic-aware live estimate, delay warning, event destinations, suggested leave-by time | Real Routes API with billed/project-restricted key, calendar scenarios, proactive traffic recheck decisions |
| Career and learning companion | Encrypted projects/deadlines, completion tracking, saved selected email/calendar entries, topic-specific practice suggestions | Personalized study-plan progression, real sourced inbox/calendar QA |
| Proactive personal agent | WorkManager deadline reminders, foreground suggestions, per-item ratings, quiet hours, daily caps and feedback suppression | Test doze, notification permissions, user approvals, scheduling reliability |
| Adaptive ranking | Persisted Helpful/Not useful feedback ranks agent suggestions and suppresses dismissed reminders | Longer-term adaptation evaluation, bias/error safeguards |
| Multimodal perception | User-selected image OCR + generic offline image labels | Model/device evaluation, no unsupported claims about vision understanding |
| Offline voice | Android 12+ on-device SpeechRecognizer with runtime mic consent; offline-only read-aloud TTS | Devices/languages with offline recognizer and offline installed voice packs |
| Optional cloud fallback | Confirmed one-shot HTTPS chat completion with per-request typed key | Provider interoperability, error/cancellation/security QA; not automatic |

**No background reading of Gmail, Drive, WhatsApp message bodies or Health Connect.**
No bypass of Android app sandbox or OAuth scope approval.

## Blockers preventing a merge-ready PR

1. **Google account integration:** Android OAuth registration for
   `dev.contextos.app` with a **stable signing certificate SHA-1**, consent screen,
   enabled Gmail/Drive/Calendar APIs and test account. Restricted-scope verification
   (and possibly independent assessment) is outside a code-only build.
   The `revokeAccess` code is implemented, but needs real account QA.
2. **Live traffic:** Google Routes API enabled with billing and an appropriately
   restricted key. No shared API key is committed or embedded in the APK.
3. **Physical Android testing:** Health Connect data scope approvals;
   notification listener permission/revocation and OEM battery optimization;
   local ASR/TTS; photo OCR and PDF memory use; encrypted vault recovery.
4. **Compile/test verification:** No Android SDK is available in this execution
   environment. The main-only workflow intentionally does not build this branch,
   so *new integration code has NOT passed Android CI*. No APK should be called
   verified or distributable from this branch.
5. **Remaining feature depth:** User-approved periodic commute refreshes/alerts,
   broader provider/source coverage, agent action approvals, semantic quality evaluation
   and accessibility/performance tests for full acceptance.
6. **Distribution:** Need an accurate privacy policy and any Google OAuth, Gmail
   restricted-scope and Health Connect app/policy review. No permission bypass exists.

## Most recent source improvements

- Selected Gmail/Drive full text, signed-in Google account revocation, local Google
  archive removal on successful revocation.
- Semantic search for imported documents using the same optional on-device model
  as personal memory, with lexical and structured-date fallback; prompt excerpt bounds.
- Imported WhatsApp `.txt` date-grouped conversations; bounded scanned-PDF OCR.
- Calendar-linked Google Routes departure time and traffic delay warning.
- Feedback-weighted agent suggestions plus career preparation topics; dismissed
  deadline alerts are suppressed in the background worker.
- Offline-only on-device speech input and offline-voice playback control.
- Session reset when imported data or chat consent changes.
- Updated APK verification: Qwen/LiteRT-LM and user .tflite weights excluded, while
  required small bundled ML Kit OCR/classifier library assets are allowed.
- Added unit test sources for export parsing, semantic rule gating, stream bounds,
  commute delay/leave-by calculations and feedback ranking (not yet executed in CI).

### User validation checklist before merge

- [ ] Configure developer-owned Google Cloud project + OAuth test user and signing.
- [ ] Configure Routes API test billing/key.
- [ ] Build a test APK locally without modifying main-only Actions triggers.
- [ ] Test real Gmail, Drive and Google Calendar authorization/revocation.
- [ ] Test representative Timeline/WhatsApp/PDF/photo samples and model-memory retrieval.
- [ ] Test Android 13–16 permissions, notifications, voice, Health Connect, user deletion.
- [ ] Complete benchmark, accessibility, security and error-path checks.
- [ ] Only then create a reviewed PR to main; user performs final merge.

Read `PHASE5-6-ALL-IDEAS.md` for module behavior and integration prerequisites.
