package com.kipu.app.feature.accounts.presentation

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.kipu.app.ui.theme.KipuTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Historical before/after excerpt harness; production copy is asserted by screen/component tests. */
class Issue48CopyScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun captureBeforeAndAfterCopy() {
        val before = mutableStateOf(true)
        compose.setContent {
            KipuTheme(darkTheme = false) {
                CopyComparisonScreen(before = before.value)
            }
        }
        capture("issue48-before.png")
        compose.runOnIdle { before.value = false }
        capture("issue48-after.png")
    }

    private fun capture(filename: String) {
        compose.waitForIdle()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "issue-48-copy").apply { mkdirs() }
        File(directory, filename).outputStream().use { output ->
            compose.onRoot().captureToImage().asAndroidBitmap()
                .compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}

@Composable
private fun CopyComparisonScreen(before: Boolean) {
    val scrollState = rememberScrollState()
    val content = if (before) CopyVersion.Before else CopyVersion.After

    Scaffold { insets ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(if (before) "ANTES · Etiquetas actuales" else "DESPUÉS · Copy unificado",
                    style = MaterialTheme.typography.headlineSmall)
                Text("Comparativa de los mismos importes y estados", style = MaterialTheme.typography.bodyMedium)

                CopyCard("Dashboard · dinero propio") {
                    Text(content.dashboardTitle, style = MaterialTheme.typography.labelLarge)
                    Text("S/ 8,420.50", style = MaterialTheme.typography.headlineMedium)
                    Text(content.dashboardHint, style = MaterialTheme.typography.bodySmall)
                }

                CopyCard("Dashboard · tarjetas y cupo") {
                    Text(content.creditHint, style = MaterialTheme.typography.bodyMedium)
                    Text(content.quota, style = MaterialTheme.typography.bodyMedium)
                    Text("3 de 4 · queda 1 espacio", style = MaterialTheme.typography.bodySmall)
                }

                CopyCard("Alta de cuenta o tarjeta · cupo") {
                    Text(content.accountQuota, style = MaterialTheme.typography.bodyMedium)
                    Text(content.cardQuota, style = MaterialTheme.typography.bodyMedium)
                }

                CopyCard("Tarjeta de crédito · mismos valores") {
                    Text("Deuda actual · S/ 1,420.00", style = MaterialTheme.typography.bodyMedium)
                    Text(content.availableCredit, style = MaterialTheme.typography.labelLarge)
                    Text("S/ 2,580.00", style = MaterialTheme.typography.titleLarge)
                    Text(content.creditLimit, style = MaterialTheme.typography.bodySmall)
                    Text("S/ 4,000.00", style = MaterialTheme.typography.bodyMedium)
                }

                CopyCard("Selector de movimiento · misma tarjeta") {
                    Text("Visa · •••• 4821", style = MaterialTheme.typography.bodyMedium)
                    Text(content.movementCredit, style = MaterialTheme.typography.labelMedium)
                    Text("S/ 2,580.00     Usado: S/ 1,420.00", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun CopyCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

private data class CopyVersion(
    val dashboardTitle: String,
    val dashboardHint: String,
    val creditHint: String,
    val quota: String,
    val availableCredit: String,
    val creditLimit: String,
    val movementCredit: String,
    val accountQuota: String,
    val cardQuota: String,
) {
    companion object {
        val Before = CopyVersion(
            dashboardTitle = "TOTAL DISPONIBLE",
            dashboardHint = "Activos líquidos reales · Excluye líneas de crédito",
            creditHint = "Líneas de crédito (no suman al saldo disponible)",
            quota = "Cupo Free · 3 de 4 cuentas y tarjetas",
            availableCredit = "Disponible en línea",
            creditLimit = "Línea total",
            movementCredit = "Disp:",
            accountQuota = "Esta cuenta consumirá 1 cupo de los 4 permitidos en el plan Free.",
            cardQuota = "Esta tarjeta consumirá 1 cupo de los 4 permitidos en el plan Free.",
        )
        val After = CopyVersion(
            dashboardTitle = "DINERO REAL DISPONIBLE",
            dashboardHint = "Dinero propio en cuentas y efectivo · No incluye crédito",
            creditHint = "Crédito disponible, no es dinero propio",
            quota = "Cupo Free de instrumentos · 3 de 4",
            availableCredit = "Crédito disponible, no es dinero propio",
            creditLimit = "Línea total de crédito",
            movementCredit = "Crédito disponible, no es dinero propio:",
            accountQuota = "Esta cuenta ocupa 1 de los 4 cupos para instrumentos de Kipu Free.",
            cardQuota = "Esta tarjeta ocupa 1 de los 4 cupos para instrumentos de Kipu Free.",
        )
    }
}
