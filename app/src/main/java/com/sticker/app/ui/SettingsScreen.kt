package com.sticker.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    api: String,
    preview: String,
    fin: String,
    onSave: (String, String, String) -> Unit
) {
    var apiT by remember { mutableStateOf(TextFieldValue(api)) }
    var pv by remember { mutableStateOf(TextFieldValue(preview)) }
    var fn by remember { mutableStateOf(TextFieldValue(fin)) }

    Column(modifier = modifier.padding(16.dp)) {
        Text("Replicate API token", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(apiT, { apiT = it }, placeholder = { Text("r8_...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text("Preview model VERSION (img2img @1024)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(pv, { pv = it }, placeholder = { Text("copy from model API page") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text("Final model VERSION (tiled img2img @4096)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(fn, { fn = it }, placeholder = { Text("copy from model API page") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onSave(apiT.text, pv.text, fn.text) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "- Get API token at replicate.com → Account → API Tokens\n" +
            "- Open a model page → API → copy the “version” hash\n" +
            "- You can change these later here",
            style = MaterialTheme.typography.bodySmall
        )
    }
}
