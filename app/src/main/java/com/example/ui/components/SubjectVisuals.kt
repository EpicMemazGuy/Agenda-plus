package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.TempleHindu
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.AppThemeStyle
import com.example.model.ResolvedSubjectInfo
import com.example.model.SubjectCustomizationState
import com.example.model.SubjectCustomizationCatalog
import com.example.ui.theme.LocalAppThemeStyle

val LocalSubjectCustomizationState = staticCompositionLocalOf { SubjectCustomizationState() }

@Immutable
data class SubjectIconEntry(
    val name: String,
    val labelDutch: String,
    val icon: ImageVector,
    val keywords: String
)

object SubjectIconCatalog {

    val icons: List<SubjectIconEntry> = listOf(
        SubjectIconEntry("school", "School", Icons.Default.School, "school algemeen les"),
        SubjectIconEntry("calculate", "Rekenmachine", Icons.Default.Calculate, "wiskunde rekenen math wi"),
        SubjectIconEntry("functions", "Functies", Icons.Default.Functions, "wiskunde b algebra calculus"),
        SubjectIconEntry("pie_chart", "Grafiek", Icons.Default.PieChart, "statistiek economie wiskunde c"),
        SubjectIconEntry("menu_book", "Boek", Icons.AutoMirrored.Filled.MenuBook, "nederlands literatuur lezen ne"),
        SubjectIconEntry("auto_stories", "Studieboek", Icons.Default.AutoStories, "lezen studie boek"),
        SubjectIconEntry("edit_note", "Schrijven", Icons.Default.EditNote, "notities essay verslag"),
        SubjectIconEntry("translate", "Vertalen", Icons.Default.Translate, "engels frans duits talen en"),
        SubjectIconEntry("language", "Taal", Icons.Default.Language, "duits spaans talen wereld"),
        SubjectIconEntry("chat", "Gesprek", Icons.Default.Chat, "frans spreekvaardigheid taal"),
        SubjectIconEntry("public", "Wereldbol", Icons.Default.Public, "aardrijkskunde geografie ak"),
        SubjectIconEntry("explore", "Kompas", Icons.Default.Explore, "aardrijkskunde kaart reizen"),
        SubjectIconEntry("history_edu", "Geschiedenis", Icons.Default.HistoryEdu, "geschiedenis pen gs"),
        SubjectIconEntry("temple_hindu", "Klassiek", Icons.Default.TempleHindu, "grieks latijn oudheid kcv"),
        SubjectIconEntry("biotech", "Biologie", Icons.Default.Biotech, "biologie microscoop natuur bi"),
        SubjectIconEntry("eco", "Natuur", Icons.Default.Eco, "biologie ecologie milieu"),
        SubjectIconEntry("bolt", "Natuurkunde", Icons.Default.Bolt, "natuurkunde stroom energie na"),
        SubjectIconEntry("science", "Scheikunde", Icons.Default.Science, "scheikunde lab chemie sk"),
        SubjectIconEntry("hub", "NLT", Icons.Default.Hub, "nlt techniek wetenschap"),
        SubjectIconEntry("rocket_launch", "Raket", Icons.Default.RocketLaunch, "ruimtevaart natuurkunde stem"),
        SubjectIconEntry("trending_up", "Economie", Icons.Default.TrendingUp, "economie grafiek markt ec"),
        SubjectIconEntry("account_balance", "Financiën", Icons.Default.AccountBalance, "bedrijfseconomie recht beco"),
        SubjectIconEntry("gavel", "Recht", Icons.Default.Gavel, "maatschappijleer recht ma"),
        SubjectIconEntry("balance", "Balans", Icons.Default.Balance, "maatschappijwetenschappen maw"),
        SubjectIconEntry("directions_run", "Sport", Icons.Default.DirectionsRun, "gym lo los rennen"),
        SubjectIconEntry("fitness_center", "Fitness", Icons.Default.FitnessCenter, "sport kracht lo"),
        SubjectIconEntry("sports_soccer", "Voetbal", Icons.Default.SportsSoccer, "sport bal gym"),
        SubjectIconEntry("palette", "Palet", Icons.Default.Palette, "tekenen kunst bv ckv"),
        SubjectIconEntry("brush", "Penseel", Icons.Default.Brush, "beeldende vorming tekenen"),
        SubjectIconEntry("architecture", "Ontwerp", Icons.Default.Architecture, "techniek ontwerpen tekenen"),
        SubjectIconEntry("theater_comedy", "Theater", Icons.Default.TheaterComedy, "ckv drama toneel"),
        SubjectIconEntry("music_note", "Muziek", Icons.Default.MusicNote, "muziek noot zang mu"),
        SubjectIconEntry("piano", "Piano", Icons.Default.Piano, "muziek instrument toetsen"),
        SubjectIconEntry("camera_alt", "Fotografie", Icons.Default.CameraAlt, "media ckv film foto"),
        SubjectIconEntry("code", "Code", Icons.Default.Code, "informatica programmeren in"),
        SubjectIconEntry("computer", "Computer", Icons.Default.Computer, "informatica digitaal ict"),
        SubjectIconEntry("terminal", "Terminal", Icons.Default.Terminal, "programmeren developer"),
        SubjectIconEntry("groups", "Mentorles", Icons.Default.Groups, "mentor klas mentoruur ml"),
        SubjectIconEntry("psychology", "Filosofie", Icons.Default.Psychology, "filosofie psychologie fi"),
        SubjectIconEntry("lightbulb", "Idee", Icons.Default.Lightbulb, "project brainstorm studie"),
        SubjectIconEntry("tune", "KWT", Icons.Default.Tune, "keuzeles kwt keuzewerktijd"),
        SubjectIconEntry("extension", "Puzzel", Icons.Default.Extension, "logica puzzel module"),
        SubjectIconEntry("work", "Werk / Stage", Icons.Default.Work, "stage lob orientatie")
    )

