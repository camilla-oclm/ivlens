package com.ivlens.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ivlens.app.BuildConfig
import com.ivlens.app.R
import com.ivlens.app.core.CalcInput
import com.ivlens.app.core.IvCombo
import com.ivlens.app.core.Levels
import com.ivlens.app.core.Nickname
import com.ivlens.app.core.Variant
import com.ivlens.app.graph
import com.ivlens.app.scan.Appraisal
import com.ivlens.app.scan.AppraisalFit
import com.ivlens.app.scan.AppraisalReader
import com.ivlens.app.scan.ArcGeometry
import com.ivlens.app.scan.ArcReader
import com.ivlens.app.scan.MoveMemory
import com.ivlens.app.scan.ScanOutcome
import com.ivlens.app.scan.ScreenReading
import com.ivlens.app.scan.ScrollMerge
import com.ivlens.app.ui.CHIP_MAX_WIDTH
import com.ivlens.app.ui.MiniState
import com.ivlens.app.ui.NumberInput
import com.ivlens.app.ui.copyNickname
import com.ivlens.app.ui.VerdictChip
import com.ivlens.app.ui.miniFromVerdict
import com.ivlens.app.ui.EditField
import com.ivlens.app.ui.IvLensTheme
import com.ivlens.app.ui.MainActivity
import com.ivlens.app.ui.OverlayActions
import com.ivlens.app.ui.OverlayCard
import com.ivlens.app.ui.OverlayState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * Foreground service that holds the screen-capture session, shows the floating bubble and,
 * when tapped, reads the Pokémon on screen and shows the result card over the game.
 */
class OverlayService : LifecycleService(), SavedStateRegistryOwner, OverlayActions {

