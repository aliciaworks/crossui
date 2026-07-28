package dev.crossui.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun navigationBackStackIsSharedLogic() {
        val state = NavigationState("login").navigate("projects").navigate("detail").back()
        assertEquals("projects", state.activeRoute)
    }
}
