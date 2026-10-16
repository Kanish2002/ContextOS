# ContextOS feature/phase5-6-all-integrations — PR readiness

**NOT READY FOR A MERGE-READY PR.** The user specifically requested a pull request
only after every proposed feature has been completed and validated.

## Source implementation status (not equivalent to device-verified behavior)

| Requested idea | Currently working in source | To mark fully complete |
|---|---|---|
| Selected documents/files | Android SAF + bounded local import, encrypted archive | Test providers, malformed and large PDFs, text encoding, file quota |
| Photos/multimodal | Photo Picker, local ML Kit OCR and generic labels | Test on devices, multi-script OCR, semantics/false positives |
| Private offline search & RAG | Categorized encrypted knowledge store, lexical temporal questions and bounded chat retrieval | Embedding retrieval for imported documents, evaluation of accuracy and source citations |
| Gmail | Google OAuth per-source permission, user-triggered message previews and selected full message imports | Configured OAuth signing/consent, Gmail restricted-scope review/verification, account disconnect/revocation, real-device test |
| Drive | OAuth, metadata preview, selected text document export/import | OAuth configuration, authorization verification, richer Drive file coverage and identity/revocation |
| Calendar | Android calendar permission, Google Calendar previews with start times | End-to-end event permission/API tests, precise timezones, proactive integration validation |
| Health Connect | Opt-in read steps and on-demand seven-day total | Provider availability / API tests on Android versions, privacy approval |
| Notification access | NotificationListenerService records allowlisted app COUNTS only | Permission granting/revocation test, service stop/boot behavior, duplicates/rate limits |
| Timeline | User-exported JSON parser for two known layouts | Test with real user export variants and location data minimization |
| WhatsApp | Chat export as text via SAF; optional notification counts only | Test import consent and supported export format; private app DB is **not accessible** |
| Commute | Imported historical median + user-triggered Google Routes traffic-aware estimate with manual API key, arrival buffer | Configure Routes API billing/key and test live traffic, calendar-linked per-route recommendations |
| Career/learning | Encrypted goals, saved user-approved mail/calendar items, deadlines and completion flags | Verified extraction of commitments, learning plan and personalized preparation |
| Proactive agent | Local ranked deadline reminders, privacy opt-in, quiet hours and daily caps | Live device notification QA; feedback adaptation to user schedule beyond fixed rules |
| Adaptive ranking | Helpful/Not helpful weighting for rule-based insights | Evaluate ranking and cross-module contextual feedback, persistence tests |
| Offline voice | Android 12+ on-device recognizer; RECORD_AUDIO prompt, no cloud recognizer fallback | Device microphone and language support tests, offline ASR availability |
| Optional cloud fallback | Confirmed per-request HTTPS calls, user-held ephemeral key, no automatic transmissions | Provider compatibility, cancellation and full request/response UI tests |
| Phase 5/6 | Integrated source entry screens and feature flags | All items above validated |

## Hard prerequisites

- Google Cloud API project configured with an Android OAuth client for
  package dev.contextos.app and the correct SHA-1 of the signing certificate.
  Gmail/Drive restricted scopes can require Google's verification/security review.
  Users must explicitly authorize each scope; developer/admin approval alone is
  never sufficient to read another person's account.
- Routes API activated with billing and an appropriately restricted API key.
  Keys cannot be embedded as shared plaintext secrets in the APK.
- Real Android phones, Google Play Services, Health Connect where supported,
  on-device recognizer, and compatible .litertlm model for end-to-end QA.
- Only pushes to main may trigger GitHub Actions APK builds, per the user's
  separate instruction. Do NOT add feature-branch Actions triggers merely
  to obtain a green badge. Feature code has NOT been compiled by CI.
- Manual tests for permissions, deletion, Keystore corruption, backup, offline
  operation, consent revocation, battery saver and Android 13–16.

## Most recent source additions

- Google Routes API: live traffic-aware estimates on demand and deadline-aware
  leave-by calculator (requires user's API key).
- Select one Gmail or Drive preview and explicitly import readable contents.
- Photo OCR plus ML Kit bundled on-device image labels.
- On-device-only speech recognition when OS offers it.
- Android API-26 compatible streaming reads with strict size bounds.
- Tests: route computation and size limits.

**Decision:** Do not raise a merge-ready PR or merge into main until this
list has no blocking item. No feature-branch GitHub Actions workflow was created.
