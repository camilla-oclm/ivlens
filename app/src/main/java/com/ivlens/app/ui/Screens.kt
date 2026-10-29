package com.ivlens.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ivlens.app.BuildConfig
import com.ivlens.app.core.Nickname
import com.ivlens.app.core.Pokedex
import com.ivlens.app.core.VerdictKind
import com.ivlens.app.graph
import com.ivlens.app.service.OverlayService
import com.ivlens.app.store.AppSettings
import com.ivlens.app.store.SavedMon
import com.ivlens.app.store.ThemeMode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private const val GO_PACKAGE = "com.nianticlabs.pokemongo"

private fun openGo(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(GO_PACKAGE)
    if (launch != null) context.startActivity(launch)
    else Toast.makeText(context, "Pokémon GO isn't installed on this phone.", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun ScreenTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = blockText(30.sp, 900), modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun PanelTitle(text: String) {
    Text(text.uppercase(), style = blockText(16.sp, 900), modifier = Modifier.padding(bottom = 4.dp))
}

@Composable
private fun Body(text: String, color: Color = LocalInk.current) {
    Text(text, style = blockText(14.sp, 500, color))
}

@Composable
internal fun Small(text: String) {
    Text(text, style = blockText(12.sp, 500, Block.Muted))
}

// ===================================================================== Scan

@Composable
fun ScanScreen() {
    val context = LocalContext.current
    val running by OverlayService.running.collectAsState()
    var canOverlay by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var canNotify by remember { mutableStateOf(notificationsAllowed(context)) }
    var openGoWhenReady by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        canOverlay = Settings.canDrawOverlays(context)
        canNotify = notificationsAllowed(context)
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = it }
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val data = res.data
        if (res.resultCode == Activity.RESULT_OK && data != null) {
            OverlayService.start(context, res.resultCode, data)
            openGoWhenReady = true
        }
    }
    // Switch to the game only once the bubble's service is up and capturing.
    LaunchedEffect(running, openGoWhenReady) {
        if (running && openGoWhenReady) {
            openGoWhenReady = false
            openGo(context)
        }
    }
    fun requestOverlay() {
        context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
    }
    fun startBubble() {
        if (!Settings.canDrawOverlays(context)) {
            requestOverlay()
            return
        }
        val mpm = context.getSystemService(MediaProjectionManager::class.java)
        val intent = if (Build.VERSION.SDK_INT >= 34) {
            mpm.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
        } else mpm.createScreenCaptureIntent()
        captureLauncher.launch(intent)
    }

    ScanContent(
        running = running,
        canOverlay = canOverlay,
        canNotify = canNotify,
        onStart = ::startBubble,
        onOpenGo = { openGo(context) },
        onStop = { OverlayService.stop(context) },
        onRequestOverlay = ::requestOverlay,
        onRequestNotify = { notifyLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
    )
}

/** The Scan tab's layout, without the permission and service plumbing. */
@Composable
fun ScanContent(
    running: Boolean,
    canOverlay: Boolean,
    canNotify: Boolean,
    onStart: () -> Unit,
    onOpenGo: () -> Unit,
    onStop: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotify: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Block.Page).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            ScreenTitle("IV Lens")
            Body("Open a Pokémon in Pokémon GO and get its IVs, PvP ranks and a straight answer: keep it or not.", Block.Muted)
        }

        BlockPanel(fill = if (running) Block.Teal else Block.Yellow) {
            Text(if (running) "THE BUBBLE IS ON" else "START THE BUBBLE", style = blockText(20.sp, 900))
            Spacer(Modifier.height(4.dp))
            Body(
                if (running) "Open a Pokémon in the game. Long-press the bubble to turn it off."
                else "Android will ask to record or share your screen. Choose the entire screen. The screen is read on your phone and images are never uploaded.",
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (running) {
                    BlockButton("Open Pokémon GO", onOpenGo, fill = Block.Paper)
                    BlockButton("Stop", onStop, fill = Block.Paper)
                } else {
                    BlockButton(if (canOverlay) "Start bubble" else "Allow overlay first", onStart, fill = Block.Paper)
                }
            }
        }

        BlockPanel {
            PanelTitle("Permissions")
            PermissionRow("Display over other apps", "Needed for the bubble, the chip and the card.", canOverlay, onRequestOverlay)
            if (Build.VERSION.SDK_INT >= 33) {
                PermissionRow("Notifications", "Optional. Shows a Stop button while the bubble is on.", canNotify, onRequestNotify)
            }
        }

        BlockPanel {
            PanelTitle("How it works")
            val steps = listOf(
                "Open a Pokémon in the game with the POWER UP button on screen.",
                "A chip appears over the Pokémon by itself: HUNDO, PVP, RAIDS, KEEP or TRANSFER. Swipe to the next Pokémon and it updates.",
                "If it says MOVES?, scroll down a little so its moves show. The chip then gives the final answer.",
                "Several IVs possible? Open the game's Appraise screen for that Pokémon. Once the three bars show, the chip switches to the exact IVs.",
                "Tap the chip (or the bubble) for the full card. Drag the chip anywhere you like.",
                "If the Pokémon hides the CP, tap CP on the card and type it in.",
                "Exact IVs? Tap COPY NAME on the card, then rename the Pokémon in the game and paste. Its IVs then show in your Pokémon list. " +
                    "Torchic71⑦⑭⑪ means 71%, with Attack 7, Defense 14 and HP 11. A PvP pick shows its league and rank instead: U8 is Ultra League #8.",
            )
            steps.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(24.dp).background(Block.Ink), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = blockText(12.sp, 900, Block.OnInk))
                    }
                    Spacer(Modifier.width(10.dp))
                    Body(s)
                }
            }
        }

        BlockPanel {
            PanelTitle("What the chip says")
            val words = listOf(
                Triple("HUNDO", VerdictKind.HUNDO, "Perfect 15/15/15. Keep it."),
                Triple("PVP", VerdictKind.PVP, "A species that's used in Great or Ultra League, with IVs that suit it. Keep it and power it to the level the card gives."),
                Triple("RAIDS", VerdictKind.RAIDS, "One of the best raid attackers of its type. Keep it and give it the moves the card names."),
                Triple("KEEP", VerdictKind.KEEP, "Good IVs, Legendary or Lucky, but no battle or raid use."),
                Triple("TRANSFER", VerdictKind.TRANSFER, "Nothing special. Safe to transfer for candy."),
                Triple("MOVES?", null, "The answer depends on its moves. Scroll down so they show."),
            )
            for ((word, kind, meaning) in words) {
                Row(Modifier.padding(vertical = 4.dp).height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    VerdictBlock(word, "", kind, false, Modifier.width(92.dp).fillMaxHeight())
                    Spacer(Modifier.width(10.dp))
                    Body(meaning)
                }
            }
            Small(
                "Tap the chip for the card: it lists what to do, step by step. In the league rows, #1 is the best of " +
                    "4,096 IV combinations. \"Over cap\" means it's already too strong for that league; \"Too small\" means " +
                    "it can't get near the league's CP limit even fully powered, so the rank doesn't matter there.",
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ===================================================================== Settings

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val graph = context.graph
    val settings by graph.settings.state.collectAsState()
    val version = remember { runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "" }
    val saved by graph.collection.items.collectAsState()
    var savedNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch { savedNote = exportSaved(context, uri, graph.collection.items.value) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { savedNote = importSaved(context, uri, graph.collection) }
    }
    SettingsContent(
        settings, version, graph.gameData.dateText, saved.size, savedNote,
        onExport = { savedNote = null; runCatching { exportLauncher.launch(savedFileName()) } },
        onImport = { savedNote = null; runCatching { importLauncher.launch(SAVED_FILE_TYPES) } },
    ) { f -> graph.settings.update(f) }
}

/** The Settings tab (its own tab since 1.22; before, a panel at the end of Scan). */
@Composable
fun SettingsContent(
    settings: AppSettings,
    version: String,
    dataDate: String,
    savedCount: Int,
    savedNote: String?,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Block.Page).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenTitle("Settings")

        BlockPanel {
            PanelTitle("Look")
            ChoiceRow("Theme", "PHONE follows your phone's dark mode. The chip and card over the game follow it too.", ThemeMode.entries, settings.theme, { it.name }) { v ->
                onSettings { it.copy(theme = v) }
            }
        }

        BlockPanel {
            PanelTitle("Scanning")
            SwitchRow("Auto-scan", "Reads each Pokémon by itself and shows the verdict chip. The bubble still works for a manual scan.", settings.autoScan) { v ->
                onSettings { it.copy(autoScan = v) }
            }
            SwitchRow("Auto save", "Puts each Pokémon the chip reads in Saved, once per Pokémon. Its entry updates when the moves or exact IVs come in.", settings.autoSave) { v ->
                onSettings { it.copy(autoSave = v) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Trainer level", style = blockText(15.sp, 700))
                    Small("Sets the level arc's end and how far you can power up.")
                }
                BlockButton("−", { onSettings { it.copy(trainerLevel = it.trainerLevel - 1) } }, fill = Block.Paper, shadow = 2.dp)
                Text("${settings.trainerLevel}", style = blockText(18.sp, 900), modifier = Modifier.width(44.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                BlockButton("+", { onSettings { it.copy(trainerLevel = it.trainerLevel + 1) } }, fill = Block.Paper, shadow = 2.dp)
            }
            SwitchRow("Read the level arc", "Uses the white dot above the Pokémon to pin down its level.", settings.useArc) { v ->
                onSettings { it.copy(useArc = v) }
            }
            // Debug build only: the Play build writes nothing to the phone's log.
            if (BuildConfig.DEBUG) {
                SwitchRow("Debug log", "Notes what each read saw in the phone's log, to fix problems over a USB or Wi-Fi debugging link. Leave it off normally.", settings.debugLog) { v ->
                    onSettings { it.copy(debugLog = v) }
                }
            }
        }

        BlockPanel {
            PanelTitle("Verdicts")
            ChoiceRow("PvP keeper", "Rank needed in Great or Ultra League.", listOf(25, 50, 100, 200), settings.pvpRankCutoff, { "TOP $it" }) { v ->
                onSettings { it.copy(pvpRankCutoff = v) }
            }
            ChoiceRow("PvP species", "How high PvPoke must rank the species in that league.", listOf(50, 100, 200), settings.pvpSpeciesCutoff, { "TOP $it" }) { v ->
                onSettings { it.copy(pvpSpeciesCutoff = v) }
            }
            ChoiceRow("Raid keeper", "Rank needed among all attackers of its moves' type, Shadows included.", listOf(10, 20, 30, 50), settings.raidRankCutoff, { "TOP $it" }) { v ->
                onSettings { it.copy(raidRankCutoff = v) }
            }
            ChoiceRow("Keep IVs from", "Anything below is Transfer unless it's good for PvP or raids.", listOf(67, 82, 91), settings.keepIv, { "$it%" }) { v ->
                onSettings { it.copy(keepIv = v) }
            }
            SwitchRow("Best buddy in PvP ranks", "Ranks assume level 51 is reachable.", settings.bestBuddyPvp) { v ->
                onSettings { it.copy(bestBuddyPvp = v) }
            }
        }

        // 1.24: a backup, or the way to another phone (and from the app's old ID, com.ivlens.app).
        BlockPanel {
            PanelTitle("Saved list")
            Small("Export puts your saved Pokémon in a file, as a backup or to move them to another phone. Import adds the Pokémon from such a file; ones you already have stay as they are.")
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockButton("Export", onExport, Modifier.weight(1f), fill = Block.Paper, shadow = 2.dp, enabled = savedCount > 0)
                BlockButton("Import", onImport, Modifier.weight(1f), fill = Block.Paper, shadow = 2.dp)
            }
            savedNote?.let { Text(it, style = blockText(13.sp, 700), modifier = Modifier.padding(top = 8.dp)) }
        }

        BlockPanel {
            PanelTitle("About")
            if (version.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Version", style = blockText(15.sp, 700), modifier = Modifier.weight(1f))
                    Text(version, style = blockText(15.sp, 900))
                }
            }
            if (dataDate.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Game data", style = blockText(15.sp, 700))
                        Small("Stats, moves and rankings. They update with the app.")
                    }
                    Text(dataDate, style = blockText(15.sp, 900))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Made by", style = blockText(15.sp, 700), modifier = Modifier.weight(1f))
                Text("Plum Comet Labs", style = blockText(15.sp, 900))
            }
            // Handed to the Discord app or the browser; IV Lens itself needs no internet for these.
            val uri = LocalUriHandler.current
            val open: (String) -> Unit = { url -> runCatching { uri.openUri(url) } }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BlockButton("Discord", { open(StudioLinks.DISCORD) }, Modifier.weight(1f), fill = Block.Paper, shadow = 2.dp)
                BlockButton("Website", { open(StudioLinks.WEBSITE) }, Modifier.weight(1f), fill = Block.Paper, shadow = 2.dp)
            }
            var report by remember { mutableStateOf(false) }
            BlockButton("Report a bug", { report = true }, Modifier.fillMaxWidth().padding(top = 8.dp), fill = Block.Paper, shadow = 2.dp)
            if (report) BugReportDialog(read = emptyList()) { report = false }
            Text(
                "Write what went wrong, then copy it to our Discord. Your app version, phone model and Android version are added. Have an idea? Tell us there too.",
                style = blockText(12.sp, 500, Block.Muted),
                modifier = Modifier.padding(top = 6.dp),
            )
            BlockButton("Privacy policy", { open(StudioLinks.PRIVACY) }, Modifier.fillMaxWidth().padding(top = 8.dp), fill = Block.Paper, shadow = 2.dp)
            var licences by remember { mutableStateOf(false) }
            BlockButton("Licences", { licences = true }, Modifier.fillMaxWidth().padding(top = 8.dp), fill = Block.Paper, shadow = 2.dp)
            if (licences) LicencesDialog { licences = false }
        }

        Small(
            "Stats come from PvPoke's game master data (MIT licence) and raid move stats from the PokeMiners game master. " +
                "Font: Archivo (SIL Open Font License). IV Lens is a fan-made tool and isn't affiliated with Niantic, Nintendo or The Pokémon Company.",
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun notificationsAllowed(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
private fun PermissionRow(title: String, body: String, granted: Boolean, onGrant: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = blockText(15.sp, 700))
            Small(body)
        }
        if (granted) {
            Box(Modifier.size(36.dp).background(Block.Teal), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = "Allowed", tint = Block.OnColor)
            }
        } else {
            BlockButton("Allow", onGrant, shadow = 2.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(title: String, body: String, options: List<T>, current: T, label: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Text(title, style = blockText(15.sp, 700))
        Small(body)
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (o in options) BlockToggle(label(o), o == current, { onPick(o) })
        }
    }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = blockText(15.sp, 700))
            Small(body)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Block.OnColor,
                checkedTrackColor = Block.Yellow,
                checkedBorderColor = Block.Ink,
                uncheckedThumbColor = Block.Ink,
                uncheckedTrackColor = Block.Paper,
                uncheckedBorderColor = Block.Ink,
            ),
        )
    }
}

