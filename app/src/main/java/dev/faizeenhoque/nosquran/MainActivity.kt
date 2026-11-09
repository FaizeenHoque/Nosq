package dev.faizeenhoque.nosquran

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuranLoader(application: Application) : AndroidViewModel(application) {
    var repository by mutableStateOf<QuranRepository?>(null)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    android.os.Trace.beginSection("NOS.repository")
                    try {
                        QuranRepository(getApplication())
                    } finally {
                        android.os.Trace.endSection()
                    }
                }
            }
                .onSuccess { repository = it }
                .onFailure {
                    error =
                        "The offline Quran could not be opened. Please retry or reinstall from a trusted source."
                }
        }
    }
}

class MainActivity : ComponentActivity() {
    private var probe: PerformanceProbe? = null

    override fun onDestroy() {
        probe?.close()
        super.onDestroy()
    }

    override fun onStop() {
        androidx.lifecycle.ViewModelProvider(this)[QuranState::class.java].flushProgress()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (BuildConfig.BENCHMARK) probe = PerformanceProbe(this)
        setContent {
            val loader: QuranLoader = viewModel()
            val state: QuranState = viewModel()
            val repo = loader.repository
            androidx.activity.compose.ReportDrawnWhen { repo != null && state.ready }
            if (BuildConfig.BENCHMARK && state.ready)
                LaunchedEffect(state) { state.finishOnboarding() }
            if (repo != null && state.ready)
                QuranApp(
                    repo,
                    state,
                    if (BuildConfig.BENCHMARK) intent.getStringExtra("benchmarkRoute") else null,
                )
            else
                NosTheme(state.settings) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(AppMetadata.Name, style = MaterialTheme.typography.displayMedium)
                            Spacer(Modifier.height(24.dp))
                            if (loader.error != null) {
                                Text(loader.error!!)
                                Spacer(Modifier.height(16.dp))
                                NosButton(onClick = loader::load) { Text("Try again") }
                            } else {
                                PlaybackActivity(true)
                                Spacer(Modifier.height(16.dp))
                                Eyebrow("PREPARING YOUR READING SPACE")
                            }
                        }
                    }
                }
        }
    }
}
