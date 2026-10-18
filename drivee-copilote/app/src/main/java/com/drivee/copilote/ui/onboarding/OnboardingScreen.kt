package com.drivee.copilote.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Contenu réutilisable de la Prominent Disclosure (texte + listes).
 * Utilisé par l'écran d'accueil et par la boîte de dialogue du tableau de bord.
 */
@Composable
fun DisclosureBody(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            DisclosureText.INTRO,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(16.dp))

        SectionTitle("Ce que nous faisons")
        DisclosureText.WE_DO.forEach { BulletLine(it, positive = true) }

        Spacer(Modifier.height(12.dp))
        SectionTitle("Ce que nous ne faisons pas")
        DisclosureText.WE_DONT.forEach { BulletLine(it, positive = false) }

        Spacer(Modifier.height(16.dp))
        Text(
            DisclosureText.FOOTER,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun BulletLine(text: String, positive: Boolean) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            if (positive) "✓  " else "✕  ",
            color = if (positive) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Écran d'accueil (première ouverture) : affiche la divulgation puis propose
 * d'activer l'accessibilité. Conforme à l'exigence Google (divulgation AVANT redirection).
 */
@Composable
fun OnboardingScreen(
    onAcceptAndActivate: () -> Unit,
    onRefuse: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            "Bienvenue sur Drivee Copilote",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Votre copilote de rentabilité VTC.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    DisclosureText.TITLE,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(12.dp))
                DisclosureBody()
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAcceptAndActivate
        ) { Text("J'accepte et j'active") }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onRefuse
        ) { Text("Plus tard") }

        Spacer(Modifier.height(24.dp))
    }
}
