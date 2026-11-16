package com.example.ui.dialogs

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeStyle
import com.example.model.GradeItem
import com.example.model.SubjectGradeSummary
import com.example.ui.components.isNothingOs
import com.example.ui.components.nothingCardBorder
import com.example.ui.theme.LocalDarkThemeActive
import com.example.util.GradeExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeExportSheet(
    summaries: List<SubjectGradeSummary>,
    overallAverage: Double,
    studentName: String,
    appThemeStyle: AppThemeStyle,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allGrades = remember(summaries) {
        summaries.flatMap { it.grades }.sortedByDescending { it.date }
    }
    var selectedIds by remember(allGrades) {
        mutableStateOf(allGrades.take(3).map { it.id }.toSet())
    }
    var isWorking by remember { mutableStateOf(false) }

    val saveCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(GradeExporter.generateCsvString(summaries).toByteArray(Charsets.UTF_8))
                        }
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (ok) "CSV-bestand opgeslagen" else "Opslaan van CSV mislukt",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val savePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            GradeExporter.writePdfReport(out, summaries, studentName, overallAverage)
                        }
                    }.isSuccess
                }
                Toast.makeText(
                    context,
                    if (ok) "PDF-rapport opgeslagen" else "Opslaan van PDF mislukt",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.testTag("grade_export_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Exporteer Cijfers",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Deel een Agenda+ cijferkaart of sla alles op als CSV/PDF",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Sluiten")
                }
            }

            // Full Grade List Export (CSV & PDF)
            Surface(
                shape = RoundedCornerShape(if (isNothingOs) 20.dp else 16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = nothingCardBorder(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Volledig cijferoverzicht (${summaries.size} vakken)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { saveCsvLauncher.launch("AgendaPlus_Cijfers.csv") },
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_csv_save_button")
                        ) {
                            Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("CSV Opslaan")
                        }
                        OutlinedButton(
                            onClick = { savePdfLauncher.launch("AgendaPlus_Cijfers.pdf") },
                            shape = RoundedCornerShape(percent = 50),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("export_pdf_save_button")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("PDF Opslaan")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    isWorking = true
                                    val file = withContext(Dispatchers.IO) {
                                        GradeExporter.writeCsvFile(context, summaries)
                                    }
                                    isWorking = false
                                    GradeExporter.shareFileViaProvider(context, file, "text/csv", "Deel CSV Cijferoverzicht")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deel CSV")
                        }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    isWorking = true
                                    val file = withContext(Dispatchers.IO) {
                                        GradeExporter.writePdfFile(context, summaries, studentName, overallAverage)
                                    }
                                    isWorking = false
                                    GradeExporter.shareFileViaProvider(context, file, "application/pdf", "Deel PDF Cijferrapport")
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Deel PDF")
                        }
                    }
                }
            }

            // PNG Share Card Selection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Selecteer cijfers voor PNG Kaart (${selectedIds.size}/8)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = {
                        selectedIds = if (selectedIds.isNotEmpty()) {
                            emptySet()
                        } else {
                            allGrades.take(8).map { it.id }.toSet()
                        }
                    }
                ) {
                    Text(if (selectedIds.isNotEmpty()) "Wis selectie" else "Kies laatste")
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(allGrades, key = { it.id }) { grade ->
                    val isSelected = grade.id in selectedIds
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerLow,
                        border = nothingCardBorder(isAlert = isSelected),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                selectedIds = if (isSelected) {
                                    selectedIds - grade.id
                                } else if (selectedIds.size < 8) {
                                    selectedIds + grade.id
                                } else {
                                    Toast.makeText(context, "Maximaal 8 cijfers per PNG-kaart", Toast.LENGTH_SHORT).show()
                                    selectedIds
                                }
                            }
                            .testTag("export_select_grade_${grade.id}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = grade.subjectName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${grade.description} • ${grade.weight}x",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${grade.score}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (grade.isPassing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            val isDarkModeActive = LocalDarkThemeActive.current
            val primaryColorArgb = MaterialTheme.colorScheme.primary.toArgb()

            Button(
                onClick = {
                    val chosen = allGrades.filter { it.id in selectedIds }
                    if (chosen.isEmpty()) return@Button
                    scope.launch {
                        isWorking = true
                        val pngFile = withContext(Dispatchers.IO) {
                            GradeExporter.renderGradeShareCardPng(
                                context = context,
                                selectedGrades = chosen,
                                overallAverage = overallAverage,
                                studentName = studentName,
                                appThemeStyle = appThemeStyle,
                                isDarkMode = isDarkModeActive,
                                dynamicPrimaryArgb = primaryColorArgb
                            )
                        }
                        isWorking = false
                        GradeExporter.shareFileViaProvider(
                            context = context,
                            file = pngFile,
                            mimeType = "image/png",
                            chooserTitle = "Deel Agenda+ Cijferkaart"
                        )
                    }
                },
                enabled = selectedIds.isNotEmpty() && !isWorking,
                shape = RoundedCornerShape(percent = 50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("export_png_share_button")
            ) {
                if (isWorking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deel PNG Cijferkaart", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
