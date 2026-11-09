package dev.proxboss.proxguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.proxboss.proxguard.data.model.ThemeMode
import dev.proxboss.proxguard.notify.NotificationHelper
import dev.proxboss.proxguard.ui.MainViewModel
import dev.proxboss.proxguard.ui.components.AppLogo
import dev.proxboss.proxguard.ui.nav.ProxGuardRoot
import dev.proxboss.proxguard.ui.screens.LoginScreen
import dev.proxboss.proxguard.ui.theme.ProxGuardTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(NotificationHelper.EXTRA_NAVIGATE)
        setContent {
            val config by viewModel.config.collectAsStateWithLifecycle()
            val route by pendingRoute.collectAsStateWithLifecycle()
            val current = config
            if (current == null) {
                ProxGuardTheme(ThemeMode.DARK) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            AppLogo(72.dp)
                            Text(
                                text = "ProxGuard",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            } else {
                ProxGuardTheme(current.themeMode) {
                    if (current.isConfigured) {
                        ProxGuardRoot(
                            viewModel = viewModel,
                            config = current,
                            pendingRoute = route,
                            onPendingRouteHandled = { pendingRoute.value = null },
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding(),
                        ) {
                            LoginScreen(
                                viewModel = viewModel,
                                isEditing = false,
                                onSaved = {},
                                onBack = null,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = intent.getStringExtra(NotificationHelper.EXTRA_NAVIGATE)
    }
}
