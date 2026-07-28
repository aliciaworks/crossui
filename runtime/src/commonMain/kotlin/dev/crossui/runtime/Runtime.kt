package dev.crossui.runtime

import dev.crossui.ir.DiffOp
import dev.crossui.ir.Platform
import dev.crossui.ir.UiDocument
import dev.crossui.ir.diff

/**
 * The intentionally small KMP runtime. Native UI is generated ahead of time;
 * this module only shares state, events, bindings, navigation, and capabilities.
 */
interface Reducer<State, Action, Effect> {
    fun reduce(state: State, action: Action): Reduction<State, Effect>
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
) {
    private val observers = mutableSetOf<(State) -> Unit>()

    var state: State = initialState
        private set

    fun dispatch(action: Action): StoreUpdate<State, Effect> {
        val reduction = reducer.reduce(state, action)
        state = reduction.state
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

    fun dispatch(action: Action): DocumentUpdate<Effect> {
        val update = store.dispatch(action)
        val next = view(update.state).also(UiDocument::validate)
        val result = DocumentUpdate(next, diff(document, next), update.effects)
        document = next
        return result
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
