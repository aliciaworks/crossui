package dev.crossui.integration.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.crossui.generated.LoginScreen
import dev.crossui.generated.LoginScreenTheme
import dev.crossui.integration.login.LoginActions
import dev.crossui.integration.login.createLoginConnector
import dev.crossui.runtime.AndroidContentPicker
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private lateinit var contentPicker: AndroidContentPicker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        contentPicker = AndroidContentPicker(this)
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

            LoginScreenTheme {
                LoginScreen(connector, LoginActions)
            }
        }
    }
}
