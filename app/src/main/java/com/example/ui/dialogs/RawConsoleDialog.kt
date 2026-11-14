package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.util.RawConsoleLogger

@Composable
fun RawConsoleDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val logs by RawConsoleLogger.logs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .testTag("raw_console_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF18181B) // Dark CLI terminal background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // CLI Terminal Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF27272A))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color(0xFF4ADE80), // Bright CLI green
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Netwerk- & API-console",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF4F4F5)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Copy Log Button
                        IconButton(
                            onClick = {
                                val rawText = RawConsoleLogger.getRawLogString()
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = ClipData.newPlainText("Raw CLI Log", rawText)
                                clipboard?.setPrimaryClip(clip)
                                Toast.makeText(context, "Raw logs gekopieerd naar klembord!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.testTag("copy_raw_log_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Kopieer log",
                                tint = Color(0xFF38BDF8) // Bright Cyan
                            )
                        }

                        // Clear Button
                        IconButton(onClick = { RawConsoleLogger.clear() }) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Wissen",
                                tint = Color(0xFFA1A1AA)
                            )
                        }

                        // Close Button
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Sluiten",
                                tint = Color(0xFFF4F4F5)
                            )
                        }
                    }
                }

                // CLI Quick Info Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF09090B))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Regels: ${logs.size} | Kopieer en plak de exacte fout in de chat",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFA1A1AA),
                        fontSize = 11.sp
                    )

                    Button(
                        onClick = {
                            val rawText = RawConsoleLogger.getRawLogString()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("Console-log", rawText)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Gekopieerd! Plak in de chat.", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF16A34A),
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Kopieer Log", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }

                HorizontalDivider(color = Color(0xFF3F3F46))

                // Monospace Log Output List
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF09090B))
                        .padding(8.dp)
                        .testTag("raw_cli_output_feed"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(logs) { entry ->
                        val levelColor = when (entry.level) {
                            "ERROR" -> Color(0xFFEF4444) // Red
                            "WARN" -> Color(0xFFF59E0B) // Amber
                            "HTTP" -> Color(0xFF38BDF8) // Cyan
                            "CONSOLE" -> Color(0xFFA855F7) // Purple
                            "SYSTEM" -> Color(0xFF4ADE80) // Green
                            else -> Color(0xFFE4E4E7) // White
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF18181B), shape = RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "[${entry.timestamp}] ",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFF71717A)
                                )
                                Text(
                                    text = "[${entry.level}] ",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = levelColor
                                )
                                Text(
                                    text = "<${entry.tag}>",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color(0xFFA1A1AA)
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = entry.message,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFF4F4F5)
                            )
                        }
                    }
                }
            }
        }
    }
}
