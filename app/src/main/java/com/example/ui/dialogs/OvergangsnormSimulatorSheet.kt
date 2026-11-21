package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.SubjectGradeSummary
import com.example.model.capitalizeSubjectName
import com.example.ui.components.OneUiPillBadge
import com.example.ui.components.appCardShape
import com.example.ui.components.appInnerCardShape
import com.example.ui.components.appScreenHorizontalPadding
import com.example.ui.components.appSheetContainerColor
import com.example.ui.components.appSheetShape
import com.example.ui.components.isOneUi85
import com.example.ui.components.nothingCardBorder
import java.util.Locale
import kotlin.math.floor
import kotlin.math.round

data class OvergangsSubjectRow(
    val subjectKey: String,
    val subjectCode: String,
    val subjectName: String,
    val originalAverage: Double,
    val simulatedAverage: Double,
    val roundedReportGrade: Int,
    val tekortPoints: Int,
    val isKernvak: Boolean
)

data class OvergangsNormStatus(
    val overallAverage: Double,
    val totalTekortenpunten: Int,
    val totalOnvoldoendes: Int,
    val kernvakOnvoldoendes: Int,
    val kernvakFives: Int,
    val kernvakFourOrLess: Int,
    val kernvakkenValid: Boolean,
    val verdict: String,
    val verdictDetail: String,
    val rows: List<OvergangsSubjectRow>
)

private fun isKernvakSubject(subjectName: String, subjectCode: String): Boolean {
    val name = subjectName.trim().lowercase(Locale.US)
    val code = subjectCode.trim().uppercase(Locale.US)
    if (name.contains("nederlands") || name.contains("engels") || name.contains("wiskunde")) {
        return true
    }
    return code.startsWith("NE") || code.startsWith("EN") || code.startsWith("WI")
}

private fun dutchReportGrade(avg: Double): Int {
    val safe = avg.coerceIn(1.0, 10.0)
    return floor(safe + 0.50001).toInt().coerceIn(1, 10)
}

private fun tekortenpuntenForAverage(avg: Double): Int = when {
    avg < 3.5 -> 3
    avg < 4.5 -> 2
    avg < 5.5 -> 1
    else -> 0
}

