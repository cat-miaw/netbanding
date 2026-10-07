package com.netbanding.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.netbanding.app.R
import com.netbanding.app.data.prefs.REGIONS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    region: String,
    onRegion: (String) -> Unit,
    dataVersion: Int,
    lastUpdated: String?,
    onBack: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = REGIONS.firstOrNull { it.first == region }?.second ?: region,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.region_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    REGIONS.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { onRegion(code); expanded = false },
                        )
                    }
                }
            }
            Text(stringResource(R.string.about_text), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.data_version, dataVersion, lastUpdated?.take(10) ?: "-"),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.privacy_title))
            }
            Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}
