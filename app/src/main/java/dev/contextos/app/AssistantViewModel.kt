package dev.contextos.app

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.contextos.app.data.ModelStore
import dev.contextos.app.inference.LocalInferenceEngine
import dev.contextos.app.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

class AssistantViewModel(application: Application): AndroidViewModel(application) {
    private val modelStore = ModelStore(application)
    private val runtime = LocalInferenceEngine()
    private val ids = AtomicLong()
    private val ramBytes = ActivityManager.MemoryInfo().also {
        (application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
    }.totalMem
    private val _state = MutableStateFlow(AssistantState(deviceRamBytes = ramBytes))
    val state: StateFlow<AssistantState> = _state.asStateFlow()

    init {
        val chosen = modelStore.selected()
        if (chosen != null) {
            _state.value = _state.value.copy(modelName = chosen.name, modelSize = chosen.length(),
                status = "Local model ready for loading.")
            if (modelStore.isBundledSelected()) loadModel()
        } else if (modelStore.bundledAssetName() != null) {
            // Extraction may take several seconds: never copy a 300MB asset on the UI thread.
            viewModelScope.launch {
                _state.value = _state.value.copy(busy = true, importPercent = 0,
                    status = "Preparing included AI model for first use...")
                try {
                    val model = modelStore.prepareBundledModel { p ->
                        _state.value = _state.value.copy(importPercent = p)
                    }
                    _state.value = _state.value.copy(modelName = model.name,
                        modelSize = model.length(), status = "Bundled model copied. Loading locally...")
                } catch (t: Throwable) {
                    _state.value = _state.value.copy(error = t.message ?: "Bundled model setup failed",
                        status = "Could not prepare included model.")
                } finally {
                    _state.value = _state.value.copy(busy = false, importPercent = null)
                }
                if (modelStore.selected() != null) loadModel()
            }
        }
    }

    fun importModel(uri: Uri) {
        if (_state.value.busy || _state.value.generating) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, initialized = false,
                importPercent = null, error = null, status = "Importing model into private storage...")
            try {
                runtime.close()
                val file = modelStore.importFromUri(uri) { percent ->
                    _state.value = _state.value.copy(importPercent = percent)
                }
                _state.value = _state.value.copy(
                    modelName = file.name, modelSize = file.length(), initialized = false,
                    messages = emptyList(), benchmark = null,
                    status = "Model imported. Tap Load model to initialize.")
            } catch (t: Exception) {
                _state.value = _state.value.copy(error = t.message ?: "Import failed", status = "Model import failed")
            } finally {
                _state.value = _state.value.copy(busy = false, importPercent = null)
            }
        }
    }

    fun chooseBackend(backend: DeviceBackend) {
        if (_state.value.busy || _state.value.generating || _state.value.backend == backend) return
        runtime.close()
        _state.value = _state.value.copy(backend = backend, initialized = false,
            status = "Backend changed. Load model again.", benchmark = null,
            messages = emptyList(), error = null)
    }

    fun loadModel() {
        if (_state.value.busy || _state.value.generating) return
        val model = modelStore.selected() ?: run {
            _state.value = _state.value.copy(error = "First import a .litertlm file.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, initialized = false,
                error = null, status = "Loading ${_state.value.backend.name} model...")
            val started = SystemClock.elapsedRealtime()
            try {
                runtime.open(model.absolutePath, _state.value.backend,
                    getApplication<Application>().cacheDir.absolutePath)
                val elapsed = SystemClock.elapsedRealtime() - started
                _state.value = _state.value.copy(initialized = true,
                    status = "Model ready · Fully on device", messages = emptyList(),
                    benchmark = Benchmark(loadMillis = elapsed))
            } catch (t: Throwable) {
                _state.value = _state.value.copy(initialized = false,
                    error = t.message ?: t.javaClass.simpleName,
                    status = "Couldn't load model. Try CPU or another .litertlm file.")
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isEmpty() || !_state.value.initialized || _state.value.generating || _state.value.busy) return
        val user = ChatMessage(ids.incrementAndGet(), Role.USER, prompt)
        val assistantId = ids.incrementAndGet()
        val assistant = ChatMessage(assistantId, Role.ASSISTANT, "", generating = true)
        _state.value = _state.value.copy(
            generating = true, error = null,
            messages = _state.value.messages + user + assistant,
            status = "Generating locally...")
        viewModelScope.launch {
            val started = SystemClock.elapsedRealtime()
            var firstChunk: Long? = null
            val accumulator = StreamAccumulator()
            try {
                runtime.generate(prompt)
                    .flowOn(Dispatchers.Default)
                    .collect { fragment ->
                        if (fragment.isNotEmpty() && firstChunk == null) {
                            firstChunk = SystemClock.elapsedRealtime() - started
                        }
                        val response = accumulator.append(fragment)
                        _state.value = _state.value.copy(messages =
                            _state.value.messages.map { message ->
                                if (message.id == assistantId) message.copy(content = response) else message
                            })
                    }
                val elapsed = SystemClock.elapsedRealtime() - started
                _state.value = _state.value.copy(
                    benchmark = Benchmark(_state.value.benchmark?.loadMillis,
                        firstChunk, elapsed, accumulator.value().length),
                    status = "Response completed offline")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (t: Throwable) {
                val errorText = "Generation error: ${t.message ?: t.javaClass.simpleName}"
                _state.value = _state.value.copy(error = errorText, status = "Generation failed",
                    messages = _state.value.messages.map { message ->
                        if (message.id == assistantId && message.content.isEmpty())
                            message.copy(content = "Could not generate a response.") else message
                    })
            } finally {
                _state.value = _state.value.copy(generating = false,
                    messages = _state.value.messages.map { if (it.id == assistantId) it.copy(generating = false) else it })
            }
        }
    }

    fun newChat() {
        if (_state.value.generating || _state.value.busy) return
        if (!_state.value.initialized) {
            _state.value = _state.value.copy(messages = emptyList())
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, status = "Creating new private chat...")
            try {
                runtime.freshConversation()
                _state.value = _state.value.copy(messages = emptyList(),
                    status = "New chat ready", error = null)
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "Could not reset chat")
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun clearError() { _state.value = _state.value.copy(error = null) }
    override fun onCleared() {
        runtime.close()
        super.onCleared()
    }
}
