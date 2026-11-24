package app.rem.ui.lock

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.rem.ui.components.PrimaryButton
import app.rem.ui.components.noirSurface
import app.rem.ui.onboarding.editorial
import app.rem.ui.theme.Rem

/** Asks for fingerprint/face or the phone's screen lock. */
fun authenticate(activity: FragmentActivity, onSuccess: () -> Unit) {
    val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
    })
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock Rem")
        .setSubtitle("Your reminders stay private")
        .setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        .build()
    prompt.authenticate(info)
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    val c = Rem.colors
    LaunchedEffect(Unit) { onUnlock() }
    Box(Modifier.fillMaxSize().background(c.canvas)) {
        Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(84.dp).noirSurface(androidx.compose.foundation.shape.CircleShape, elevated = true), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = c.gold, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(28.dp))
            Text(editorial("Rem is *locked.*"), style = MaterialTheme.typography.displaySmall, color = c.label)
        }
        PrimaryButton("Unlock", onClick = onUnlock, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp))
    }
}

/** Re-lock only after Rem has been in the background for a while (not for pickers and dialogs). */
fun shouldRelock(backgroundedAtMillis: Long?, nowMillis: Long, graceMillis: Long = 60_000): Boolean =
    backgroundedAtMillis != null && nowMillis - backgroundedAtMillis > graceMillis
