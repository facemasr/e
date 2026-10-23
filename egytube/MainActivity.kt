package com.personal.ytplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.bumptech.glide.Glide
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.Page
import java.util.Locale
import java.util.concurrent.Executors
import java.text.NumberFormat

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : Activity() {
    private val bg = Color.rgb(10, 13, 19)
    private val surface = Color.rgb(22, 27, 35)
    private val secondary = Color.rgb(153, 163, 176)
    private val red = Color.rgb(250, 65, 73)
    private val pool = Executors.newSingleThreadExecutor()
    private val videos = mutableListOf<StreamInfoItem>()
    private val sources = mutableListOf<StreamOption>()
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var root: LinearLayout
    private lateinit var header: View
    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var chipBar: View
    private lateinit var countryButton: TextView
    private lateinit var sectionTitle: TextView
    private lateinit var status: TextView
    private lateinit var list: ListView
    private lateinit var cardAdapter: VideoAdapter
    private lateinit var playerBox: FrameLayout
    private lateinit var playerDetails: LinearLayout
    private lateinit var playingTitle: TextView
    private lateinit var playingMeta: TextView
    private lateinit var qualitySpinner: Spinner
    private lateinit var qualityBar: LinearLayout
    private lateinit var footer: LinearLayout
    private lateinit var homeTab: TextView
    private lateinit var trendTab: TextView
    private lateinit var searchTab: TextView
    private var activeToken = 0
    private var playing = false
    private var inFullscreen = false
    private var chosenSource = -1
    private var spinnerReady = false
    private var currentMode = "trending"
    private var currentQuery = ""
    private var chosenCountry = "US"
    private val countries = arrayOf("عُمان", "مصر", "السعودية", "الإمارات", "الكويت", "قطر", "البحرين", "الأردن", "المغرب", "الجزائر", "العراق", "الولايات المتحدة", "بريطانيا", "ألمانيا", "الهند")
    private val countryCodes = arrayOf("OM", "EG", "SA", "AE", "KW", "QA", "BH", "JO", "MA", "DZ", "IQ", "US", "GB", "DE", "IN")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NewPipe.init(HttpDownloader())
        val locale = resources.configuration.locales[0]
        val phoneCountry = locale.country.ifBlank { Locale.getDefault().country }
        chosenCountry = getPreferences(MODE_PRIVATE).getString("country", phoneCountry.ifBlank { "US" })!!.uppercase(Locale.US)
        NewPipe.setupLocalization(Localization(locale.language.ifBlank { "ar" }), ContentCountry(chosenCountry))
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and 0
        makeScreen()
        player = ExoPlayer.Builder(this)
            .setSeekBackIncrementMs(10_000L)
            .setSeekForwardIncrementMs(10_000L)
            .build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                status.text = "تعذّر تشغيل هذا المصدر (" + error.errorCodeName + "). جرّب جودة مختلفة."
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) status.text = "● يعمل الآن"
                if (state == Player.STATE_BUFFERING && playing) status.text = "جارٍ التحميل..."
            }
        })
        playerView.setFullscreenButtonClickListener { full -> fullscreen(full) }
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            searchInput.setText(shared)
            startSearchOrUrl()
        } else loadTrending()
    }

    private fun Int.dp() = (this * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply { setColor(color); cornerRadius = radius.dp().toFloat() }
    }
    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView {
        return TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = true
            if (bold) typeface = Typeface.DEFAULT_BOLD
        }
    }

    private fun makeScreen() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 8.dp(), 14.dp(), 7.dp())
            setBackgroundColor(bg)
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.egytube_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "EGYTUBE"
            setOnClickListener { showHome() }
        }
        head.addView(logo, LinearLayout.LayoutParams(43.dp(), 43.dp()))
        head.addView(text("EGYTUBE", 22f, Color.WHITE, true), LinearLayout.LayoutParams(0, 43.dp(), 1f))
        val searchIcon = text("⌕", 33f, Color.WHITE).apply {
            gravity = Gravity.CENTER; contentDescription = "البحث"
            setOnClickListener { focusSearch() }
        }
        head.addView(searchIcon, LinearLayout.LayoutParams(44.dp(), 44.dp()))
        countryButton = text("🌐", 14f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = rounded(surface, 20)
            setPadding(8.dp(), 0, 8.dp(), 0)
            setOnClickListener { pickCountry() }
        }
        head.addView(countryButton, LinearLayout.LayoutParams(65.dp(), 36.dp()))
        header = head
        root.addView(head, LinearLayout.LayoutParams(-1, 62.dp()))

        val search = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(13.dp(), 4.dp(), 13.dp(), 4.dp())
        }
        searchInput = EditText(this).apply {
            setSingleLine(true)
            hint = "ابحث عن فيديوهات وقنوات..."
            setTextColor(Color.WHITE); setHintTextColor(secondary)
            textSize = 15f; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(15.dp(), 0, 10.dp(), 0)
            background = rounded(surface, 24)
            setOnEditorActionListener { _, _, _ -> startSearchOrUrl(); true }
        }
        search.addView(searchInput, LinearLayout.LayoutParams(0, 47.dp(), 1f))
        search.addView(text("بحث", 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { startSearchOrUrl() }
        }, LinearLayout.LayoutParams(55.dp(), 47.dp()))
        searchBar = search
        root.addView(search, LinearLayout.LayoutParams(-1, 58.dp()))

        val chips = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setFillViewport(false)
        }
        val chipRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(13.dp(), 2.dp(), 10.dp(), 9.dp())
            gravity = Gravity.CENTER_VERTICAL
        }
        val sections = listOf("الكل", "الرائج", "موسيقى", "ألعاب", "تقنية", "رياضة", "أخبار", "بث مباشر", "أفلام")
        sections.forEach { label ->
            val pill = text(label, 13f, Color.WHITE, label == "الرائج").apply {
                gravity = Gravity.CENTER
                setPadding(15.dp(), 0, 15.dp(), 0)
                background = rounded(if (label == "الرائج") Color.rgb(59, 66, 82) else surface, 22)
                setOnClickListener {
                    when (label) {
                        "الكل", "الرائج" -> showHome()
                        else -> searchFor(label)
                    }
                }
            }
            chipRow.addView(pill, LinearLayout.LayoutParams(-2, 37.dp()).apply { rightMargin = 7.dp() })
        }
        chips.addView(chipRow)
        chipBar = chips
        root.addView(chips, LinearLayout.LayoutParams(-1, 51.dp()))

        sectionTitle = text("الرائجة الآن", 19f, Color.WHITE, true).apply {
            setPadding(17.dp(), 4.dp(), 12.dp(), 3.dp())
        }
        root.addView(sectionTitle, LinearLayout.LayoutParams(-1, 43.dp()))
        status = text("جارٍ التحميل...", 12f, secondary).apply {
            setPadding(17.dp(), 0, 14.dp(), 3.dp())
        }
        root.addView(status, LinearLayout.LayoutParams(-1, 30.dp()))
        list = ListView(this).apply {
            divider = null
            cacheColorHint = Color.TRANSPARENT
            setBackgroundColor(bg)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
            setPadding(10.dp(), 0, 10.dp(), 0)
        }
        cardAdapter = VideoAdapter()
        list.adapter = cardAdapter
        list.setOnItemClickListener { _, _, position, _ ->
            if (position in videos.indices) playVideo(videos[position].url)
        }

        playerView = PlayerView(this).apply {
            setControllerShowTimeoutMs(3600)
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setKeepContentOnPlayerReset(true)
        }
        playerBox = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            visibility = View.GONE
        }
        playerBox.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        val watermark = ImageView(this).apply {
            setImageResource(R.drawable.egytube_logo)
            alpha = 0.66f
            scaleType = ImageView.ScaleType.FIT_CENTER
            isClickable = false; isFocusable = false
            contentDescription = "علامة EGYTUBE"
        }
        playerBox.addView(watermark, FrameLayout.LayoutParams(46.dp(), 46.dp(), Gravity.TOP or Gravity.END).apply {
            topMargin = 12.dp(); rightMargin = 14.dp()
        })
        root.addView(playerBox, LinearLayout.LayoutParams(-1, 235.dp()))

        playerDetails = LinearLayout(this).apply {
            visibility = View.GONE
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp(), 8.dp(), 14.dp(), 6.dp())
        }
        playingTitle = text("", 18f, Color.WHITE, true).apply {
            maxLines = 3
        }
        playerDetails.addView(playingTitle)
        playingMeta = text("", 13f, secondary).apply {
            maxLines = 2
            setPadding(0, 7.dp(), 0, 6.dp())
        }
        playerDetails.addView(playingMeta)
        root.addView(playerDetails)
        qualityBar = LinearLayout(this).apply {
            visibility = View.GONE; orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14.dp(), 0, 14.dp(), 5.dp())
        }
        val qualityLabel = text("الجودة", 13f, secondary)
        qualityBar.addView(qualityLabel, LinearLayout.LayoutParams(48.dp(), 44.dp()))
        qualitySpinner = Spinner(this)
        qualityBar.addView(qualitySpinner, LinearLayout.LayoutParams(0, 48.dp(), 1f))
        qualityBar.addView(text("1×", 15f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = rounded(surface, 20)
            setOnClickListener { chooseSpeed() }
        }, LinearLayout.LayoutParams(57.dp(), 39.dp()))
        qualityBar.addView(text("⛶", 23f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            setOnClickListener { fullscreen(!inFullscreen) }
        }, LinearLayout.LayoutParams(44.dp(), 44.dp()))
        qualitySpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (spinnerReady && position != chosenSource) switchQuality(position)
            }
        }
        root.addView(qualityBar)
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))

        footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(18, 22, 31))
            setPadding(10.dp(), 4.dp(), 10.dp(), 4.dp())
        }
        homeTab = nav("⌂\nالرئيسية") { showHome() }
        trendTab = nav("▶\nالرائج") { loadTrending() }
        searchTab = nav("⌕\nبحث") { focusSearch() }
        footer.addView(homeTab, LinearLayout.LayoutParams(0, -1, 1f))
        footer.addView(trendTab, LinearLayout.LayoutParams(0, -1, 1f))
        footer.addView(searchTab, LinearLayout.LayoutParams(0, -1, 1f))
        root.addView(footer, LinearLayout.LayoutParams(-1, 58.dp()))
        setContentView(root)
        updateCountryLabel()
        updateTabs()
    }

    private fun nav(label: String, listener: () -> Unit) = text(label, 13f).apply {
        gravity = Gravity.CENTER
        setOnClickListener { listener() }
    }
    private fun updateTabs() {
        homeTab.setTextColor(if (currentMode == "trending") Color.WHITE else secondary)
        trendTab.setTextColor(if (currentMode == "trending") red else secondary)
        searchTab.setTextColor(if (currentMode == "search") red else secondary)
    }
    private fun countryLabel(): String {
        val i = countryCodes.indexOf(chosenCountry)
        return if (i < 0) chosenCountry else countries[i]
    }
    private fun updateCountryLabel() {
        countryButton.text = "🌐 " + chosenCountry
    }
    private fun pickCountry() {
        AlertDialog.Builder(this).setTitle("اختر البلد لعرض الرائج")
            .setSingleChoiceItems(countries, countryCodes.indexOf(chosenCountry)) { d, index ->
                chosenCountry = countryCodes[index]
                getPreferences(MODE_PRIVATE).edit().putString("country", chosenCountry).apply()
                NewPipe.setPreferredContentCountry(ContentCountry(chosenCountry))
                updateCountryLabel()
                d.dismiss()
                showHome()
            }.setNegativeButton("إلغاء", null).show()
    }
    private fun focusSearch() {
        exitPlayer()
        currentMode = "search"
        updateTabs()
        searchInput.requestFocus()
        val im = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        im.showSoftInput(searchInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
    }
    private fun showHome() {
        exitPlayer()
        searchInput.clearFocus()
        loadTrending()
    }
    private fun exitPlayer() {
        if (inFullscreen) fullscreen(false)
        if (playing) {
            player.stop()
            playing = false
        }
        playerBox.visibility = View.GONE
        playerDetails.visibility = View.GONE
        qualityBar.visibility = View.GONE
        searchBar.visibility = View.VISIBLE
        chipBar.visibility = View.VISIBLE
    }
    private fun applyCards(newItems: List<StreamInfoItem>, headline: String) {
        videos.clear()
        videos.addAll(newItems)
        sectionTitle.text = headline
        cardAdapter.notifyDataSetChanged()
        list.setSelection(0)
        status.text = if (newItems.isEmpty()) "لا تتوفر فيديوهات حاليًا. جرّب بلدًا آخر أو البحث." else (newItems.size.toString() + " فيديو • اضغط للمشاهدة")
    }
    private fun loadTrending() {
        exitPlayer()
        currentMode = "trending"
        updateTabs()
        val token = ++activeToken
        sectionTitle.text = "الرائجة في " + countryLabel()
        status.text = "جارٍ جلب الفيديوهات الرائجة..."
        videos.clear(); cardAdapter.notifyDataSetChanged()
        pool.execute {
            try {
                val extractor = ServiceList.YouTube.kioskList.defaultKioskExtractor
                extractor.forceContentCountry(ContentCountry(chosenCountry))
                extractor.forceLocalization(Localization("ar"))
                extractor.fetchPage()
                val received = extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                runOnUiThread {
                    if (token == activeToken && !isDestroyed) {
                        applyCards(received, "الرائجة في " + countryLabel())
                    }
                }
            } catch(e: Exception) {
                runOnUiThread {
                    if (token == activeToken) {
                        status.text = "تعذّر تحميل الرائج لهذا البلد. اسحب للتحديث أو جرّب البحث."
                        Toast.makeText(this, (e.message ?: "تعذّر الاتصال").take(125), Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    private fun startSearchOrUrl() {
        val q = searchInput.text.toString().trim()
        if (q.isBlank()) return
        val match = Regex("https?://(?:www\\.|m\\.)?(?:youtube\\.com|youtu\\.be)/[^\\s]+", RegexOption.IGNORE_CASE).find(q)
        if (match != null) playVideo(match.value) else searchFor(q)
    }
    private fun searchFor(q: String) {
        exitPlayer()
        currentMode = "search"
        currentQuery = q
        searchInput.setText(q)
        updateTabs()
        val token = ++activeToken
        sectionTitle.text = "نتائج البحث: " + q
        status.text = "جارٍ البحث..."
        videos.clear(); cardAdapter.notifyDataSetChanged()
        pool.execute {
            try {
                val extractor = ServiceList.YouTube.getSearchExtractor(q)
                extractor.forceContentCountry(ContentCountry(chosenCountry))
                extractor.fetchPage()
                val received = extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                runOnUiThread {
                    if (token == activeToken && !isDestroyed) applyCards(received, "نتائج البحث: " + q)
                }
            } catch(e: Exception) {
                runOnUiThread {
                    if (token == activeToken) status.text = "تعذّر البحث. تحقق من الاتصال أو جرّب مصطلحًا مختلفًا."
                }
            }
        }
    }
    private fun playVideo(url: String) {
        val token = ++activeToken
        if (inFullscreen) fullscreen(false)
        playing = true
        chosenSource = -1
        spinnerReady = false
        player.stop()
        playerBox.visibility = View.VISIBLE
        playerDetails.visibility = View.VISIBLE
        qualityBar.visibility = View.VISIBLE
        searchBar.visibility = View.GONE
        chipBar.visibility = View.GONE
        sectionTitle.text = "فيديوهات مقترحة"
        playingTitle.text = "جارٍ فتح الفيديو..."
        playingMeta.text = ""
        status.text = "جارٍ تجهيز المشغل..."
        list.setSelection(0)
        pool.execute {
            try {
                val info = StreamInfo.getInfo(url)
                if (info.ageLimit > 0) throw IllegalStateException("هذا الفيديو مقيّد بالعمر وغير مدعوم.")
                val found = mutableListOf<StreamOption>()
                val audio = info.audioStreams.filter { it.isUrl && it.content.startsWith("https://") }.maxByOrNull { it.averageBitrate }
                info.videoStreams.filter { it.isUrl && it.content.startsWith("https://") }
                    .sortedByDescending { it.height }
                    .forEach { found.add(StreamOption(it.resolution + " — فيديو وصوت", it.content)) }
                info.videoOnlyStreams.filter { it.isUrl && it.content.startsWith("https://") }
                    .sortedByDescending { it.height }
                    .forEach { stream ->
                        if (audio != null) found.add(StreamOption(stream.resolution + " — جودة عالية", stream.content, audio.content))
                    }
                if (!info.hlsUrl.isNullOrBlank()) found.add(StreamOption("HLS — تلقائي", info.hlsUrl, null, MimeTypes.APPLICATION_M3U8))
                if (!info.dashMpdUrl.isNullOrBlank()) found.add(StreamOption("DASH — تلقائي", info.dashMpdUrl, null, MimeTypes.APPLICATION_MPD))
                val suggested = info.relatedItems.filterIsInstance<StreamInfoItem>()
                runOnUiThread {
                    if (token != activeToken || isDestroyed) return@runOnUiThread
                    playingTitle.text = info.name
                    playingMeta.text = info.uploaderName.orEmpty() + "  •  " + views(info.viewCount) + "  •  " + (info.textualUploadDate ?: "")
                    sources.clear(); sources.addAll(found)
                    if (suggested.isNotEmpty()) applyCards(suggested, "فيديوهات مقترحة")
                    if (found.isEmpty()) {
                        status.text = "هذا الفيديو لا يحتوي على مصدر قابل للتشغيل حاليًا."
                    } else {
                        qualitySpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, found.map { it.label })
                        spinnerReady = true
                        switchQuality(0)
                    }
                }
            } catch(e: Exception) {
                runOnUiThread {
                    if (token == activeToken && !isDestroyed) {
                        status.text = "تعذّر تجهيز هذا الفيديو: " + (e.message ?: "خطأ غير معروف").take(95)
                        playingTitle.text = "تعذّر عرض الفيديو"
                    }
                }
            }
        }
    }
    private fun chooseSpeed() {
        val names = arrayOf("0.5×", "0.75×", "1×", "1.25×", "1.5×", "1.75×", "2×")
        val values = floatArrayOf(.5f,.75f,1f,1.25f,1.5f,1.75f,2f)
        AlertDialog.Builder(this).setTitle("سرعة التشغيل").setItems(names) { _, which ->
            player.setPlaybackSpeed(values[which])
            Toast.makeText(this, "السرعة: " + names[which], Toast.LENGTH_SHORT).show()
        }.show()
    }
    private fun switchQuality(index: Int) {
        if (index !in sources.indices) return
        val seek = if (chosenSource < 0) 0L else player.currentPosition.coerceAtLeast(0)
        chosenSource = index
        val option = sources[index]
        val factory = DefaultDataSource.Factory(this, DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
        fun progressive(url: String): MediaSource = ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(url))
        try {
            val media = when {
                option.mime == MimeTypes.APPLICATION_M3U8 -> HlsMediaSource.Factory(factory).createMediaSource(MediaItem.Builder().setUri(option.url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
                option.mime == MimeTypes.APPLICATION_MPD -> DashMediaSource.Factory(factory).createMediaSource(MediaItem.Builder().setUri(option.url).setMimeType(MimeTypes.APPLICATION_MPD).build())
                option.audio != null -> MergingMediaSource(progressive(option.url), progressive(option.audio))
                else -> progressive(option.url)
            }
            player.setMediaSource(media)
            player.prepare()
            if (seek > 0) player.seekTo(seek)
            player.playWhenReady = true
            if (qualitySpinner.selectedItemPosition != index) qualitySpinner.setSelection(index)
        } catch(e: Exception) {
            status.text = "تعذّر تشغيل الجودة المختارة."
        }
    }
    private fun fullscreen(full: Boolean) {
        inFullscreen = full
        if (full) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        adaptFullscreen()
    }
    private fun adaptFullscreen() {
        val landscape = inFullscreen
        header.visibility = if (landscape) View.GONE else View.VISIBLE
        footer.visibility = if (landscape) View.GONE else View.VISIBLE
        searchBar.visibility = if (landscape || playing) View.GONE else View.VISIBLE
        chipBar.visibility = if (landscape || playing) View.GONE else View.VISIBLE
        sectionTitle.visibility = if (landscape) View.GONE else View.VISIBLE
        status.visibility = if (landscape) View.GONE else View.VISIBLE
        playerDetails.visibility = if (landscape || !playing) View.GONE else View.VISIBLE
        qualityBar.visibility = if (landscape || !playing) View.GONE else View.VISIBLE
        list.visibility = if (landscape) View.GONE else View.VISIBLE
        playerBox.layoutParams = if (landscape) LinearLayout.LayoutParams(-1, 0, 1f)
                                   else LinearLayout.LayoutParams(-1, 235.dp())
        window.decorView.systemUiVisibility = if (landscape)
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            else 0
    }
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (playing && inFullscreen) adaptFullscreen()
    }
    override fun onBackPressed() {
        when {
            inFullscreen -> fullscreen(false)
            playing -> showHome()
            currentMode == "search" -> showHome()
            else -> super.onBackPressed()
        }
    }
    override fun onDestroy() {
        playerView.player = null
        player.release()
        pool.shutdownNow()
        super.onDestroy()
    }
    private fun views(n: Long): String {
        if (n < 0) return "مشاهدات غير متاحة"
        val value = when {
            n >= 1_000_000_000 -> String.format(Locale.US, "%.1f مليار", n / 1_000_000_000.0)
            n >= 1_000_000 -> String.format(Locale.US, "%.1f مليون", n / 1_000_000.0)
            n >= 1_000 -> String.format(Locale.US, "%.1f ألف", n / 1_000.0)
            else -> NumberFormat.getNumberInstance(Locale.forLanguageTag("ar")).format(n)
        }
        return value + " مشاهدة"
    }
    private fun duration(seconds: Long): String {
        if (seconds <= 0) return "مباشر"
        val h = seconds / 3600; val m = (seconds % 3600)/60; val s = seconds % 60
        return if (h > 0) String.format(Locale.US,"%d:%02d:%02d",h,m,s)
            else String.format(Locale.US,"%d:%02d",m,s)
    }
    private fun bestImage(item: StreamInfoItem): String? {
        val images = item.thumbnails
        return images.maxByOrNull { it.height.coerceAtLeast(0) }?.url
    }
    private inner class VideoAdapter : BaseAdapter() {
        override fun getCount() = videos.size
        override fun getItem(position: Int): Any = videos[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val item = videos[position]
            val holder: CardHolder
            val outer: LinearLayout
            if (convertView is LinearLayout && convertView.tag is CardHolder) {
                outer = convertView
                holder = convertView.tag as CardHolder
            } else {
                outer = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 4.dp(), 0, 13.dp())
                }
                val cover = FrameLayout(this@MainActivity).apply {
                    background = rounded(surface, 14)
                    clipToOutline = true
                }
                val picture = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                cover.addView(picture, FrameLayout.LayoutParams(-1,-1))
                val length = text("0:00", 12f, Color.WHITE, true).apply {
                    background = rounded(Color.argb(220, 0,0,0), 5)
                    setPadding(6.dp(), 3.dp(), 6.dp(), 3.dp())
                }
                cover.addView(length, FrameLayout.LayoutParams(-2,-2,Gravity.RIGHT or Gravity.BOTTOM).apply {
                    rightMargin=9.dp(); bottomMargin=9.dp()
                })
                val width = resources.displayMetrics.widthPixels - 20.dp()
                outer.addView(cover, LinearLayout.LayoutParams(-1, width * 9 / 16))
                val meta = LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.TOP
                    setPadding(3.dp(), 11.dp(), 0, 0)
                }
                val avatar = ImageView(this@MainActivity).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = rounded(Color.rgb(38, 47, 64), 100)
                    clipToOutline = true
                }
                meta.addView(avatar, LinearLayout.LayoutParams(41.dp(), 41.dp()).apply { rightMargin=11.dp() })
                val texts = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL }
                val title = text("", 15.5f, Color.WHITE, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
                texts.addView(title)
                val detail = text("", 12f, secondary).apply {
                    maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, 5.dp(), 0, 0)
                }
                texts.addView(detail)
                meta.addView(texts, LinearLayout.LayoutParams(0,-2,1f))
                val more = text("⋮", 27f, secondary).apply {
                    gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    setOnClickListener {
                        val positionNow = this.tag as? Int ?: return@setOnClickListener
                        if (positionNow !in videos.indices) return@setOnClickListener
                        val video = videos[positionNow]
                        AlertDialog.Builder(this@MainActivity).setItems(arrayOf("مشاركة الفيديو", "نسخ الرابط")) { _, which ->
                            if (which == 0) {
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"; putExtra(Intent.EXTRA_TEXT, video.url)
                                }
                                startActivity(Intent.createChooser(share, "مشاركة"))
                            } else {
                                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("EGYTUBE", video.url))
                                Toast.makeText(this@MainActivity,"تم نسخ الرابط",Toast.LENGTH_SHORT).show()
                            }
                        }.show()
                    }
                }
                meta.addView(more, LinearLayout.LayoutParams(35.dp(), 46.dp()))
                outer.addView(meta)
                holder = CardHolder(picture,avatar,title,detail,length,more)
                outer.tag = holder
            }
            holder.title.text = item.name
            val channel = item.uploaderName?.ifBlank { "قناة يوتيوب" } ?: "قناة يوتيوب"
            val verified = if (item.isUploaderVerified) " ✓" else ""
            val age = item.textualUploadDate?.takeIf { it.isNotBlank() }?.let { " • " + it } ?: ""
            holder.subtitle.text = channel + verified + "  •  " + views(item.viewCount) + age
            holder.duration.text = duration(item.duration)
            holder.more.tag = position
            val photo = bestImage(item)
            Glide.with(this@MainActivity).load(photo).centerCrop().placeholder(android.R.drawable.ic_media_play).into(holder.cover)
            val avatars = item.uploaderAvatars
            Glide.with(this@MainActivity).load(avatars.firstOrNull()?.url).circleCrop()
                .placeholder(android.R.drawable.sym_def_app_icon).into(holder.avatar)
            return outer
        }
    }
    private data class CardHolder(
        val cover: ImageView,
        val avatar: ImageView,
        val title: TextView,
        val subtitle: TextView,
        val duration: TextView,
        val more: TextView
    )
}
private data class StreamOption(val label: String, val url: String, val audio: String? = null, val mime: String? = null)
