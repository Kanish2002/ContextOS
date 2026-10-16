# ContextOS Phase 4 — Personal memory (first increment)

**Version:** 0.4.0. **Scope:** encrypted user-authored memory and privacy-first,
consent-gated **local keyword retrieval** into LiteRT-LM prompts.

## Implemented
- Separate **Memory** tab: add, inspect, delete individual memories; confirm before
  deleting all. 60 notes maximum, 480 characters per note.
- AES-256-GCM encryption at rest with nonexportable Android Keystore key, fresh
  random nonce for each write, authenticated associated data and atomic file writes.
  Android backups are disabled. Vault lives in application-private storage.
- Explicit, separately persisted **Use matching memories in local chat** switch,
  off by default. Existing live-context and insight switches remain independent.
- User-triggered plain-text export using Android Storage Access Framework with
  an explicit warning that exported text is *not* encrypted and may be synced or shared.
- Deterministic word-match retrieval (English, Tamil and other Unicode letter
  tokens), returning up to three relevant items in a bounded 850-character snippet.
  Unrelated queries retrieve no notes. No conversation auto-memory or continuous
  collection.
- Turning OFF chat memory and deleting used memories resets the active AI conversation.\n  Destructive actions are disabled while model inference is busy.\n- Notes passed to the model are identified as user-authored reference data, not
  executable instructions. The visible chat bubble retains only the typed prompt.
- Unit tests cover relevance, no-match privacy, bounds, duplicates, normalization,
  and Tamil matching.

## What Phase 4 does NOT yet do
- **Embeddings and vector search are not implemented yet.** This is a pure lexical
  first slice; do not describe it as semantic search.
- **Automatic chat summarization is not implemented yet.** Saving a memory always
  requires user action.
- No long-term preference learning from chats, recurring reminders, scheduling,
  microphone/calendar/health integration or background monitoring.
- No cloud sync or Internet permission.
- Sensitive memories are encrypted *at rest*, but decrypted entries necessarily
  exist in app memory while visible/retrieved. There is no biometric lock yet.

## Recovery and privacy
- If Android Keystore keys are lost or the vault is corrupted, the app shows an
  unlock error rather than silently overwriting stored data.
- The explicit **Delete all** action can reset an unreadable vault.
- App uninstall removes the encrypted vault and its Keystore key; plaintext exports
  created outside app-private storage are not deleted automatically.
- The former Phase 2 SQLite snapshots remain a separate, app-private **unencrypted**
  data store. This new vault does not migrate or encrypt those snapshots.
- App backups are disabled; do not assume an APK upgrade with a different signing
  certificate will preserve existing app data.

## Testing (CI + real Android phone)
- `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`
- Save 3 notes; kill/restart app; verify notes survive and are displayed.
- Toggle "Use matching memories in local chat" OFF; ask an exact-match question
  and verify it is not supplied to the prompt.
- Toggle ON; ask a matching and then an unrelated question, verify retrieval.
- Check 5-tab layout on Samsung with large fonts, light/dark theme, Gboard,
  gesture and 3-button navigation; chat input must remain above keyboard.
- Confirm delete-one, delete-all, confirm plaintext export, invalid note lengths,
  60-note capacity, and empty-state behavior.
- Revoke permissions; the Memory vault must still operate (does not need
  sensor, location, usage or Internet).
- Verify ciphertext not plaintext on disk and test app reinstall/data-loss behavior.

## Next Phase 4 increments
1. Small on-device embedding model, encrypted on-device vector index and semantic
   similarity fallback, with measurable inference/storage/battery costs.
2. Explicit approve/edit-before-save memory suggestions from user-initiated chat
   summaries; no invisible automatic persistence.
3. Search/filter UI, per-memory retention/expiry controls and optional biometric
   gate with recovery design.
4. Device benchmarks, Android instrumentation tests of key creation, tampering,
   atomic-write crash behavior, and privacy regression tests.