@Composable
internal fun blockFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Block.Ink,
    unfocusedBorderColor = Block.Ink,
    focusedLabelColor = Block.Ink,
    unfocusedLabelColor = Block.Muted,
    cursorColor = Block.Ink,
    focusedContainerColor = Block.Paper,
    unfocusedContainerColor = Block.Paper,
)

// ===================================================================== Calculator

@Composable
fun CalcScreen(vm: MainViewModel) {
    val ui by vm.calc.collectAsState()
    val matcher = vm.graph.matcher
    Column(
        Modifier.fillMaxSize().background(Block.Page).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        ScreenTitle("Calculator") {
            if (ui.query.isNotEmpty() || ui.cp.isNotEmpty()) BlockButton("Clear", vm::clear, fill = Block.Paper, shadow = 2.dp)
        }
        OutlinedTextField(
            value = ui.query,
            onValueChange = vm::setQuery,
            label = { Text("Pokémon") },
            singleLine = true,
            colors = blockFieldColors(),
            textStyle = blockText(16.sp, 700),
            trailingIcon = if (ui.query.isNotEmpty()) {
                { IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Clear, "Clear", tint = Block.Ink) } }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (ui.speciesId == null && ui.query.isNotBlank()) {
            val hits = remember(ui.query) { matcher.search(ui.query, 8) }
            if (hits.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 6.dp, end = 4.dp, bottom = 4.dp).block(radius = 10.dp, shadow = 3.dp)) {
                    hits.forEachIndexed { i, sp ->
                        if (i > 0) HLine()
                        Text(
                            sp.name,
                            style = blockText(15.sp, 700),
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { vm.pickSpecies(sp.id) }.padding(horizontal = 14.dp, vertical = 12.dp),
                        )
                    }
                }
            } else {
                Text("No Pokémon by that name.", style = blockText(12.sp, 700, MaterialTheme.colorScheme.error), modifier = Modifier.padding(4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("CP", ui.cp, vm::setCp, Modifier.weight(1f))
            NumberField("Max HP", ui.hp, vm::setHp, Modifier.weight(1f))
            NumberField("Stardust", ui.dust, vm::setDust, Modifier.weight(1.2f))
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BlockToggle("SHADOW", ui.shadow, vm::toggleShadow)
            BlockToggle("PURIFIED", ui.purified, vm::togglePurified)
            BlockToggle("LUCKY", ui.lucky, vm::toggleLucky)
        }
        ui.speciesId?.let { id -> vm.graph.gameData.byId[id] }?.let { sp ->
            MovePicker(sp, ui.fast, ui.charged, vm::setFast, vm::setCharged)
        }
        Spacer(Modifier.height(6.dp))
        Small("Stardust is the cost shown next to POWER UP. Leave it empty if you don't know it.")
        Spacer(Modifier.height(14.dp))
        val result = ui.result
        when {
            ui.speciesId == null -> Hint("Pick a Pokémon, then enter its CP and HP.")
            ui.cp.isEmpty() -> Hint("Enter the CP shown at the top of the Pokémon's screen.")
            result != null -> {
                val names = (listOfNotNull(ui.fast, ui.charged)).mapNotNull { vm.graph.gameData.moveById[it]?.name }
                Column(Modifier.fillMaxWidth().padding(end = 5.dp, bottom = 5.dp).block(radius = 14.dp, shadow = 5.dp)) {
                    // Like the Ranks tab, the name links to the species' Pokémon GO page (1.24, the owner).
                    val uri = LocalUriHandler.current
                    Text(
                        result.species.name.uppercase(),
                        style = blockText(20.sp, 900).copy(textDecoration = TextDecoration.Underline),
                        modifier = Modifier
                            .clickable(role = Role.Button) { runCatching { uri.openUri(Pokedex.url(result.species)) } }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    HLine()
                    ResultDetails(result, ui.analysis, ui.selected, vm::select, verdict = ui.verdict, moveNames = names)
                }
                if (result.combos.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    val context = LocalContext.current
                    val exact = ui.selected ?: result.exact
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BlockButton(
                            "Copy name", { exact?.let { copyNickname(context, Nickname.of(it, ui.verdict?.primary, result.species.name)) } },
                            Modifier.weight(1f), fill = Block.Paper, enabled = exact != null,
                        )
                        BlockButton(if (ui.saved) "Saved" else "Save", vm::save, Modifier.weight(1f), enabled = !ui.saved)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MovePicker(
    sp: com.ivlens.app.core.Species,
    fast: String?,
    charged: String?,
    onFast: (String?) -> Unit,
    onCharged: (String?) -> Unit,
) {
    val gd = LocalContext.current.graph.gameData
    var open by remember(sp.id) { mutableStateOf<Boolean?>(null) }
    fun name(id: String?) = id?.let { gd.moveById[it]?.name } ?: "—"
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BlockButton("Fast: ${name(fast)}", { open = if (open == true) null else true }, Modifier.weight(1f), fill = if (open == true) Block.Yellow else Block.Paper, shadow = 2.dp)
        BlockButton("Charged: ${name(charged)}", { open = if (open == false) null else false }, Modifier.weight(1f), fill = if (open == false) Block.Yellow else Block.Paper, shadow = 2.dp)
    }
    open?.let { isFast ->
        val ids = (if (isFast) sp.fm else sp.cm + listOf("FRUSTRATION", "RETURN"))
        FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (id in ids.distinct()) {
                val m = gd.moveById[id] ?: continue
                val selected = if (isFast) id == fast else id == charged
                BlockToggle(m.name + if (id in sp.el) " ★" else "", selected, {
                    if (isFast) onFast(if (selected) null else id) else onCharged(if (selected) null else id)
                    open = null
                })
            }
        }
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = blockText(15.sp, 600, Block.Muted), modifier = Modifier.padding(4.dp))
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        colors = blockFieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = blockText(17.sp, 900),
        modifier = modifier,
    )
}

// ===================================================================== Collection

private enum class Sort(val label: String) { NEW("NEWEST"), IV("IV"), GL("GREAT"), UL("ULTRA"), NAME("NAME") }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionScreen(vm: MainViewModel) {
    val items by vm.graph.collection.items.collectAsState()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(Sort.NEW) }
    var confirm by remember { mutableStateOf<SavedMon?>(null) }
    // Picking several to delete (1.13): SELECT or a long press starts it; taps then tick entries.
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    var confirmPicked by remember { mutableStateOf(false) }
    // Entries removed meanwhile (an auto save replacing one, say) drop out of the selection.
    val chosen = remember(items, picked) { items.map { it.id }.filter { it in picked }.toSet() }
    fun stopSelecting() {
        selecting = false
        picked = emptySet()
    }
    BackHandler(enabled = selecting) { stopSelecting() }
    // Only one verdict (1.23): after an Auto save session, say, all the TRANSFERs in one list.
    var only by remember { mutableStateOf<VerdictKind?>(null) }
    val counts = remember(items) { items.groupingBy { it.verdictKind }.eachCount() }
    val showing = only?.takeIf { counts.containsKey(it.name) }
    val shown = remember(items, query, sort, showing) {
        val q = query.trim().lowercase()
        val f = items.filter { (q.isEmpty() || it.name.lowercase().contains(q)) && (showing == null || it.verdictKind == showing.name) }
        when (sort) {
            Sort.NEW -> f.sortedByDescending { it.savedAt }
            Sort.IV -> f.sortedByDescending { it.ivMax ?: -1 }
            Sort.GL -> f.sortedBy { it.greatRank ?: Int.MAX_VALUE }
            Sort.UL -> f.sortedBy { it.ultraRank ?: Int.MAX_VALUE }
            Sort.NAME -> f.sortedBy { it.name }
        }
    }
    Column(Modifier.fillMaxSize().background(Block.Page)) {
        // Stays in view above the list, so DELETE is at hand however far down the ticks are.
        if (selecting) {
            Column(Modifier.fillMaxWidth().background(Block.Paper).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${chosen.size} selected", style = blockText(16.sp, 900), modifier = Modifier.weight(1f))
                    BlockToggle("ALL", false, { picked = shown.map { it.id }.toSet() })
                    BlockToggle("NONE", false, { picked = emptySet() })
                    BlockToggle("DONE", false, { stopSelecting() })
                }
                BlockButton(
                    "Delete ${chosen.size}", { confirmPicked = true }, Modifier.fillMaxWidth().padding(top = 8.dp),
                    fill = Block.Red, enabled = chosen.isNotEmpty(),
                )
            }
            HLine()
        }
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ScreenTitle("Saved") {
                    if (items.isNotEmpty() && !selecting) BlockToggle("SELECT", false, { selecting = true })
                }
                if (items.isNotEmpty()) {
                    OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, colors = blockFieldColors(), modifier = Modifier.fillMaxWidth())
                    Text("SHOW", style = blockText(11.sp, 700, Block.Muted), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        BlockToggle("ALL ${items.size}", showing == null, { only = null })
                        for (k in VerdictKind.entries) counts[k.name]?.let { n ->
                            BlockToggle("${k.name} $n", showing == k, { only = if (showing == k) null else k })
                        }
                    }
                    Text("SORT", style = blockText(11.sp, 700, Block.Muted), modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (s in Sort.entries) BlockToggle(s.label, sort == s, { sort = s })
                    }
                }
            }
            if (items.isEmpty()) {
                item { Hint("Nothing saved yet. Tap Save on a result to keep it here, or import a saved list in Settings.") }
            }
            items(shown, key = { it.id }) { m ->
                SavedCard(
                    m,
                    onOpen = {
                        if (!selecting) vm.load(m.input, m.selected)
                        else picked = if (m.id in picked) picked - m.id else picked + m.id
                    },
                    onDelete = { confirm = m },
                    onLongPress = {
                        selecting = true
                        picked = picked + m.id
                    },
                    picked = if (selecting) m.id in chosen else null,
                )
            }
        }
    }
    if (confirmPicked) {
        AlertDialog(
            onDismissRequest = { confirmPicked = false },
            containerColor = Block.Paper,
            title = { Text("Delete ${chosen.size} Pokémon?", style = blockText(20.sp, 900)) },
            text = { Body("This removes them from your saved list.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.graph.collection.removeAll(chosen)
                    confirmPicked = false
                    stopSelecting()
                }) { Text("DELETE", style = blockText(14.sp, 900)) }
            },
            dismissButton = { TextButton(onClick = { confirmPicked = false }) { Text("CANCEL", style = blockText(14.sp, 900)) } },
        )
    }
    confirm?.let { m ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Block.Paper,
            title = { Text("Delete ${m.name}?", style = blockText(20.sp, 900)) },
            text = { Body("This removes it from your saved list.") },
            confirmButton = { TextButton(onClick = { vm.graph.collection.remove(m.id); confirm = null }) { Text("DELETE", style = blockText(14.sp, 900)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("CANCEL", style = blockText(14.sp, 900)) } },
        )
    }
}

