# ContextOS 0.6.0 beta — Every requested integration / agent idea

This feature branch implements a **working Android-first integration scaffold for all ten
requested ideas**. Source features and Android UI exist, but connecting third-party
accounts and exercising every integration on a real Android device requires further setup
and testing. This is not a production-certified assistant.

| Idea | Source implementation | Actual limits / requirements |
|---|---|---|
| Gmail | Google Play Services AuthorizationClient, per-service gmail.readonly consent, Gmail preview listing, Save selected preview | Restricted scope; Google Cloud Android OAuth app registration + verification. No silent mailbox indexing. |
| Google Drive | Read-only OAuth, file metadata preview, Save selected metadata; SAF file import from Drive document provider | Cannot obtain Drive full document contents from metadata alone. User must explicitly select files via SAF. Restricted Drive scope verification may apply. |
| Google Calendar / device Calendar | Separate Google OAuth calendar.readonly, plus Android READ_CALENDAR permission and upcoming event previews | Requires user permission and compatible provider. No automatic calendar changes. |
| Health Connect | Read Steps permission, explicit user-triggered seven-day aggregate, rationale screen | May require Health Connect installed/updated. No background health access, no health RAG storage. |
| Notifications | Official BIND_NOTIFICATION_LISTENER_SERVICE access settings, opt-in package allowlist | Only daily COUNT metadata stored. No WhatsApp chat bodies, OTPs, or banking notification text. |
| Photos / files / documents | Android system Photo Picker, SAF, ML Kit local OCR, PDF text, TXT/MD/CSV/JSON/HTML/ICS | Only selected items. Scanned PDFs may have no text; OCR quality varies. 16 MB per document and archive quota. |
| Google Maps Timeline | User-exported Timeline JSON parser for legacy activitySegments / semanticSegments; encrypted history + offline trip median | No official general-purpose Timeline-history API, no guaranteed export schema and no live traffic data stored. |
| WhatsApp / other apps | Package-allowlisted notification **counts** only | Cannot access private app databases or bypass Android sandbox. |
| Offline searchable memory | AES-256-GCM local archive for up to 160 selected entries (16k chars/entry), user-initiated search and retrieval into LiteRT-LM | Lexical search for documents, optional separate semantic model for personal memory notes. Imported document RAG is opt-in and bounded. |
| Smart commute assistant | Historical trip median from imported Timeline, user-clicked Google Maps directions | Live routing happens in external Maps; no live traffic SDK integration or automated departure notifications. |
| Career / learning | Manually saved interview/project/study goals, calendar/email previews, deadline/completion tracking | Email/calendar previews must be explicitly saved before they become searchable. |
| Proactive personal agent | Deterministic priority ranking + opt-in WorkManager due-date checks, OS notification permission, quiet-hours/daily cap | No automatic external actions, background mailbox polling or personalized ML training. |
| Phase 6 adaptive ranking | Phase 3 Helpful/Not helpful feedback impacts insight ranking | Not a trained personalization model; lacks feedback-driven proactive ranking. |
| Phase 6 multimodal | Selected-photo on-device text OCR | No general vision-language camera surveillance or video perception. |
| Phase 6 voice | Android speech recognition intent -> review -> note or offline AI chat | Offline speech requested but not guaranteed by device recognizer. |
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
  Google Play Services. Tokens are held in RAM only, not persisted or logged.
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
download workflow. Unit tests: lexical temporal search, unfinished goals, no-match
privacy, deadline priority and median historical trip.

Required on-device QA: Google OAuth consent + real API responses, Health Connect
steps permission/revocation, Notifications Settings service registration, Photo
Picker/ML Kit cold start, very long PDF/Timeline errors, Maps intent, voice recognizer
offline behavior, WorkManager Doze/quiet hours and Android 16 notification permission.
No downloaded APK should be described as fully validated until device tests pass.
