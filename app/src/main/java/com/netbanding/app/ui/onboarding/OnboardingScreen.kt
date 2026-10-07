package com.netbanding.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.netbanding.app.R
import com.netbanding.app.data.prefs.REGIONS

@Composable
fun OnboardingScreen(
    selected: String,
    onSelect: (String) -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.onboarding_sub), style = MaterialTheme.typography.bodyMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(REGIONS, key = { it.first }) { (code, label) ->
                Card(
                    onClick = { onSelect(code) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        (if (code == selected) "✓ " else "") + label,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_done))
        }
        OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_skip))
        }
    }
}
