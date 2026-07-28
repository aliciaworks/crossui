package dev.crossui.runtime

import dev.crossui.ir.DiffOp
import dev.crossui.ir.Platform
import dev.crossui.ir.UiDocument
import dev.crossui.ir.diff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The intentionally small KMP runtime. Native UI is generated ahead of time;
 * this module only shares state, events, bindings, navigation, and capabilities.
 */
interface Reducer<State, Action, Effect> {
    fun reduce(state: State, action: Action): Reduction<State, Effect>
}

/**
 * The platform-neutral boundary consumed by generated native UI.
 *
 * Native views observe [states] and send typed actions through [send]. The
 * connector owns state management; generated UI never interprets an IR tree.
 */
interface UiConnector<State, Action> {
    val states: StateFlow<State>
    fun send(action: Action)
}

fun interface UiActionMapper<Action> {
    fun map(action: String, value: String?): Action
}

data class Reduction<State, Effect>(
    val state: State,
    val effects: List<Effect> = emptyList(),
)

data class StoreUpdate<State, Effect>(
    val state: State,
    val effects: List<Effect>,
)

class Store<State, Action, Effect>(
    initialState: State,
    private val reducer: Reducer<State, Action, Effect>,
) : UiConnector<State, Action> {
    private val observers = mutableSetOf<(State) -> Unit>()
    private val mutableStates = MutableStateFlow(initialState)

    override val states: StateFlow<State> = mutableStates.asStateFlow()
    val state: State get() = mutableStates.value

    override fun send(action: Action) {
        dispatch(action)
    }

    fun dispatch(action: Action): StoreUpdate<State, Effect> {
        val reduction = reducer.reduce(state, action)
        mutableStates.value = reduction.state
        observers.toList().forEach { it(state) }
        return StoreUpdate(state, reduction.effects)
    }

    fun observe(observer: (State) -> Unit): Subscription {
        observers += observer
        observer(state)
        return Subscription { observers -= observer }
    }
}

fun interface Subscription {
    fun cancel()
}

data class Binding<T>(
    val get: () -> T,
    val set: (T) -> Unit,
)

data class UiEvent(
    val nodeKey: String,
    val action: String,
    val value: String? = null,
)

data class NavigationState(
    val activeRoute: String,
    val backStack: List<String> = listOf(activeRoute),
) {
    fun navigate(route: String) = copy(
        activeRoute = route,
        backStack = backStack + route,
    )

    fun back(): NavigationState =
        if (backStack.size <= 1) this
        else copy(
            activeRoute = backStack[backStack.lastIndex - 1],
            backStack = backStack.dropLast(1),
        )
}

data class Environment(
    val platform: Platform,
    val locale: String,
    val colorScheme: ColorScheme = ColorScheme.System,
    val reducedMotion: Boolean = false,
    val highContrast: Boolean = false,
)

enum class ColorScheme { System, Light, Dark }

data class DocumentUpdate<Effect>(
    val document: UiDocument,
    val patches: List<DiffOp>,
    val effects: List<Effect>,
)

class DocumentStore<State, Action, Effect>(
    initialState: State,
    reducer: Reducer<State, Action, Effect>,
    private val view: (State) -> UiDocument,
) {
    private val store = Store(initialState, reducer)
    private var document = view(initialState).also(UiDocument::validate)

    fun document(): UiDocument = document
    val state: State get() = store.state
    val states: StateFlow<State> get() = store.states

    fun dispatch(action: Action): DocumentUpdate<Effect> {
        val update = store.dispatch(action)
        val next = view(update.state).also(UiDocument::validate)
        val result = DocumentUpdate(next, diff(document, next), update.effects)
        document = next
        return result
    }
}

sealed interface Async<out Value> {
    data object Idle : Async<Nothing>
    data object Loading : Async<Nothing>
    data class Success<Value>(val value: Value) : Async<Value>
    data class Failure(val error: UiError) : Async<Nothing>
}

data class UiError(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)

fun interface AsyncEffectHandler<Action, Effect> {
    suspend fun execute(effect: Effect): Action?
}

/**
 * Runs reducer effects with structured concurrency. Closing the store cancels
 * every in-flight effect owned by the screen or feature.
 */
class AsyncStore<State, Action, Effect>(
    initialState: State,
    reducer: Reducer<State, Action, Effect>,
    private val scope: CoroutineScope,
    private val handler: AsyncEffectHandler<Action, Effect>,
) : UiConnector<State, Action> {
    private val store = Store(initialState, reducer)
    private val jobs = mutableSetOf<Job>()

    val state: State get() = store.state
    override val states: StateFlow<State> get() = store.states

    override fun send(action: Action) {
        dispatch(action)
    }

    fun dispatch(action: Action) {
        val update = store.dispatch(action)
        update.effects.forEach { effect ->
            val job = scope.launch {
                handler.execute(effect)?.let(::dispatch)
            }
            jobs += job
            job.invokeOnCompletion { jobs -= job }
        }
    }

    fun cancelEffects() {
        jobs.toList().forEach(Job::cancel)
        jobs.clear()
    }

    fun close() {
        cancelEffects()
    }
}

enum class SettingStorage {
    Preferences,
    SavedState,
    Secure,
}

data class SettingKey<Value>(
    val name: String,
    val default: Value,
    val storage: SettingStorage = SettingStorage.Preferences,
)

interface Setting<Value> {
    val value: StateFlow<Value>
    suspend fun set(value: Value)
}

interface SettingsStore {
    fun boolean(key: SettingKey<Boolean>): Setting<Boolean>
    fun string(key: SettingKey<String>): Setting<String>
    fun int(key: SettingKey<Int>): Setting<Int>
    fun double(key: SettingKey<Double>): Setting<Double>
}

class LifecycleTasks(
    private val scope: CoroutineScope,
) {
    private val tasks = mutableMapOf<String, Job>()

    fun launch(id: String, block: suspend CoroutineScope.() -> Unit) {
        tasks.remove(id)?.cancel()
        tasks[id] = scope.launch(block = block)
    }

    fun cancel(id: String) {
        tasks.remove(id)?.cancel()
    }

    fun cancelAll() {
        tasks.values.forEach(Job::cancel)
        tasks.clear()
    }
}

enum class PlatformCapability {
    Camera, Location, Biometrics, Notifications, FilePicker, Share, Clipboard,
}

class CapabilitySet private constructor(
    private val values: Set<PlatformCapability>,
) {
    fun supports(capability: PlatformCapability) = capability in values

    companion object {
        fun of(vararg capabilities: PlatformCapability) =
            CapabilitySet(capabilities.toSet())
    }
}

interface NativeModule<Request, Response> {
    val platform: Platform
    val capabilities: CapabilitySet
    fun invoke(request: Request): Response
}
