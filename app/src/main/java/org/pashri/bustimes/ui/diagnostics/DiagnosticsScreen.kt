package org.pashri.bustimes.ui.diagnostics

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Shows anything the crash log has recorded.
 *
 * Reached by long-pressing the locate button, because it exists for the rare
 * occasion when something went wrong and the stack trace is the only way to
 * find out what — not as a feature anyone should trip over.
 *
 * @param log the recorded crashes, or null when there are none.
 * @param onBack called to leave the screen.
 * @param onClear called to empty the log.
 * @param modifier layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    log: String?,
    onBack: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Diagnostics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    if (log != null) {
                        IconButton(
                            onClick = { context.startActivity(shareIntent(log)) },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Share,
                                contentDescription = "Share the log",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (log == null) {
                Text(
                    text = "Nothing recorded. The app has not crashed since this log was last cleared.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
                return@Column
            }
            TextButton(onClick = onClear, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(text = "Clear")
            }
            Text(
                text = log,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
    }
}

/**
 * Builds a share intent for the log text.
 *
 * @param log the text to share.
 * @return a chooser intent.
 */
private fun shareIntent(log: String): Intent {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Bustimes crash log")
        putExtra(Intent.EXTRA_TEXT, log)
    }
    return Intent.createChooser(send, "Share the crash log")
}