    private val byName: Map<String, SubjectIconEntry> = icons.associateBy { it.name }

    fun iconFor(iconName: String?): ImageVector {
        if (iconName.isNullOrBlank()) return Icons.Default.School
        return byName[iconName.trim()]?.icon ?: Icons.Default.School
    }

    fun filter(query: String): List<SubjectIconEntry> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return icons
        return icons.filter {
            it.labelDutch.lowercase().contains(clean) ||
                it.name.lowercase().contains(clean) ||
                it.keywords.contains(clean)
        }
    }
}

@Immutable
data class ResolvedSubjectUi(
    val info: ResolvedSubjectInfo,
    val icon: ImageVector,
    val customColor: Color?
) {
    val displayName: String get() = info.displayName
    val originalCodeLabel: String? get() = info.originalCodeLabel
    val normalizedCode: String get() = info.normalizedCode
    val rawSubjectText: String get() = info.rawSubjectText
    val showOriginalCode: Boolean get() = info.originalCodeLabel != null
    val rawCode: String get() = info.originalCodeLabel ?: info.normalizedCode
}

@Composable
fun rememberSubjectDisplay(
    rawTitleOrName: String?,
    rawCode: String? = null
): ResolvedSubjectUi {
    return rememberResolvedSubjectUi(
        rawSubject = rawTitleOrName,
        rawTitle = rawTitleOrName,
        explicitCode = rawCode
    )
}

@Composable
fun rememberResolvedSubjectUi(
    rawSubject: String?,
    rawTitle: String? = null,
    explicitCode: String? = null
): ResolvedSubjectUi {
    val state = LocalSubjectCustomizationState.current
    val resolved = SubjectCustomizationCatalog.resolveSubject(
        rawSubject = rawSubject,
        rawTitle = rawTitle,
        explicitCode = explicitCode,
        state = state
    )
    return ResolvedSubjectUi(
        info = resolved,
        icon = SubjectIconCatalog.iconFor(resolved.iconName),
        customColor = resolved.accentColorHex?.let { Color(it) }
    )
}

fun Color.contrastForeground(): Color {
    return if (this.luminance() > 0.56f) Color(0xFF111111) else Color.White
}

@Composable
fun shouldApplyMetroSubjectTileColor(subjectColor: Color?): Boolean {
    val state = LocalSubjectCustomizationState.current
    return LocalAppThemeStyle.current == AppThemeStyle.WINDOWS_PHONE &&
        state.useSubjectColorsForMetroTiles &&
        subjectColor != null
}
