package dev.crossui.ir

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs

class ModelTest {
    @Test
    fun duplicateKeysAreRejected() {
        val document = UiDocument(
            root = Node(
                NodeKey("root"),
                NodeKind.Stack(Axis.Vertical),
                children = listOf(
                    Node(NodeKey("same"), NodeKind.Text("a")),
                    Node(NodeKey("same"), NodeKind.Text("b")),
                ),
            ),
        )
        assertFails { document.validate() }
    }

    @Test
    fun childChangesProduceLeafUpdates() {
        val before = UiDocument(
            root = Node(
                NodeKey("root"),
                NodeKind.Stack(Axis.Vertical),
                children = listOf(Node(NodeKey("value"), NodeKind.Text("a"))),
            ),
        )
        val after = before.copy(
            root = before.root.copy(
                children = listOf(Node(NodeKey("value"), NodeKind.Text("b"))),
            ),
        )
        val operation = diff(before, after).single()
        assertIs<DiffOp.Update>(operation)
        assertEquals(NodeKey("value"), operation.key)
    }

    @Test
    fun jsonRoundTripPreservesDocument() {
        val document = UiDocument(
            root = Node(NodeKey("root"), NodeKind.Text("hello")),
        )
        assertEquals(document, UiDocument.fromJson(document.toJson()))
    }
}
