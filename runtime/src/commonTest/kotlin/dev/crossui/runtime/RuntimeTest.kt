package dev.crossui.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class RuntimeTest {
    @Test
    fun storeNotifiesAfterReduction() {
        val reducer = object : Reducer<Int, Int, String> {
            override fun reduce(state: Int, action: Int) =
                Reduction(state + action, listOf("changed"))
        }
        val store = Store(1, reducer)
        var observed = 0
        val subscription = store.observe { observed = it }
        val update = store.dispatch(2)
        assertEquals(3, update.state)
        assertEquals(3, observed)
        subscription.cancel()
    }

    @Test
    fun connectorSendsTypedActions() {
        val store = Store(
            initialState = 1,
            reducer = object : Reducer<Int, Int, Nothing> {
                override fun reduce(state: Int, action: Int): Reduction<Int, Nothing> =
                    Reduction(state + action)
            },
        )

        val connector: UiConnector<Int, Int> = store
        connector.send(2)

        assertEquals(3, connector.states.value)
    }

    @Test
    fun navigationBackStackIsSharedLogic() {
        val state = NavigationState("login").navigate("projects").navigate("detail").back()
        assertEquals("projects", state.activeRoute)
    }

    private sealed interface AsyncAction {
        data object Start : AsyncAction
        data class Complete(val value: String) : AsyncAction
    }

    @Test
    fun asyncEffectsReturnActionsIntoTheReducer() = runTest {
        val reducer = object : Reducer<Async<String>, AsyncAction, String> {
            override fun reduce(
                state: Async<String>,
                action: AsyncAction,
            ): Reduction<Async<String>, String> = when (action) {
                AsyncAction.Start -> Reduction(Async.Loading, listOf("load"))
                is AsyncAction.Complete -> Reduction(Async.Success(action.value))
            }
        }
        val store = AsyncStore(
            initialState = Async.Idle,
            reducer = reducer,
            scope = this,
            handler = AsyncEffectHandler { AsyncAction.Complete("ready") },
        )

        store.dispatch(AsyncAction.Start)
        advanceUntilIdle()

        val state = assertIs<Async.Success<String>>(store.state)
        assertEquals("ready", state.value)
        store.close()
    }
}
