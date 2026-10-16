# ContextOS 0.6.0 beta — Every requested integration / agent idea

This feature branch implements a **working Android-first integration scaffold for all ten
requested ideas**. Source features and Android UI exist, but connecting third-party
accounts and exercising every integration on a real Android device requires further setup
and testing. This is not a production-certified assistant.

| Idea | Source implementation | Actual limits / requirements |
|---|---|---|
| Gmail | Google Play Services AuthorizationClient, per-service gmail.readonly consent, Gmail preview listing, Save selected preview | Restricted scope; Google Cloud Android OAuth app registration + verification. No silent mailbox indexing. |
| Google Drive | Read-only OAuth, file metadata preview, Save selected metadata; SAF file import from Drive document provider | Users can explicitly import selected text/Google Docs/CSV contents into encrypted search. Other formats require SAF; app still needs Google OAuth configuration. Restricted Drive scope verification may apply. |
| Google Calendar / device Calendar | Separate Google OAuth calendar.readonly, plus Android READ_CALENDAR permission and upcoming event previews | Requires user permission and compatible provider. No automatic calendar changes. |
| Health Connect | Read Steps permission, explicit user-triggered seven-day aggregate, rationale screen | May require Health Connect installed/updated. No background health access, no health RAG storage. |
| Notifications | Official BIND_NOTIFICATION_LISTENER_SERVICE access settings, opt-in package allowlist | Only daily COUNT metadata stored. No WhatsApp chat bodies, OTPs, or banking notification text. |
| Photos / files / documents | Android system Photo Picker, SAF, ML Kit local OCR, PDF text, TXT/MD/CSV/JSON/HTML/ICS | Only selected items. Scanned PDFs without a text layer use capped 5-page on-device OCR, subject to memory limits. 16 MB per document and archive quota. |
| Google Maps Timeline | User-exported Timeline JSON parser for legacy activitySegments / semanticSegments; encrypted history + offline trip median | No official general-purpose Timeline-history API, no guaranteed export schema and no live traffic data stored. |
| WhatsApp / other apps | User-exported WhatsApp .txt chat parser with timestamp-grouped local search; plus allowlisted notification **counts** only | Cannot access private app databases or bypass Android sandbox. |
| Offline searchable memory | AES-256-GCM local archive for up to 160 selected entries (16k chars/entry), user-initiated search and retrieval into LiteRT-LM | Optional local semantic retrieval for imported documents and private memory uses a separately user-imported .tflite Text Embedder; lexical/date fallback and consent-gated chat excerpt bounds. |
| Smart commute assistant | Historical trip median, user-invoked traffic-aware Google Routes estimate, calendar event destinations, leave-by calculation and traffic delay warnings | Live Routes API needs user-provided key, project and billing; traffic updates are user-initiated, not continuously monitored. |
| Career / learning | Manually saved interview/project/study goals, calendar/email previews, deadline/completion tracking | Email/calendar previews must be explicitly saved before they become searchable. |
| Proactive personal agent | Deterministic priority ranking + opt-in WorkManager due-date checks, OS notification permission, quiet-hours/daily cap | No automatic external actions, background mailbox polling or personalized ML training. |
| Phase 6 adaptive ranking | Feedback ranks/suppresses both Phase 3 insights and saved-deadline agent suggestions | Transparent explicit-feedback rule adjustment, not a trained model; no automated social/health inference. |
| Phase 6 multimodal | Selected-photo on-device text OCR and generic image labeling | No general vision-language camera surveillance or video perception. |
| Phase 6 voice | Android 12+ on-device SpeechRecognizer (no silent cloud fallback) and on-device-only TTS read-aloud, both user-triggered | Requires installed on-device recognition and offline TTS voices; feature is unavailable when device lacks support. |
| Phase 6 cloud fallback | Explicit HTTPS OpenAI-compatible single-question remote completion with confirmation and ephemeral key | A user-provided compatible endpoint/key required. Prompt leaves device, never automatic. |

## Explicit platform permissions
- **INTERNET** exists for *only user-triggered OAuth API previews and cloud completions*.
  The offline LiteRT-LM engine and knowledge archive never use a network endpoint.
- **READ_CALENDAR** requested only on the Calendar action.
- **READ_STEPS** managed by Health Connect, not a broad Android permission bypass.
- **BIND_NOTIFICATION_LISTENER_SERVICE** only after user enables special access in OS Settings.
- **POST_NOTIFICATIONS** requested for opt-in deadline alerts.
- Photo Picker/SAF require no broad storage permission.
- Google consent is granted individually by Gmail, Drive, or Calendar scope via
  Google Play Services. Tokens are held in RAM only, not persisted or logged. Explicit Google revocation is available when account identity is returned; source-tagged saved imports can be erased.
- User-imported memories and document text stored in separate app-private encrypted
  vaults. Google previews and health readings are not stored until user explicitly
  saves the selected preview (health steps never auto-saved).

## External prerequisites beyond source code
1. Configure an **Android OAuth 2.0 client** in Google Cloud for the app's package
   `dev.contextos.app` and your chosen **release signing certificate SHA-1**.
   Set up the OAuth consent screen, enable Gmail, Drive and Google Calendar APIs,
   add authorized test users during development, and submit restricted scope
   verification / required assessments before wide release.
2. End users must grant each applicable runtime/special permission themselves.
   An Android app can request permissions; it **cannot silently grant them**.
3. Offline chat needs a compatible separately imported `.litertlm` file.
   Optional semantic memory needs a separately imported compatible `.tflite` model.
4. Cloud fallback requires a user-provided HTTPS chat-completions endpoint and key,
   plus explicit per-request confirmation.
5. Existing Google Maps history must be exported by the user, with any location
   import bounded and historical. Traffic requires live routing services and may
   have separate terms, billing and API keys.

## CI and privacy validation
GitHub Actions workflow remains **push main only**, producing an APK without
bundled Qwen or other user language model weights. Do not add an automated model
download workflow. Unit test *sources* cover lexical/temporal retrieval, career topics, chat export parsing, commute delays, stream bounds and feedback ranking; integration branch is not built by the main-only CI.

Required on-device QA: Google OAuth consent + real API responses, Health Connect
steps permission/revocation, Notifications Settings service registration, Photo
Picker/ML Kit cold start, very long PDF/Timeline errors, Maps intent, voice recognizer
offline behavior, WorkManager Doze/quiet hours and Android 16 notification permission.
No downloaded APK should be described as fully validated until device tests pass.

## Feature branch and PR gating

Development continues on `feature/phase5-6-all-integrations`. Per user instruction,
GitHub Actions only builds model-free APKs after pushes to `main`. This branch
has **not** run Android CI and should not be merged merely because sources exist.
See [PR_READINESS.md](PR_READINESS.md) for incomplete acceptance criteria,
Google Cloud OAuth/Routes prerequisites, device testing and privacy review.

The APK must exclude Qwen/LiteRT-LM and user-supplied embedding model weights.
Third-party ML Kit OCR/image-labeling libraries may include small SDK recognition
assets required for selected-photo and PDF OCR; these are not the language model.
