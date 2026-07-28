package dev.crossui.integration.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.crossui.generated.LoginScreen
import dev.crossui.integration.login.LoginActions
import dev.crossui.integration.login.createLoginConnector
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            val connector = remember {
                createLoginConnector(scope) {
                    delay(10)
                }
            }
            DisposableEffect(connector) {
                onDispose(connector::close)
            }

            MaterialTheme {
                LoginScreen(connector, LoginActions)
            }
        }
    }
}
