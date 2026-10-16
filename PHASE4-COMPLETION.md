# Phase 4 completion pass — semantic RAG, summaries and long-term preferences

## Delivered
- Keystore AES-GCM atomic vault continues to store user-authored notes encrypted,
  and can read legacy notes with default kind NOTE.
- Separate Memory categories: NOTE, PREFERENCE, GOAL, FACT; add, edit and delete.
- Semantically ranked retrieval via actual **MediaPipe Text Embedder** model,
  using cosine similarity of on-device query/document vectors and a lexical tie bonus.
  Embedding vectors are held only in RAM and cleared on note edits/deletions.
- The user must independently download/import a compatible trained **.tflite**
  Text Embedder model via system file picker. The APK does not bundle one.
- If no compatible model is available or an embedding query fails, local
  bounded keyword retrieval remains the fallback. Thresholds are conservative,
  may still produce false-positive/negative semantic matches, and need device eval.
- Retrieval now runs asynchronously off the Android UI thread, with a final
  consent check before any memory is attached to a user-initiated model prompt.
- Optional **Suggest memory from recent chat** uses an independent LiteRT-LM
  conversation to propose one note from up to eight visible recent messages.
  The user edits/reviews and explicitly chooses Save or Discard. No auto-save.
- Editing/deleting saved memories and disabling chat memory clears the active
  assistant chat, preventing past retrieved notes from lingering in session.
- 60-note and prompt-size limits remain in effect.

## Privacy / limitations
- No Internet permission or cloud endpoints. Embedding model import is through
  Android's explicit file picker and is validated locally before installing.
- Embedding model files are app-private, but the model itself is not encrypted;
  the sensitive memory text and note categories are encrypted at rest.
  Vector embeddings are ephemeral in process memory only.
- Local summaries are generative and may invent details. Users must review
  before saving; no silent long-term personalization.
- The original unencrypted Phase 2 SQLite context snapshots are **not**
  migrated/encrypted in this completion pass.
- App debug APK is built without Qwen3/Gemma weights or an embedding model.
  Separate compatible local model imports are required.

## Device acceptance tests
1. Import compatible metadata-bearing .tflite Text Embedder; verify preview status.
2. Store a note; query with paraphrases without identical words, compare against
   keyword-only retrieval; check relevance and latency (English and Tamil may differ).
3. Toggle memory use OFF and ensure no stored note appears in AI prompt.
4. Request a memory suggestion after chatting: review/edit/cancel/save flows.
5. Restart app, edit a goal and category, verify encrypted persistence.
6. Delete a recalled memory and check assistant session reset.
7. Test corrupt model, corrupt vault, export warning, full capacity,
   orientation, keyboard and large text settings.
8. Run `gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`.

## Still not production-complete
On-device accuracy benchmarking, Android instrumentation tests of Keystore
recovery/corruption, accessibility audits, and a clear release signing/update
migration plan remain open. No claim of production certification is made.