/** [picked]: null when not selecting; otherwise whether this entry is ticked (a box replaces the bin). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SavedCard(m: SavedMon, onOpen: () -> Unit, onDelete: () -> Unit, onLongPress: () -> Unit = {}, picked: Boolean? = null) {
    val kind = m.verdictKind?.let { k -> runCatching { VerdictKind.valueOf(k) }.getOrNull() }
    Row(
        Modifier.fillMaxWidth().padding(end = 4.dp, bottom = 4.dp).height(IntrinsicSize.Min).block(radius = 10.dp, shadow = 4.dp)
            .combinedClickable(role = Role.Button, onClick = onOpen, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (fill, ink) = verdictFill(kind)
        Column(Modifier.width(92.dp).fillMaxHeight().background(fill).padding(8.dp), verticalArrangement = Arrangement.Center) {
            val word = kind?.name ?: "—"
            FitText(word, 17.sp, 900, ink)
            val iv = if (m.ivMin == null) "–" else if (m.ivMin == m.ivMax) "${m.ivMin}%" else "${m.ivMin}–${m.ivMax}%"
            Text(iv, style = blockText(12.sp, 700, ink), maxLines = 1)
        }
        VLine()
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(m.name.uppercase(), style = blockText(15.sp, 900), maxLines = 1, overflow = TextOverflow.Ellipsis)
            m.verdict?.let { Text(it, style = blockText(12.sp, 700), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            val tags = listOfNotNull(
                m.input.cp?.let { "CP $it" },
                m.levelText.takeIf { it.isNotEmpty() },
                if (m.input.shadow) "Shadow" else null,
                if (m.input.purified) "Purified" else null,
                if (m.input.lucky) "Lucky" else null,
            )
            Text(tags.joinToString(" · "), style = blockText(12.sp, 500, Block.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val ranks = listOfNotNull(m.greatRank?.let { "GL #$it" }, m.ultraRank?.let { "UL #$it" })
            Text((ranks + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(m.savedAt))).joinToString(" · "), style = blockText(12.sp, 500, Block.Muted), maxLines = 1)
        }
        if (picked == null) {
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Block.Ink) }
        } else {
            Box(
                Modifier.padding(12.dp).size(26.dp).block(radius = 6.dp, shadow = 0.dp, fill = if (picked) Block.Ink else Block.Paper),
                contentAlignment = Alignment.Center,
            ) {
                if (picked) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = Block.OnInk, modifier = Modifier.size(18.dp))
            }
        }
    }
}
