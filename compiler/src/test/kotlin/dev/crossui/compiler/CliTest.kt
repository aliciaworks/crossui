package dev.crossui.compiler

import dev.crossui.ir.Node
import dev.crossui.ir.NodeKey
import dev.crossui.ir.NodeKind
import dev.crossui.ir.UiDocument
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains

class CliTest {
    @Test
    fun doctorExplainDiffAndVerifyAreAgentReadable() {
        val directory = createTempDirectory("crossui-cli-test")
        val input = directory.resolve("ui.json")
        val before = UiDocument(
            root = Node(NodeKey("message"), NodeKind.Text("Before")),
        )
        Files.writeString(input, before.toJson())

        val doctor = captureStdout {
            main(
                arrayOf(
                    "doctor",
                    "--input",
                    input.toString(),
                    "--targets",
                    "compose",
                ),
            )
        }
        assertContains(doctor, "Issues: 0")

        val explain = captureStdout {
            main(
                arrayOf(
                    "explain",
                    "--input",
                    input.toString(),
                    "--node",
                    "message",
                    "--target",
                    "compose",
                ),
            )
        }
        assertContains(explain, "Generated:")

        val afterPath = directory.resolve("after.json")
        Files.writeString(
            afterPath,
            before.copy(root = Node(NodeKey("message"), NodeKind.Text("After"))).toJson(),
        )
        val diff = captureStdout {
            main(
                arrayOf(
                    "diff",
                    "--before",
                    input.toString(),
                    "--after",
                    afterPath.toString(),
                ),
            )
        }
        assertContains(diff, "update: message")

        val output = directory.resolve("generated")
        main(
            arrayOf(
                "generate",
                "--input",
                input.toString(),
                "--output",
                output.toString(),
                "--targets",
                "compose",
                "--name",
                "MessageView",
            ),
        )
        val verify = captureStdout {
            main(
                arrayOf(
                    "verify",
                    "--input",
                    input.toString(),
                    "--output",
                    output.toString(),
                    "--targets",
                    "compose",
                    "--name",
                    "MessageView",
                ),
            )
        }
        assertContains(verify, "current")
    }
}

private fun captureStdout(block: () -> Unit): String {
    val previous = System.out
    val bytes = ByteArrayOutputStream()
    try {
        System.setOut(PrintStream(bytes))
        block()
    } finally {
        System.setOut(previous)
    }
    return bytes.toString(Charsets.UTF_8)
}
