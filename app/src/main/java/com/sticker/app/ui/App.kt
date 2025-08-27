package com.sticker.app.ui

import android.content.Context
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.dataStore by preferencesDataStore(name = "settings")
private val KEY_API = stringPreferencesKey("replicate_api_token")
private val KEY_PREVIEW = stringPreferencesKey("model_preview_version")
private val KEY_FINAL = stringPreferencesKey("model_final_version")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ds = ctx.dataStore

    val defaultApi = BuildConfig.REPLICATE_API_TOKEN
    val defaultPreview = BuildConfig.MODEL_PREVIEW_VERSION
    val defaultFinal = BuildConfig.MODEL_FINAL_VERSION

    val api by ds.data.map { it[KEY_API] ?: defaultApi }.collectAsState(initial = defaultApi)
    val preview by ds.data.map { it[KEY_PREVIEW] ?: defaultPreview }.collectAsState(initial = defaultPreview)
    val fin by ds.data.map { it[KEY_FINAL] ?: defaultFinal }.collectAsState(initial = defaultFinal)

    var showSettings by rememberSaveable { mutableStateOf(api.isBlank() || preview.isBlank() || fin.isBlank()) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("4K Sticker Generator") },
                    actions = {
                        TextButton(onClick = { showSettings = !showSettings }) {
                            Text(if (showSettings) "Open" else "Settings")
                        }
                    }
                )
            }
        ) { pad ->
            if (showSettings) {
                SettingsScreen(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    api = api, preview = preview, fin = fin
                ) { a, p, f ->
                    scope.launch { ds.edit { it[KEY_API] = a.trim(); it[KEY_PREVIEW] = p.trim(); it[KEY_FINAL] = f.trim() } }
                    showSettings = false
                }
            } else {
                GeneratorScreen(
                    modifier = Modifier.fillMaxSize().padding(pad),
                    api = api, previewVersion = preview, finalVersion = fin
                )
            }
        }
    }
}