fun calculateOvergangsNormStatus(
    summaries: List<SubjectGradeSummary>,
    overrides: Map<String, Double> = emptyMap()
): OvergangsNormStatus {
    val rows = summaries.map { summary ->
        val key = "${summary.subjectCode}_${summary.subjectName}"
        val simulated = (
            overrides[key]
                ?: overrides[summary.subjectCode]
                ?: overrides[summary.subjectName]
                ?: summary.weightedAverage
            ).coerceIn(1.0, 10.0)
        val roundedSim = round(simulated * 10.0) / 10.0
        val reportGrade = dutchReportGrade(roundedSim)
        val tekort = tekortenpuntenForAverage(roundedSim)
        val kernvak = isKernvakSubject(summary.subjectName, summary.subjectCode)
        OvergangsSubjectRow(
            subjectKey = key,
            subjectCode = summary.subjectCode,
            subjectName = summary.subjectName.capitalizeSubjectName().ifBlank { summary.subjectCode },
            originalAverage = summary.weightedAverage,
            simulatedAverage = roundedSim,
            roundedReportGrade = reportGrade,
            tekortPoints = tekort,
            isKernvak = kernvak
        )
    }

    val totalTekort = rows.sumOf { it.tekortPoints }
    val totalOnvoldoendes = rows.count { it.simulatedAverage < 5.5 }
    val kernvakRows = rows.filter { it.isKernvak }
    val kernvakFives = kernvakRows.count { it.simulatedAverage in 4.5..5.4999 }
    val kernvakFourOrLess = kernvakRows.count { it.simulatedAverage < 4.5 }
    val kernvakOnvoldoendes = kernvakFives + kernvakFourOrLess
    val kernvakkenValid = kernvakFourOrLess == 0 && kernvakFives <= 1

    val overall = if (rows.isNotEmpty()) {
        round((rows.sumOf { it.simulatedAverage } / rows.size) * 10.0) / 10.0
    } else 0.0

    val verdict: String
    val verdictDetail: String
    when {
        totalTekort <= 4 && totalOnvoldoendes <= 2 && kernvakkenValid -> {
            verdict = "Bevorderbaar"
            verdictDetail = if (totalTekort == 0) {
                "Geen tekortpunten — je gaat ruim over!"
            } else {
                "$totalTekort tekortpunt(en) en max. 1x een 5 op een kernvak."
            }
        }
        (totalTekort in 5..6 && totalOnvoldoendes <= 3 && kernvakFourOrLess <= 1) ||
            (totalTekort <= 4 && !kernvakkenValid && kernvakFourOrLess == 0) -> {
            verdict = "Bespreekgeval"
            verdictDetail = buildString {
                append("$totalTekort tekortpunt(en)")
                if (!kernvakkenValid) {
                    append(" • Let op kernvaknorm (Ne/En/Wi max. 1x een 5)")
                }
            }
        }
        else -> {
            verdict = "Zittenblijven risico"
            verdictDetail = "$totalTekort tekortpunten over $totalOnvoldoendes onvoldoende(n)."
        }
    }

    return OvergangsNormStatus(
        overallAverage = overall,
        totalTekortenpunten = totalTekort,
        totalOnvoldoendes = totalOnvoldoendes,
        kernvakOnvoldoendes = kernvakOnvoldoendes,
        kernvakFives = kernvakFives,
        kernvakFourOrLess = kernvakFourOrLess,
        kernvakkenValid = kernvakkenValid,
        verdict = verdict,
        verdictDetail = verdictDetail,
        rows = rows
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OvergangsnormSimulatorSheet(
    summaries: List<SubjectGradeSummary>,
    onDismiss: () -> Unit
) {
    var overrides by remember(summaries) { mutableStateOf<Map<String, Double>>(emptyMap()) }
    val status = remember(summaries, overrides) {
        calculateOvergangsNormStatus(summaries, overrides)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = appSheetShape(),
        containerColor = appSheetContainerColor(),
        tonalElevation = if (isOneUi85) 0.dp else BottomSheetDefaults.Elevation,
        modifier = Modifier.testTag("overgangsnorm_simulator_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = appScreenHorizontalPadding(standard = 20.dp))
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Overgangsnorm Simulator",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Speel met je eindgemiddelden en bekijk je tekortpunten",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("close_overgangsnorm_sheet")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Sluiten")
                }
            }

            // Verdict Banner Card
            val isDanger = status.verdict == "Zittenblijven risico"
            val isWarning = status.verdict == "Bespreekgeval"
            Surface(
                shape = appCardShape(standard = 20.dp, nothing = 22.dp),
                color = when {
                    isDanger -> MaterialTheme.colorScheme.errorContainer
                    isWarning -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.primaryContainer
                },
                border = nothingCardBorder(isAlert = isDanger),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overgangsnorm_verdict_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = status.verdict,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = when {
                                isDanger -> MaterialTheme.colorScheme.onErrorContainer
                                isWarning -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            modifier = Modifier.testTag("overgangsnorm_verdict_text")
                        )
                        OneUiPillBadge(
                            text = "Gem. ${status.overallAverage}",
                            isError = isDanger
                        )
                    }
                    Text(
                        text = status.verdictDetail,
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            isDanger -> MaterialTheme.colorScheme.onErrorContainer
                            isWarning -> MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OneUiPillBadge(
                            text = "Tekortpunten: ${status.totalTekortenpunten}",
                            isError = status.totalTekortenpunten > 4
                        )
                        OneUiPillBadge(
                            text = "Onvoldoendes: ${status.totalOnvoldoendes}",
                            isError = status.totalOnvoldoendes > 2
                        )
                        OneUiPillBadge(
                            text = if (status.kernvakkenValid) "Kernvakken OK" else "Kernvak risico",
                            isError = !status.kernvakkenValid
                        )
                    }
                }
            }

            if (overrides.isNotEmpty()) {
                OutlinedButton(
                    onClick = { overrides = emptyMap() },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("overgang_reset_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Herstel naar echte cijfers")
                }
            }

            // Subject rows list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(status.rows, key = { it.subjectKey }) { row ->
                    val isChanged = row.simulatedAverage != row.originalAverage
                    Surface(
                        shape = appInnerCardShape(standard = 16.dp, nothing = 18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = nothingCardBorder(isAlert = row.tekortPoints > 0),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overgang_row_${row.subjectCode}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = row.subjectName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (row.isKernvak) {
                                        OneUiPillBadge(text = "Kernvak")
                                    }
                                }
                                Text(
                                    text = buildString {
                                        append("Rapport: ${row.roundedReportGrade}")
                                        if (row.tekortPoints > 0) append(" • ${row.tekortPoints} tekort")
                                        if (isChanged) append(" (was ${row.originalAverage})")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (row.tekortPoints > 0) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                FilledTonalIconButton(
                                    onClick = {
                                        val next = round(((row.simulatedAverage - 0.1).coerceAtLeast(1.0)) * 10.0) / 10.0
                                        overrides = overrides + (row.subjectKey to next)
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("overgang_minus_${row.subjectCode}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Remove,
                                        contentDescription = "Verlaag ${row.subjectName}"
                                    )
                                }

                                Text(
                                    text = String.format(Locale.US, "%.1f", row.simulatedAverage),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (row.simulatedAverage < 5.5) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )

                                FilledTonalIconButton(
                                    onClick = {
                                        val next = round(((row.simulatedAverage + 0.1).coerceAtMost(10.0)) * 10.0) / 10.0
                                        overrides = overrides + (row.subjectKey to next)
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("overgang_plus_${row.subjectCode}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Verhoog ${row.subjectName}"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
