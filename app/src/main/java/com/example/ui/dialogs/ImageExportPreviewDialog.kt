package com.example.ui.dialogs

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.appButtonShape
import com.example.ui.components.appCardShape
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.appSheetShape
import com.example.ui.components.nothingCardBorder
import com.example.util.GradeExporter
import com.example.util.StoryAndScheduleExporter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageExportPreviewDialog(
    title: String,
    pngFile: File?,
    isGenerating: Boolean = false,
    shareChooserTitle: String = "Deel afbeelding",
    onDismiss: () -> Unit,
    controlsContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    val context = LocalContext.current
    var saveFeedback by remember(pngFile) { mutableStateOf<String?>(null) }

    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { targetUri ->
        val file = pngFile
        if (targetUri != null && file != null && file.exists()) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                }
            }.isSuccess
            saveFeedback = if (saved) "Afbeelding opgeslagen!" else "Opslaan mislukt."
        }
    }

    val previewBitmap = remember(pngFile?.absolutePath) {
        pngFile?.takeIf { it.exists() }?.let { file ->
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
            }.getOrNull()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = appSheetShape(),
        containerColor = appSheetContainerColor()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("export_preview_close")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Sluiten")
                }
            }

            if (controlsContent != null) {
                Surface(
                    shape = appCardShape(standard = 16.dp, nothing = 18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = nothingCardBorder(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        content = controlsContent
                    )
                }
            }

            Surface(
                shape = appCardShape(standard = 18.dp, nothing = 20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGenerating || previewBitmap == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 48.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                            Text(
                                text = "Afbeelding wordt gerenderd...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val aspectRatio = (previewBitmap.width.toFloat() / previewBitmap.height.toFloat())
                            .coerceIn(0.42f, 1.85f)
                        Image(
                            bitmap = previewBitmap,
                            contentDescription = title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(if (aspectRatio < 0.8f) 0.72f else 1f)
                                .aspectRatio(aspectRatio)
                        )
                    }
                }
            }

            if (!saveFeedback.isNullOrBlank()) {
                Text(
                    text = saveFeedback!!,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val file = pngFile ?: return@OutlinedButton
                        val result = StoryAndScheduleExporter.savePngToGallery(
                            context = context,
                            pngFile = file,
                            displayName = file.nameWithoutExtension
                        )
                        result.onSuccess { uri ->
                            if (uri != null) {
                                saveFeedback = "Opgeslagen in Galerij (Pictures/Agenda+)"
                            } else {
                                safLauncher.launch("${file.nameWithoutExtension}.png")
                            }
                        }.onFailure { err ->
                            saveFeedback = err.message ?: "Opslaan mislukt."
                        }
                    },
                    enabled = pngFile != null && !isGenerating,
                    shape = appButtonShape(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("export_save_gallery_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Opslaan in Galerij", maxLines = 1)
                }

                Button(
                    onClick = {
                        val file = pngFile ?: return@Button
                        GradeExporter.shareFileViaProvider(
                            context = context,
                            file = file,
                            mimeType = "image/png",
                            chooserTitle = shareChooserTitle
                        )
                    },
                    enabled = pngFile != null && !isGenerating,
                    shape = appButtonShape(),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("export_share_png_button")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Deel PNG", maxLines = 1)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}