    companion object {
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        private const val ACTION_STOP = "com.ivlens.app.STOP"
        private const val CHANNEL = "bubble"
        private const val NOTIFICATION_ID = 7
        /** Tallest the chip gets (two-line note), shadow included; measured about 50 dp on a phone. */
        private const val CHIP_HEIGHT_DP = 56f

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = Intent(context, OverlayService::class.java)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }
    }

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private val main = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var projection: MediaProjection? = null
    private var capturer: ScreenCapturer? = null
    private var reader: ScreenReader? = null
    private var bubble: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var card: ComposeView? = null
    private val state = MutableStateFlow<OverlayState?>(null)
    private var scanning = false

    // Auto-scan: watch the panel, read it once it settles, show the small verdict chip.
    private var autoJob: Job? = null
    private var prevSample: FrameSample? = null
    private var scannedSample: FrameSample? = null
    private var movingSince = 0L
    private var lastAutoScan = 0L
    private var mini: ComposeView? = null
    private var miniParams: WindowManager.LayoutParams? = null
    private var bubbleShift = Point(0, 0)
    private var chipShift = Point(0, 0)
    private val miniState = MutableStateFlow<MiniState?>(null)
    /** Full-card state behind the chip, opened when the chip is tapped. */
    private var miniFull: OverlayState? = null
    private var dismissedKey: String? = null
    /** Back-off when an animated screen keeps reading as the same Pokémon. */
    private var sameStreak = 0
    private var shownWeight: Double? = null
    private var retryAt = 0L
    private var retries = 0

    // Appraise screen: exact IVs remembered per Pokémon for this session.
    private val appraised = mutableMapOf<String, IvCombo>()
    // Moves per Pokémon for this session, so swiping away and back doesn't ask for them again.
    private val moveMemory = MoveMemory()
    // Shadow / Lucky / Purified per Pokémon, from reads that showed its stardust cost.
    private val seenVariant = mutableMapOf<String, Variant>()
    private var lastAppraisalTry = 0L
    /** Last time a Pokémon's page (plain, or behind the Appraise screen) was on screen. */
    private var pageAliveAt = 0L
    /** Bars seen on the last tick; they're acted on once two frames in a row agree (the fill animates in). */
    private var lastBars: Appraisal? = null
    /** Bars already matched to a Pokémon, so a still Appraise screen isn't read over and over. */
    private var handledBars: Appraisal? = null

    /** What each read saw, for `adb logcat -s IvLens`; only in the debug build, with "Debug log" on in Settings. */
    private fun log(msg: String) {
        if (BuildConfig.DEBUG && graph.settings.state.value.debugLog) Log.d("IvLens", msg)
    }

    private fun OverlayState.named(): OverlayState {
        val gd = graph.gameData
        return copy(moveNames = (listOfNotNull(input.fast) + input.charged).mapNotNull { gd.moveById[it]?.name })
    }

    private fun keyOf(s: OverlayState) = "${s.input.speciesId}|${s.input.cp}|${s.input.hp}"

    /** " [Shadow]", and what said LUCKY, for the debug log; empty for a plain Pokémon. */
    private fun flags(input: CalcInput, reading: ScreenReading?): String =
        (if (input.variant != Variant.NORMAL) " [${input.variant.label}]" else "") +
            (reading?.luckyText?.let { " LUCKY from $it" } ?: "")

    /**
     * Behind the Appraise screen the stardust cost and the PURIFY button are hidden, and a number
     * read there can be anything (the year in "caught on 10/18/2026"). So the stardust isn't used:
     * the kind of Pokémon comes from an earlier read with the cost, else from PURIFY or LUCKY on
     * screen, else it's plain.
     * - A Shadow Rhyhorn (13/2/2, 2026-10-20) swiped to there said TRANSFER instead of RAIDS.
     * - A Nymble there came out Purified · Lucky from a stray number, which started the Lucky loop.
     */
    private suspend fun behindAppraise(o: ScanOutcome): ScanOutcome {
        val i = o.input
        val r = o.reading
        val v = seenVariant["${i.speciesId}|${i.cp}|${i.hp}"] ?: Variant(shadow = r.shadow, lucky = r.lucky && !r.shadow)
        if (i.dust == null && v == i.variant) return o
        val input = i.copy(dust = null).withVariant(v)
        val g = graph
        return o.copy(input = input, result = withContext(Dispatchers.Default) { g.calculate(input) })
    }

    /** Same Pokémon with moves attached, verdict recomputed. */
    private suspend fun withMoves(s: OverlayState, fast: String?, charged: List<String>): OverlayState {
        val input = s.input.copy(fast = fast, charged = charged)
        val r = s.result?.copy(input = input)
        val g = graph
        val v = withContext(Dispatchers.Default) { r?.takeIf { it.combos.isNotEmpty() }?.let { g.judge(it, s.selected) } }
        return s.copy(input = input, result = r, verdict = v ?: s.verdict).named()
    }

    /**
     * Pins [s] to the IVs from the Appraise screen; null when they don't fit its CP and HP. Bars
     * that fit another form of it (Hisuian Qwilfish for Qwilfish) switch the state to that form.
     */
    private suspend fun applyAppraisal(s: OverlayState, ap: Appraisal): OverlayState? {
        val g = graph
        return withContext(Dispatchers.Default) {
            val (result, combo) = AppraisalFit.fitForms(g.calc, s.input, s.result, ap, g.settings.state.value.powerMaxIndex, s.candidates)
                ?: return@withContext null
            // The CP comes from the result too: with it hidden or garbled, the bars worked it out.
            val input = s.input.copy(speciesId = result.species.id, cp = result.input.cp)
            for (k in AppraisalFit.memoryKeys(s.input, s.weightKg)) appraised[k] = combo
            val next = s.copy(
                input = input, result = result.copy(input = input), selected = combo, analysis = g.analyze(result, combo),
                verdict = g.judge(result.copy(input = input), combo), appraised = true, bars = null, problem = null, editing = null,
            )
            for (k in AppraisalFit.memoryKeys(next.input, next.weightKg)) appraised[k] = combo
            next
        }?.named()
    }

    /** Applies an appraisal and moves read earlier this session to a fresh read of the same Pokémon. */
    private suspend fun remembered(s: OverlayState): OverlayState {
        val keys = AppraisalFit.recallKeys(s.input, s.weightKg, cpFits = s.problem != ScanOutcome.Problem.NO_MATCH)
        val c = keys.firstNotNullOfOrNull { appraised[it] }
        return withSeenMoves(c?.let { applyAppraisal(s, Appraisal(it.atk, it.def, it.sta)) } ?: s)
    }

    /** Keeps the moves [s] shows, and fills in the rest of those seen earlier this session for it. */
    private suspend fun withSeenMoves(s: OverlayState): OverlayState {
        moveMemory.note(s.input, s.weightKg)
        val (fast, charged) = moveMemory.recall(s.input, s.weightKg) ?: return s
        log("moves from earlier: ${s.input.speciesId} CP ${s.input.cp} HP ${s.input.hp}: $fast + ${charged.joinToString(", ")}")
        return withMoves(s, fast, charged)
    }

    /**
     * Which Pokémon the bars belong to: the one on the chip, unless the page behind the Appraise
     * screen shows a different one (the game's arrows step to the next Pokémon).
     */
    private suspend fun appraisedState(outcome: ScanOutcome, ap: Appraisal, old: OverlayState?): OverlayState? {
        val i = outcome.input
        val screen = if (i.speciesId != null && i.cp != null && i.hp != null) buildState(behindAppraise(outcome)) else null
        val sameAsOld = old != null && (screen == null ||
            (screen.input.cp == old.input.cp && screen.input.hp == old.input.hp && screen.candidates.any { it.id == old.input.speciesId }))
        return (if (sameAsOld) old?.let { applyAppraisal(it, ap) } else null)
            ?: screen?.let { applyAppraisal(it, ap) }
            ?: old?.takeIf { !sameAsOld && screen == null }?.let { applyAppraisal(it, ap) }
    }

    private suspend fun showAppraisal(outcome: ScanOutcome, ap: Appraisal) {
        handledBars = ap
        val next = appraisedState(outcome, ap, miniFull)?.let { withSeenMoves(it) }
        if (next == null) {
            // Bars that fit neither the page behind nor the chip's Pokémon: say so, rather than nothing.
            val i = outcome.input
            val base = (if (i.speciesId != null && i.cp != null && i.hp != null) buildState(outcome) else null) ?: miniFull
            log("bars ${ap.ivText} don't fit ${base?.input?.let { "${it.speciesId} CP ${it.cp} HP ${it.hp}" } ?: "any Pokémon read"}")
            miniFull = base?.copy(bars = ap, appraised = false)
            miniState.value = miniFull?.let { miniFor(it) }
                ?: MiniState(null, "CHECK", "BARS", "Bars ${ap.ivText}", "Couldn't tell which Pokémon. Open its page first.")
            dismissedKey = null
            showMini()
            return
        }
        log("bars ${ap.ivText} fit ${next.input.speciesId} CP ${next.input.cp} HP ${next.input.hp}" + flags(next.input, outcome.reading))
        miniFull = next
        shownWeight = outcome.reading.weightKg ?: shownWeight
        dismissedKey = null
        miniState.value = miniFor(next)
        showMini()
        autoSave(next)
    }

    /** With "Auto save" on, keeps each Pokémon the chip reads in Saved, updated as more comes in. */
    private suspend fun autoSave(s: OverlayState) {
        val g = graph
        if (!g.settings.state.value.autoSave || s.problem != null || (s.bars != null && !s.appraised)) return
        val r = s.result?.takeIf { it.combos.isNotEmpty() } ?: return
        val a = s.analysis ?: return
        val changed = withContext(Dispatchers.Default) { g.save(r, s.selected, a, auto = true) }
        if (changed) log("auto save: ${s.input.speciesId} CP ${s.input.cp} HP ${s.input.hp}" + flags(s.input, null))
    }

    /**
     * Off the white Pokémon page: the Appraise screen (tinted page, animated team leader), a menu,
     * or somewhere else entirely. One read tells which; the chip stays while the Pokémon's page is
     * still behind, and picks up the exact IVs once the bars show.
     */
    private suspend fun tryAppraisal(rects: List<Rect>) {
        val rd = reader ?: return
        val bmp = capturer?.grab() ?: return
        scanning = true
        try {
            val outcome = rd.read(bmp, null, rects)
            val r = outcome.reading
            val behind = outcome.appraising || r.pokemonPage || r.hp.isNotEmpty() || r.cp.isNotEmpty()
            val ap = outcome.appraisal
            when {
                ap != null -> {
                    pageAliveAt = SystemClock.uptimeMillis()
                    showAppraisal(outcome, ap)
                }
                behind -> {
                    pageAliveAt = SystemClock.uptimeMillis()
                    miniState.value = miniState.value?.copy(busy = false)
                }
                else -> {
                    // Map, battle...: stop looking until a Pokémon's page shows up again.
                    log("no Pokémon page behind: chip hidden")
                    pageAliveAt = 0L
                    hideMini()
                    miniState.value = null
                }
            }
        } catch (e: Exception) {
            // Leave things as they are.
        } finally {
            bmp.recycle()
            scanning = false
        }
    }

    /**
     * Looks for the Appraise screen's bars in the newest frame, by their look alone (no text
     * reading, so it's cheap enough for every tick). True while they're on screen. Once two frames
     * in a row agree (the fill animates in), the bars are matched to the Pokémon once.
     *
     * New bars get up to [quickLooks] second looks, 150 ms apart, rather than waiting for the next
     * tick: a quick tap can close the screen within half a second of the bars filling (a Minun,
     * 2026-10-17, was seen once and gone at the next tick).
     */
    private suspend fun checkBars(rects: List<Rect>, quickLooks: Int = 3): Boolean {
        val cap = capturer ?: return false
        val w = cap.width
        val bars = withContext(Dispatchers.Default) {
            val (l, t, r, b) = AppraisalReader.region(w, cap.height)
            cap.region(l, t, r, b, rects)?.let { AppraisalReader.findIn(it, r - l, b - t, w) }
        }
        if (bars == null) {
            if (lastBars != null) log("bars gone")
            lastBars = null
            return false
        }
        pageAliveAt = SystemClock.uptimeMillis()
        val ap = bars.appraisal
        if (ap != lastBars) log("bars ${ap?.ivText ?: "still filling"}")
        val steady = ap != null && ap == lastBars
        lastBars = ap
        if (ap == null || !steady || ap == handledBars) {
            if (ap == null) miniState.value = miniState.value?.copy(busy = true)
            if (ap != null && !steady && ap != handledBars && quickLooks > 0) {
                delay(150)
                if (scanning || card != null) return true
                return checkBars(rects, quickLooks - 1)
            }
            return true
        }
        matchBars(ap, rects)
        return true
    }

    /** Reads the page behind the Appraise screen once, to tell whose bars these are. */
    private suspend fun matchBars(ap: Appraisal, rects: List<Rect>) {
        val rd = reader ?: return
        val bmp = capturer?.grab() ?: return
        scanning = true
        try {
            showAppraisal(rd.read(bmp, null, rects), ap)
        } catch (e: Exception) {
            log("bars ${ap.ivText}: reading the page behind failed: $e")
        } finally {
            bmp.recycle()
            scanning = false
        }
    }

    /**
     * The level arc didn't fit CP, HP and stardust (a sparkle or a bright background caught at the
     * wrong moment): read it again from a fresh frame or two before settling for a range.
     */
    private suspend fun recheckArc(outcome: ScanOutcome, rects: List<Rect>): ScanOutcome {
        val r = outcome.result ?: return outcome
        val hint = outcome.input.levelHint
        if (outcome.problem != null || r.usedArc || hint.isNullOrEmpty() || outcome.input.dust == null || r.combos.size <= 1) return outcome
        val rd = reader ?: return outcome
        val g = graph
        repeat(2) {
            delay(180)
            val bmp = capturer?.grab() ?: return outcome
            val arc = try {
                rd.readArc(bmp, loadGeometry(), rects)
            } finally {
                bmp.recycle()
            } ?: return@repeat
            val near = ArcReader.levelsNear(g.gameData, arc, g.resolver.maxLevelIndex(g.settings.state.value.trainerLevel))
            val input = outcome.input.copy(levelHint = near)
            val again = withContext(Dispatchers.Default) { g.calculate(input) }
            if (again != null && again.usedArc && again.combos.isNotEmpty()) {
                return outcome.copy(arc = arc, input = input, result = again)
            }
        }
        return outcome
    }

    override fun onCreate() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_bubble), NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (projection != null) return START_NOT_STICKY

        try {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.capture_failed), Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_DATA, Intent::class.java) }
        if (data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val p = mpm.getMediaProjection(code, data) ?: throw IllegalStateException("No projection")
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    main.post { stopSelf() }
                }
            }, main)
            projection = p
            capturer = ScreenCapturer(this, p)
            reader = ScreenReader(graph)
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.capture_failed), Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }
        showBubble()
        _running.value = true
        lifecycleScope.launch { graph.settings.state.collect { setAuto(it.autoScan) } }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        _running.value = false
        autoJob?.cancel()
        hideMini()
        hideCard()
        bubble?.let { runCatching { wm.removeView(it) } }
        bubble = null
        capturer?.release()
        capturer = null
        runCatching { projection?.stop() }
        projection = null
        reader?.close()
        super.onDestroy()
    }

    private fun notification(): Notification {
        val stop = PendingIntent.getService(
            this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            // Bold Block blue for the icon in the notification shade, where plain grey was barely visible.
            .setColor(0xFF3D5AFE.toInt())
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.stop), stop)
            .setOngoing(true)
            .build()
    }

    private fun dp(v: Float): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    // ---------------------------------------------------------------- bubble

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        val size = dp(54f)
        val shadow = dp(3f)
        // Bold Block: yellow rounded square, black outline, hard black shadow.
        val face = GradientDrawable().apply {
            cornerRadius = dp(14f).toFloat()
            setColor(0xFFFFD400.toInt())
            setStroke(dp(2f), 0xFF111111.toInt())
        }
        val drop = GradientDrawable().apply {
            cornerRadius = dp(14f).toFloat()
            setColor(0xFF111111.toInt())
        }
        val view = FrameLayout(this).apply {
            background = LayerDrawable(arrayOf(drop, face)).apply {
                setLayerInset(0, shadow, shadow, 0, 0)
                setLayerInset(1, 0, 0, shadow, shadow)
            }
            contentDescription = getString(R.string.bubble_desc)
            addView(
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_bubble)
                    setColorFilter(0xFF111111.toInt(), PorterDuff.Mode.SRC_IN)
                },
                FrameLayout.LayoutParams(dp(34f), dp(34f), Gravity.CENTER).apply { rightMargin = shadow; bottomMargin = shadow },
            )
        }
        val dm = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dm.widthPixels - size - dp(6f)
            y = (dm.heightPixels * 0.30).toInt()
        }

        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longPressed = false
        val longPress = Runnable {
            longPressed = true
            Toast.makeText(this, getString(R.string.bubble_stopped), Toast.LENGTH_SHORT).show()
            stopSelf()
        }
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false; longPressed = false
                    main.postDelayed(longPress, 900)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        main.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        params.x = (startX + dx).toInt()
                        params.y = (startY + dy).toInt()
                        runCatching { wm.updateViewLayout(view, params) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    main.removeCallbacks(longPress)
                    if (!dragging && !longPressed && e.actionMasked == MotionEvent.ACTION_UP) scan()
                }
            }
            true
        }
        wm.addView(view, params)
        bubble = view
        bubbleParams = params
    }

    // ---------------------------------------------------------------- scanning

    private fun scan() {
        if (scanning) return
        val cap = capturer ?: return
        val rd = reader ?: return
        scanning = true
        hideCard()
        hideMini()
        bubble?.visibility = View.INVISIBLE
        lifecycleScope.launch {
            // Give the mirror a moment to deliver a frame without the bubble or card in it.
            delay(320)
            val bmp = cap.grab()
            bubble?.visibility = View.VISIBLE
            if (bmp == null) {
                scanning = false
                Toast.makeText(this@OverlayService, getString(R.string.no_frame), Toast.LENGTH_SHORT).show()
                return@launch
            }
            bubble?.alpha = 0.5f
            try {
                var outcome = rd.read(bmp, loadGeometry())
                outcome.arc?.let { if (it.coverage >= ArcReader.CONFIDENT_COVERAGE) saveGeometry(it.geometry) }
                val ap = outcome.appraisal
                val applied = if (ap != null) appraisedState(outcome, ap, state.value ?: miniFull)?.let { withSeenMoves(it) } else null
                if (applied != null) {
                    state.value = applied
                    if (miniFull?.let { keyOf(it) } == keyOf(applied)) miniFull = applied
                } else {
                    outcome = recheckArc(outcome, emptyList())
                    state.value = remembered(buildState(outcome))
                }
                showCard()
            } catch (e: Exception) {
                Toast.makeText(this@OverlayService, getString(R.string.scan_failed), Toast.LENGTH_SHORT).show()
            } finally {
                bmp.recycle()
                bubble?.alpha = 1f
                scanning = false
            }
        }
    }

    /** Full-card state for a scan, with PvP analysis and verdict. */
    private suspend fun buildState(outcome: ScanOutcome): OverlayState {
        val g = graph
        val base = OverlayState(
            candidates = outcome.candidates,
            input = outcome.input,
            result = outcome.result,
            analysis = null,
            selected = null,
            problem = outcome.problem,
            editing = if (outcome.problem == ScanOutcome.Problem.NO_CP) EditField.CP else null,
            weightKg = outcome.reading.weightKg,
        )
        return withContext(Dispatchers.Default) {
            val r = outcome.result
            if (r != null && r.combos.isNotEmpty()) base.copy(analysis = g.analyze(r, null), verdict = g.judge(r, null)) else base
        }.named()
    }

    // ---------------------------------------------------------------- auto-scan

    private fun setAuto(on: Boolean) {
        if (on && autoJob == null) {
            ensureMiniParams()
            prevSample = null
            scannedSample = null
            autoJob = lifecycleScope.launch {
                while (isActive) {
                    delay(400)
                    runCatching { tick() }
                }
            }
        } else if (!on) {
            autoJob?.cancel()
            autoJob = null
            hideMini()
            miniState.value = null
            miniFull = null
        }
    }

    /** Where Android really put an overlay, minus where its params asked; the last known when hidden. */
    private fun shiftOf(v: View?, p: WindowManager.LayoutParams?, last: Point): Point {
        if (v == null || p == null || !v.isAttachedToWindow || v.width == 0) return last
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return Point(loc[0] - p.x, loc[1] - p.y)
    }

    /** Screen rectangles of our own overlays, which the readers must ignore. */
    private fun overlayRects(): List<Rect> {
        val out = mutableListOf<Rect>()
        // Overlays can be laid out below the status bar, while the capture counts from the top of
        // the screen. Before 1.12 the chip's box sat too high: its LUCKY label fell below it and was
        // read as the next Pokémon's, so every Pokémon after a Lucky one came out Lucky.
        bubbleShift = shiftOf(bubble, bubbleParams, bubbleShift)
        val shift = shiftOf(mini, miniParams, chipShift)
        if (shift != chipShift) log("chip sits ${shift.x},${shift.y} px from where it was placed")
        chipShift = shift
        bubbleParams?.let { out += Rect(it.x, it.y, it.x + it.width, it.y + it.height).apply { offset(bubbleShift.x, bubbleShift.y) } }
        // Reserved whether or not the chip is showing, so fingerprints stay comparable.
        miniParams?.let {
            out += Rect(it.x - dp(4f), it.y - dp(4f), it.x + chipWidthPx() + dp(6f), it.y + dp(CHIP_HEIGHT_DP + 6f))
                .apply { offset(chipShift.x, chipShift.y) }
        }
        return out
    }

    /** Chip width: up to 244 dp, and narrow enough to sit inside the level arc. */
    private fun chipWidthPx(): Int {
        val w = resources.displayMetrics.widthPixels
        return minOf(dp(CHIP_MAX_WIDTH.value), (w * 0.62).toInt())
    }

    private suspend fun tick() {
        if (scanning || card != null) return
        val cap = capturer ?: return
        val rects = overlayRects()
        val s = withContext(Dispatchers.Default) { cap.sample(rects) } ?: return
        val prev = prevSample
        prevSample = s
        val now = SystemClock.uptimeMillis()
        if (s.whiteFraction < 0.55f) {
            // Not the plain white page. The Appraise screen tints it and brings in an animated team
            // leader, so it never holds still: look for its bars on every tick, and check on a
            // timer whether the Pokémon's page is still seen behind. The bars are looked for even
            // after that gives up, so a slow walk through the leader's lines still gets read.
            scannedSample = null
            movingSince = 0L
            sameStreak = 0
            if (checkBars(rects)) return
            if (now - pageAliveAt < 15_000) {
                if (now - lastAppraisalTry > 1000) {
                    lastAppraisalTry = now
                    tryAppraisal(rects)
                }
            } else {
                if (mini != null) log("off the Pokémon's page for 15 s: chip hidden")
                hideMini()
                miniState.value = null
            }
            return
        }
        pageAliveAt = now
        lastBars = null
        handledBars = null
        val settled = prev != null && prev.diff(s) <= 6
        if (settled) movingSince = 0L else if (movingSince == 0L) movingSince = now
        val ref = scannedSample
        // A big change (scrolled, or swiped to the next Pokémon) gets a fresh read straight away.
        if (ref != null && ref.diff(s) > 250) sameStreak = 0
        if (ref != null && ref.diff(s) < 16) {
            // Unchanged panel, but the last read missed the CP: try again a few times.
            if (retryAt != 0L && now >= retryAt && retries < 4) {
                retries++
                retryAt = 0L
                lastAutoScan = now
                autoScan(s, rects)
            }
            return
        }
        retries = 0
        // Read once the panel holds still; if something on it keeps animating, read anyway.
        val stuck = movingSince != 0L && now - movingSince > 2500
        val gap = minOf(8000L, 1200L * (1 + sameStreak))
        if ((settled || stuck) && now - lastAutoScan > gap) {
            lastAutoScan = now
            autoScan(s, rects)
        }
    }

    private suspend fun autoScan(sample: FrameSample, rects: List<Rect>) {
        val rd = reader ?: return
        val bmp = capturer?.grab() ?: return
        scanning = true
        scannedSample = sample
        miniState.value?.let { miniState.value = it.copy(busy = true) }
        try {
            var outcome = rd.read(bmp, loadGeometry(), rects)
            outcome.arc?.let { if (it.coverage >= ArcReader.CONFIDENT_COVERAGE) saveGeometry(it.geometry) }
            val reading = outcome.reading
            log("auto: ${outcome.input.speciesId} CP ${outcome.input.cp} HP ${outcome.input.hp} dust ${outcome.input.dust}: " +
                "${outcome.result?.combos?.size ?: 0} combos${outcome.problem?.let { " ($it)" } ?: ""}" + flags(outcome.input, reading))
            val old = miniFull
            outcome.appraisal?.let { ap ->
                showAppraisal(outcome, ap)
                return
            }
            if (outcome.appraising) {
                // Appraise screen, bars still filling: keep the chip and look again shortly.
                scannedSample = null
                miniState.value = miniState.value?.copy(busy = false)
                return
            }
            // Scrolled down the same Pokémon (CP, then name and HP, off the top): keep what we
            // know and pick up its moves.
            if (old != null && ScrollMerge.samePokemonScrolled(old.input, shownWeight, outcome)) {
                val sp = old.input.speciesId?.let { graph.gameData.byId[it] }
                val (f, ch) = graph.moveMatcher.read(reading.moves, sp)
                if (f != null && (f.id != old.input.fast || ch.map { it.id } != old.input.charged)) {
                    val next = withMoves(old, f.id, ch.map { it.id })
                    if (ScrollMerge.confirms(old.input, old.weightKg, outcome)) moveMemory.note(next.input, next.weightKg)
                    miniFull = next
                    miniState.value = miniFor(next)
                    if (mini == null && dismissedKey != keyOf(next)) showMini()
                    autoSave(next)
                } else {
                    miniState.value = miniState.value?.copy(busy = false)
                }
                return
            }
            if (reading.hp.isEmpty() || outcome.problem == ScanOutcome.Problem.NO_SPECIES) {
                hideMini()
                miniState.value = null
                return
            }
            outcome = recheckArc(outcome, rects)
            var full = remembered(buildState(outcome))
            shownWeight = reading.weightKg
            val key = keyOf(full)
            val oldKey = old?.let { keyOf(it) }
            // Moves read earlier while scrolled still belong to this Pokémon.
            if (old != null && oldKey == key && full.input.fast == null && old.input.fast != null) {
                full = withMoves(full, old.input.fast, old.input.charged)
            }
            // Same Pokémon read again (e.g. after scrolling): keep the sharper result.
            if (old != null && oldKey == key) sameStreak++ else sameStreak = 0
            // A banner or the Pokémon itself may be covering the CP; look again shortly.
            retryAt = if (full.problem != null) SystemClock.uptimeMillis() + 2000 else 0L
            if (full.problem == null) retries = 0
            val gainedMoves = old != null && old.input.fast == null && full.input.fast != null
            // A read with the stardust cost settles Shadow / Lucky / Purified, so it replaces a
            // guess made without it (behind the Appraise screen) even when it's no sharper.
            val settledKind = full.input.dust != null && full.result?.combos?.isNotEmpty() == true
            if (settledKind) seenVariant[key] = full.input.variant
            val kindChanged = settledKind && old != null && old.input.variant != full.input.variant
            if (old != null && oldKey == key && !gainedMoves && !kindChanged &&
                (old.result?.combos?.size ?: 99) <= (full.result?.combos?.size ?: 99) && mini != null
            ) {
                miniState.value = miniState.value?.copy(busy = false)
                return
            }
            if (key == dismissedKey) {
                miniState.value = null
                return
            }
            dismissedKey = null
            miniFull = full
            miniState.value = miniFor(full)
            showMini()
            autoSave(full)
        } catch (e: Exception) {
            miniState.value = miniState.value?.copy(busy = false)
        } finally {
            bmp.recycle()
            scanning = false
        }
    }

    private fun miniFor(s: OverlayState): MiniState {
        val name = s.result?.species?.name ?: s.candidates.firstOrNull { it.id == s.input.speciesId }?.name ?: "Pokémon"
        val r = s.result
        val v = s.verdict
        return when {
            s.bars != null && !s.appraised -> MiniState(null, "CHECK", "BARS", name, "Bars ${s.bars.ivText} don't fit its CP and HP. Tap to check.")
            s.problem == ScanOutcome.Problem.NO_CP -> MiniState(null, "CP?", "HIDDEN", name, "Tap to type the CP")
            r == null || r.combos.isEmpty() || v == null -> MiniState(null, "CHECK", "NUMBERS", name, "Tap to fix")
            else -> {
                val shown = s.selected ?: r.exact
                val iv = shown?.let { "${it.percent}%" } ?: r.ivRangeText()
                val note = when {
                    s.appraised -> " exact"
                    !v.confident -> " · likely"
                    else -> ""
                }
                miniFromVerdict(v, "$name · $iv$note", appraise = shown == null && !s.appraised)
            }
        }
    }

    /**
     * Default chip spot: centred over the Pokémon, inside the level arc, so it never covers the
     * CP, the arc, the numbers below or the moves once scrolled. Pokémon GO lays this area out
     * by screen width, so width units hold across phones.
     */
    private fun defaultMiniPosition(): Pair<Int, Int> {
        val w = resources.displayMetrics.widthPixels
        val g = loadGeometry() ?: ArcReader.DEFAULT_GEOMETRY
        val top = ((g.cyRatio - 0.47 * g.rRatio) * w).toInt()
        return (w - chipWidthPx()) / 2 to top
    }

    private fun ensureMiniParams(): WindowManager.LayoutParams {
        miniParams?.let { return it }
        val prefs = getSharedPreferences("mini", MODE_PRIVATE)
        val (dx, dy) = defaultMiniPosition()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // New keys: positions saved by 1.2 (bottom-left) aren't carried over.
            x = prefs.getInt("x2", dx)
            y = prefs.getInt("y2", dy)
        }
        miniParams = params
        return params
    }

    private fun showMini() {
        if (mini != null) return
        val params = ensureMiniParams()
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                IvLensTheme {
                    val s by miniState.collectAsState()
                    val width = with(LocalDensity.current) { chipWidthPx().toDp() }
                    s?.let {
                        VerdictChip(
                            it,
                            onTap = ::openFullFromMini,
                            onDismiss = ::dismissMini,
                            onDrag = ::moveMini,
                            onDragEnd = ::saveMiniPosition,
                            width = width,
                        )
                    }
                }
            }
        }
        wm.addView(view, params)
        mini = view
    }

    private fun hideMini() {
        mini?.let { runCatching { wm.removeView(it) } }
        mini = null
    }

    private fun moveMini(dx: Float, dy: Float) {
        val p = miniParams ?: return
        val v = mini ?: return
        p.x += dx.toInt()
        p.y += dy.toInt()
        runCatching { wm.updateViewLayout(v, p) }
    }

    private fun saveMiniPosition() {
        val p = miniParams ?: return
        getSharedPreferences("mini", MODE_PRIVATE).edit().putInt("x2", p.x).putInt("y2", p.y).apply()
        // The ignored area moved, so fingerprints taken before aren't comparable.
        prevSample = null
        scannedSample = null
    }

    private fun dismissMini() {
        miniFull?.let { dismissedKey = "${it.input.speciesId}|${it.input.cp}|${it.input.hp}" }
        hideMini()
        miniState.value = null
    }

    private fun openFullFromMini() {
        val s = miniFull ?: return
        hideMini()
        // Bars that didn't fit open with the bar editor, ready to fix.
        state.value = s.bars?.takeIf { !s.appraised }?.let { b -> s.copy(barInput = listOf(b.atk, b.def, b.sta)) } ?: s
        showCard()
    }

    private fun loadGeometry(): ArcGeometry? {
        val p = getSharedPreferences("arc", MODE_PRIVATE)
        val key = "${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}"
        if (p.getString("key", null) != key) return null
        val r = p.getFloat("r", 0f).toDouble()
        val cy = p.getFloat("cy", 0f).toDouble()
        return if (r > 0) ArcGeometry(r, cy) else null
    }

    private fun saveGeometry(g: ArcGeometry) {
        val key = "${resources.displayMetrics.widthPixels}x${resources.displayMetrics.heightPixels}"
        getSharedPreferences("arc", MODE_PRIVATE).edit()
            .putString("key", key).putFloat("r", g.rRatio.toFloat()).putFloat("cy", g.cyRatio.toFloat()).apply()
    }

    // ---------------------------------------------------------------- result card

    private fun showCard() {
        if (card != null) return
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                IvLensTheme {
                    val s by state.collectAsState()
                    s?.let { OverlayCard(it, this@OverlayService) }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM
            y = dp(20f)
        }
        wm.addView(view, params)
        card = view
    }

    private fun hideCard() {
        card?.let { runCatching { wm.removeView(it) } }
        card = null
    }

    private fun update(f: (OverlayState) -> OverlayState) {
        val s = state.value ?: return
        state.value = f(s)
    }

    /** Recalculate after the user changes an input. */
    private fun recompute(input: CalcInput) {
        val s = state.value ?: return
        val g = graph
        state.value = s.copy(input = input, busy = true, saved = false)
        lifecycleScope.launch {
            val next = withContext(Dispatchers.Default) {
                val r = g.calculate(input)
                val sel = s.selected?.takeIf { r?.combos?.contains(it) == true }
                val ok = r?.takeIf { it.combos.isNotEmpty() }
                val a = ok?.let { g.analyze(it, sel) }
                s.copy(input = input, result = r, analysis = a, verdict = ok?.let { g.judge(it, sel) }, selected = sel, busy = false, saved = false,
                    appraised = s.appraised && sel != null,
                    problem = if (input.cp == null) s.problem else null).named()
            }
            // Bars that didn't fit may fit the corrected numbers.
            state.value = s.bars?.let { applyAppraisal(next, it) } ?: next
        }
    }

    override fun close() {
        val s = state.value
        hideCard()
        state.value = null
        // Bring the chip back, reflecting any fixes made on the card.
        if (autoJob != null && s != null && s.input.hp != null && s.input.speciesId != null) {
            miniFull = s
            miniState.value = miniFor(s)
            showMini()
        }
    }

    override fun rescan() = scan()

    override fun toggleSpeciesList() = update { it.copy(showSpecies = !it.showSpecies) }

    override fun pickSpecies(id: String) {
        val s = state.value ?: return
        state.value = s.copy(showSpecies = false, selected = null, appraised = false)
        recompute(s.input.copy(speciesId = id))
    }

    override fun toggleShadow() {
        val i = state.value?.input ?: return
        recompute(i.copy(shadow = !i.shadow, purified = false, lucky = if (!i.shadow) false else i.lucky))
    }

    override fun togglePurified() {
        val i = state.value?.input ?: return
        recompute(i.copy(purified = !i.purified, shadow = false))
    }

    override fun toggleLucky() {
        val i = state.value?.input ?: return
        recompute(i.copy(lucky = !i.lucky, shadow = if (!i.lucky) false else i.shadow))
    }

    override fun edit(field: EditField) {
        val s = state.value ?: return
        if (s.editing == field) {
            state.value = s.copy(editing = null, buffer = "")
            return
        }
        val current = when (field) {
            EditField.CP -> s.input.cp
            EditField.HP -> s.input.hp
            EditField.DUST -> s.input.dust
        }
        val text = current?.toString() ?: ""
        state.value = s.copy(editing = field, buffer = text, cursor = text.length)
    }

    override fun key(c: Char) {
        val s = state.value ?: return
        val field = s.editing ?: return
        when (c) {
            'x' -> state.value = s.copy(editing = null, buffer = "")
            '<' -> state.value = s.withNumber(NumberInput(s.buffer, s.cursor).backspace())
            '←' -> state.value = s.withNumber(NumberInput(s.buffer, s.cursor).left())
            '→' -> state.value = s.withNumber(NumberInput(s.buffer, s.cursor).right())
            '✓' -> {
                val v = s.buffer.toIntOrNull()
                val input = when (field) {
                    EditField.CP -> s.input.copy(cp = v?.takeIf { it in 10..9999 })
                    EditField.HP -> s.input.copy(hp = v?.takeIf { it in 10..999 })
                    EditField.DUST -> s.input.copy(dust = v?.takeIf { it > 0 })
                }
                state.value = s.copy(editing = null, buffer = "")
                recompute(input)
            }
            else -> state.value = s.withNumber(NumberInput(s.buffer, s.cursor).type(c))
        }
    }

    override fun moveCursor(at: Int) = update { it.withNumber(NumberInput(it.buffer, it.cursor).moveTo(at)) }

    private fun OverlayState.withNumber(n: NumberInput) = copy(buffer = n.text, cursor = n.cursor)

    override fun select(combo: IvCombo?) {
        val s = state.value ?: return
        val r = s.result ?: return
        state.value = s.copy(selected = combo, appraised = s.appraised && combo == s.selected)
        val g = graph
        lifecycleScope.launch {
            val (a, v) = withContext(Dispatchers.Default) { g.analyze(r, combo) to g.judge(r, combo) }
            update { if (it.selected == combo) it.copy(analysis = a, verdict = v) else it }
        }
    }

    override fun toggleBars() {
        val s = state.value ?: return
        if (s.barInput != null) {
            state.value = s.copy(barInput = null)
            return
        }
        val from = s.bars ?: s.selected?.takeIf { s.appraised }?.let { Appraisal(it.atk, it.def, it.sta) }
        state.value = s.copy(barInput = from?.let { listOf(it.atk, it.def, it.sta) } ?: listOf(null, null, null), editing = null)
    }

    override fun setBar(stat: Int, value: Int) {
        val s = state.value ?: return
        val input = (s.barInput ?: listOf(null, null, null)).toMutableList().also { it[stat] = value }.toList()
        state.value = s.copy(barInput = input)
        val (a, d, h) = input
        if (a == null || d == null || h == null) return
        val ap = Appraisal(a, d, h)
        val g = graph
        lifecycleScope.launch {
            val next = applyAppraisal(s.copy(barInput = input), ap)
                ?: withContext(Dispatchers.Default) {
                    // Doesn't fit CP and HP: drop any pinned combo and keep the bars to show why.
                    val r = s.result?.takeIf { it.combos.isNotEmpty() }
                    s.copy(
                        barInput = input, bars = ap, selected = null, appraised = false,
                        analysis = r?.let { g.analyze(it, null) } ?: s.analysis, verdict = r?.let { g.judge(it, null) } ?: s.verdict,
                    )
                }
            update { if (it.barInput == input) next else it }
        }
    }

    override fun save() {
        val s = state.value ?: return
        val r = s.result ?: return
        val a = s.analysis ?: return
        graph.save(r, s.selected, a)
        state.value = s.copy(saved = true)
    }

    override fun copyName() {
        val s = state.value ?: return
        val combo = s.selected ?: s.result?.exact ?: return
        copyNickname(this, Nickname.of(combo, s.verdict?.primary, s.result?.species?.name ?: ""))
    }

    override fun report() {
        val s = state.value ?: return
        val i = s.input
        val name = s.result?.species?.name ?: s.candidates.firstOrNull { it.id == i.speciesId }?.name ?: "not recognised"
        val kind = listOfNotNull("Shadow".takeIf { i.shadow }, "Purified".takeIf { i.purified }, "Lucky".takeIf { i.lucky }, i.gender?.name?.lowercase())
        val shown = s.selected ?: s.result?.exact
        val read = listOfNotNull(
            "Pokémon: $name" + if (kind.isNotEmpty()) " (${kind.joinToString(", ")})" else "",
            "CP ${i.cp ?: "?"} · HP ${i.hp ?: "?"} · Stardust ${i.dust ?: "?"}",
            when {
                shown != null -> "IVs: ${shown.ivText}, Lv ${Levels.label(shown.level)}" + if (s.appraised) " (from Appraise)" else ""
                s.result != null -> "IVs: ${s.result.combos.size} possible"
                else -> null
            },
            s.bars?.let { "Appraise bars seen: ${it.ivText}" },
            s.verdict?.let { "Verdict: ${it.primary.title}" },
            s.moveNames.takeIf { it.isNotEmpty() }?.let { "Moves: ${it.joinToString(" + ")}" },
            s.problem?.let { "Problem: $it" },
        )
        // The form to write the report needs a keyboard, so it opens in the app (1.24).
        val form = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putStringArrayListExtra(MainActivity.EXTRA_BUG_READ, ArrayList(read))
        close()
        hideMini()
        startActivity(form)
    }

    override fun openInApp() {
        val s = state.value ?: return
        val i = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_INPUT, Json.encodeToString(CalcInput.serializer(), s.input))
        s.selected?.let { i.putExtra(MainActivity.EXTRA_SELECTED, Json.encodeToString(IvCombo.serializer(), it)) }
        close()
        hideMini()
        startActivity(i)
    }
}
