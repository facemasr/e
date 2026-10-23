package com.personal.ytplayer

import android.app.Activity
import android.app.DownloadManager
import android.os.Environment
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.app.PictureInPictureParams
import android.util.Rational
import android.graphics.Rect
import android.widget.FrameLayout
import android.view.WindowInsets
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class MainActivity:Activity() {
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var root:LinearLayout
    private lateinit var header:LinearLayout
    private lateinit var searchRow:LinearLayout
    private lateinit var searchBox:AutoCompleteTextView
    private val cachedVideos=mutableListOf<VideoModel>()
    private val extraItems=mutableListOf<InfoItem>()
    private lateinit var extraResults:HorizontalScrollView
    private lateinit var shortSeriesSlider:ShortSeriesCarousel
    private lateinit var extraRow:LinearLayout
    private var typeFilter="الكل"
    private var sortMode="الملاءمة"
    private lateinit var categories:HorizontalScrollView
    private lateinit var sectionTitle:TextView
    private lateinit var status:TextView
    private lateinit var searchTools:LinearLayout
    private lateinit var watchNotice:TextView
    private var portraitVideo=false
    private fun normalVideoHeight():Int {
        val width=resources.displayMetrics.widthPixels
        val height=resources.displayMetrics.heightPixels
        return if(portraitVideo)
            kotlin.math.min((height*0.65f).toInt(),width*16/9)
        else width*9/16
    }
    private fun adjustNormalPlayerHeight() {
        if(!this::panel.isInitialized || !this::playerHost.isInitialized || panel.isExpanded())return
        if(isWatching && !miniActive && !inPip) {
            panel.root.layoutParams=LinearLayout.LayoutParams(-1,normalVideoHeight())
        }
    }
    private lateinit var list:RecyclerView
    private lateinit var webResults:WebView
    private var webMode=false
    private lateinit var adapter:VideoCardAdapter
    private lateinit var panel:VideoPlaybackPanel
    private lateinit var playerDetails:LinearLayout
    private lateinit var playerHost:LinearLayout
    private lateinit var advertising:AdvertisementBanner
    private lateinit var screen:FrameLayout
    private lateinit var miniHost:LinearLayout
    private lateinit var miniSlot:FrameLayout
    private lateinit var pipHost:FrameLayout
    private lateinit var miniTitle:TextView
    private var miniActive=false
    private var inPip=false
    private var pipAuthorizedSource=false
    private lateinit var footer:LinearLayout
    private lateinit var moreButton:TextView
    private var currentToken=0
    private var currentExtractor:ListExtractor<out InfoItem>?=null
    private var nextPage:Page?=null
    private var inFlight=false
    private var isWatching=false
    private var country="OM"
    private var countryLabel="عُمان"
    private var sourceOptions= mutableListOf<StreamChoice>()
    private var currentQuality=-1
    private var playingModel:VideoModel?=null
    private var customTerm:String?=null
    private var homeMode="مقترح"
    private var lastRecordedUrl:String?=null
    private fun categoryOf(model:VideoModel):String {
        val title=(model.title+" "+model.channel).lowercase(Locale.ROOT)
        return when {
            listOf("موسيقى","أغنية","اغنية","song","music","شيرين","مهرجان").any{title.contains(it)} -> "موسيقى"
            listOf("مسلسل","دراما","الحلقة","episode","drama","كوري").any{title.contains(it)} -> "مسلسلات"
            listOf("فيلم","movie","film","سينما","trailer").any{title.contains(it)} -> "أفلام"
            listOf("كرة","مباراة","football","soccer","رياضة","هدف","sport").any{title.contains(it)} -> "رياضة"
            listOf("game","gaming","لعبة","العاب","ألعاب","minecraft","pubg").any{title.contains(it)} -> "ألعاب"
            listOf("تقنية","هاتف","android","tech","برمجة","computer","iphone").any{title.contains(it)} -> "تقنية"
            listOf("أخبار","خبر","news","عاجل").any{title.contains(it)} -> "أخبار"
            listOf("anime","انمي","أنمي","animation").any{title.contains(it)} -> "أنمي"
            else -> "متنوع"
        }
    }
    private fun recordWatching(model:VideoModel) {
        if(lastRecordedUrl==model.url)return
        lastRecordedUrl=model.url
        val category=categoryOf(model)
        val preferences=getSharedPreferences("egytube_interests",0)
        preferences.edit().putInt("total",preferences.getInt("total",0)+1)
            .putInt("count_"+category, preferences.getInt("count_"+category,0)+1)
            .putString("last_category",category).apply()
    }
    private fun recommendedScore(video:VideoModel):Double {
        val preferences=getSharedPreferences("egytube_interests",0)
        val interest=preferences.getInt("count_"+categoryOf(video),0)
        val followed=getSharedPreferences("egytube_library",0)
            .getStringSet("followed_channels",emptySet()).orEmpty().contains(video.channel)
        val popularity=kotlin.math.ln(video.views.coerceAtLeast(0L).toDouble()+1.0)
        return interest*16.0+popularity+(if(followed)18.0 else 0.0)
    }
    private fun homeTitle():String = when(homeMode) {
        "الأكثر مشاهدة" -> "الأكثر مشاهدة الآن"
        "الأحدث" -> "أحدث الفيديوهات"
        else -> if(getSharedPreferences("egytube_interests",0).getInt("total",0)>0)
            "محتوى قد يعجبك" else "فيديوهات مختارة لك"
    }
    private val options = linkedMapOf(
        "OM" to "عُمان","SA" to "السعودية","EG" to "مصر",
        "AE" to "الإمارات","KW" to "الكويت","QA" to "قطر",
        "BH" to "البحرين","JO" to "الأردن","US" to "الولايات المتحدة",
        "GB" to "بريطانيا","DE" to "ألمانيا","IN" to "الهند"
    )
    private fun dp(n:Int)=Palette.dp(this,n)
    private fun baseText(text:String,size:Float,bold:Boolean=false,color:Int=Palette.WHITE):TextView=
        TextView(this).apply {
            this.text=text; textSize=size;setTextColor(color)
            if(bold)typeface=Typeface.DEFAULT_BOLD
        }
    private fun clickable(text:String, action:()->Unit):TextView=baseText(text,14f,true).apply {
        gravity=Gravity.CENTER
        setPadding(dp(10),dp(7),dp(10),dp(7))
        background=Palette.round(Palette.SURFACE,dp(18).toFloat())
        setOnClickListener { action() }
    }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        NewPipe.init(HttpDownloader())
        @Suppress("DEPRECATION")
        val telephony=getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
        val sim=telephony.simCountryIso.uppercase(Locale.ROOT)
        val fromLocale=Locale.getDefault().country.uppercase(Locale.ROOT)
        country=when {
            options.containsKey(sim) -> sim
            options.containsKey(fromLocale) -> fromLocale
            else -> "OM"
        }
        countryLabel=options[country] ?: country
        NewPipe.setPreferredContentCountry(ContentCountry(country))
        window.statusBarColor=Color.WHITE
        window.navigationBarColor=Color.WHITE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildUI()
        val deepId=intent?.data?.let { url ->
            if(url.scheme=="https" && url.host=="egyshow.com" && url.path=="/watch")
                url.getQueryParameter("egy")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
            else null
        }
        if(deepId!=null) {
            openPlayer(VideoModel("https://www.youtube.com/watch?v="+deepId,
                "فيديو يوتيوب","",-1L,-1L,"","","",false,false))
        } else if(intent?.action==Intent.ACTION_SEND && intent.type=="text/plain") {
            searchBox.setText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            search()
        } else loadTrending()
    }

    private fun buildUI() {
        if(this::shortSeriesSlider.isInitialized)shortSeriesSlider.close()
        miniActive=false
        inPip=false
        // Rebuilding the screen after country selection must release the old player and WebViews.
        if(this::advertising.isInitialized) advertising.dispose()
        if(this::panel.isInitialized) panel.release()
        if(this::webResults.isInitialized) webResults.destroy()
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
        }
        header=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(dp(13),dp(3),dp(13),dp(3))
            setBackgroundColor(Color.WHITE)
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.egytube_logo)
            scaleType=ImageView.ScaleType.FIT_CENTER
            contentDescription="EGYTUBE"
        }, LinearLayout.LayoutParams(dp(42),dp(42)))
        header.addView(baseText("EGYTUBE",21f,true).apply {
            setPadding(dp(8),0,0,0)
        },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(clickable("⌕",{showSearch()}).apply {
            textSize=23f;setBackgroundColor(Color.TRANSPARENT)
        })
        header.addView(clickable("☰",{showMenu()}).apply {
            textSize=19f;setBackgroundColor(Color.TRANSPARENT)
        })
        root.addView(header, LinearLayout.LayoutParams(-1,dp(58)))

        searchRow=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(12),dp(3),dp(12),dp(5))
        }
        searchBox=AutoCompleteTextView(this).apply {
            threshold=1
            dropDownVerticalOffset=dp(4)

            hint="ابحث عن الفيديوهات أو ألصق رابطًا"
            textSize=14.5f
            setTextColor(Palette.WHITE)
            setHintTextColor(Palette.SECOND)
            background=Palette.round(Palette.SURFACE,dp(16).toFloat())
            setPadding(dp(14),0,dp(14),0)
            isSingleLine=true
            imeOptions=EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _,_,_ -> search();true }
            setOnItemClickListener { parent,_,position,_ ->
                setText(parent.getItemAtPosition(position).toString())
                dismissDropDown()
                search()
            }
            addTextChangedListener(object:TextWatcher {
                override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
                override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int) {
                    val term=s?.toString()?.trim().orEmpty()
                    if(term.length<2)return
                    val history=getSharedPreferences("egytube_search",0)
                        .getString("terms","").orEmpty().split("\n")
                    val suggestions=(history + cachedVideos.map{it.title}
                        + listOf("أفلام","موسيقى","ألعاب","تقنية","رياضة","Shorts"))
                        .filter { it.contains(term,ignoreCase=true) && it!=term }
                        .distinct().take(7)
                    if(suggestions.isNotEmpty()) {
                        setAdapter(ArrayAdapter(this@MainActivity,android.R.layout.simple_dropdown_item_1line,suggestions))
                        showDropDown()
                    }
                }
                override fun afterTextChanged(s:Editable?){}
            })
        }
        searchRow.addView(searchBox,LinearLayout.LayoutParams(0,dp(48),1f))
        searchRow.addView(clickable("بحث",{search()}),LinearLayout.LayoutParams(dp(69),dp(45)).apply { leftMargin=dp(8) })
        searchRow.visibility=View.GONE
        root.addView(searchRow,LinearLayout.LayoutParams(-1,dp(65)))

        categories=HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false
            setPadding(0,0,0,dp(5))
        }
        val chips=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(12),0,dp(12),0)
        }
        val countryChip=clickable("◉  "+countryLabel,{ chooseCountry() })
        chips.addView(countryChip, LinearLayout.LayoutParams(-2,dp(40)).apply { rightMargin=dp(7) })
        for (entry in listOf(
            "الكل" to "", "مقترح لك" to "@for-you", "الأكثر مشاهدة" to "@popular",
            "الأحدث" to "@new", "موسيقى" to "موسيقى", "دراما" to "مسلسلات دراما",
            "أفلام" to "أفلام", "رياضة" to "رياضة", "ألعاب" to "الألعاب",
            "مباشر" to "بث مباشر", "أخبار" to "أخبار"
        )) {
            val term=entry.second
            val selected=(term=="@for-you" && homeMode=="مقترح") || (term=="@popular" && homeMode=="الأكثر مشاهدة")
            chips.addView(clickable(entry.first,{
                when(term) {
                    "" -> {homeMode="مقترح";sortMode="الملاءمة";loadTrending()}
                    "@for-you" -> {
                        homeMode="مقترح";sortMode="الملاءمة"
                        if(customTerm!=null)loadTrending() else applyFilters()
                    }
                    "@popular" -> {
                        homeMode="الأكثر مشاهدة";sortMode="الأكثر مشاهدة"
                        if(customTerm!=null)loadTrending() else applyFilters()
                    }
                    "@new" -> {
                        homeMode="الأحدث";sortMode="الأحدث تقريبًا"
                        if(customTerm!=null)loadTrending() else applyFilters()
                    }
                    else -> {searchBox.setText(term);search()}
                }
            }).apply {
                if(selected) {
                    background=Palette.round(0xff161616.toInt(),dp(17).toFloat())
                    setTextColor(Color.WHITE)
                }
            },LinearLayout.LayoutParams(-2,dp(40)).apply { rightMargin=dp(7) })
        }
        categories.addView(chips)
        root.addView(categories,LinearLayout.LayoutParams(-1,dp(49)))
        shortSeriesSlider=ShortSeriesCarousel(this,
            { video -> openPlayer(video) },
            {
                searchBox.setText("مسلسلات قصيرة الحلقة 1")
                search()
            }
        )
        root.addView(shortSeriesSlider.root,LinearLayout.LayoutParams(-1,-2))
        shortSeriesSlider.load()

        sectionTitle=baseText(homeTitle(),19f,true).apply {
            setPadding(dp(17),dp(12),dp(10),dp(4))
        }
        root.addView(sectionTitle)
        status=baseText("جارٍ تحميل الفيديوهات...",12.5f,false,Palette.SECOND).apply {
            setPadding(dp(17),0,dp(17),dp(8))
        }
        root.addView(status)
        searchTools=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(12),dp(3),dp(12),dp(7))
        }
        searchTools.addView(clickable("نوع النتائج ▾",{chooseType()}))
        searchTools.addView(clickable("الترتيب ▾",{chooseSort()}),
            LinearLayout.LayoutParams(-2,dp(39)).apply {leftMargin=dp(7)})
        root.addView(searchTools,LinearLayout.LayoutParams(-1,dp(49)))
        extraResults=HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false
            visibility=View.GONE
        }
        extraRow=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(10),dp(4),dp(10),dp(9))
        }
        extraResults.addView(extraRow)
        root.addView(extraResults,LinearLayout.LayoutParams(-1,dp(65)))
        panel=VideoPlaybackPanel(this,
            { leavePlayer() },
            { chooseQuality() },
            { fullscreen -> resizePlayer(fullscreen) },
            { enterPip() },
            { expandMini() },
            { portrait ->
                portraitVideo=portrait
                adjustNormalPlayerHeight()
            },
            { shareActiveVideo() }
        )
        panel.playbackError={ error -> runOnUiThread {
            if(this::watchNotice.isInitialized && isWatching) {
                watchNotice.text=error
                watchNotice.visibility=View.VISIBLE
            } else {
                status.text=error
                status.visibility=View.VISIBLE
            }
        } }
        panel.player.addListener(object:androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(isPlaying:Boolean) {
                if(isPlaying && playingModel!=null) recordWatching(playingModel!!)
            }
        })
        playerHost=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=View.GONE
        }
        playerHost.addView(panel.root,LinearLayout.LayoutParams(-1,normalVideoHeight()))
        root.addView(playerHost,LinearLayout.LayoutParams(-1,-2))
        // The clearly labelled advertising placement belongs INSIDE the player frame.
        advertising=AdvertisementBanner(this)
        panel.attachAd(advertising)
        playerDetails=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=View.GONE
            setPadding(dp(16),dp(9),dp(16),dp(9))
        }
        root.addView(playerDetails)

        list=RecyclerView(this).apply {
            layoutManager=LinearLayoutManager(this@MainActivity)
            setHasFixedSize(false)
            clipToPadding=false
        }
        adapter=VideoCardAdapter(this,{ video -> openPlayer(video) })
        list.adapter=adapter
        list.addOnScrollListener(object:RecyclerView.OnScrollListener() {
            override fun onScrolled(view:RecyclerView,dx:Int,dy:Int) {
                if(dy<=0 || inFlight || nextPage==null || isWatching)return
                val manager=view.layoutManager as LinearLayoutManager
                if(manager.findLastVisibleItemPosition() >= adapter.itemCount - 5) loadMore()
            }
        })
        root.addView(list,LinearLayout.LayoutParams(-1,0,1f))
        webResults = WebView(this).apply {
            visibility=View.GONE
            setBackgroundColor(Palette.BG)
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.loadsImagesAutomatically=true
            settings.mediaPlaybackRequiresUserGesture=true
            webChromeClient=WebChromeClient()
            webViewClient=object:WebViewClient() {
                override fun shouldOverrideUrlLoading(view:WebView?, request:WebResourceRequest?):Boolean {
                    val uri=request?.url ?: return false
                    if(!request.isForMainFrame)return false
                    val host=uri.host.orEmpty().lowercase(Locale.ROOT)
                    // Let our own short-series catalog and episode pages navigate inside WebView.
                    if(host=="egyshow.com" || host=="www.egyshow.com")return false
                    if(!(host=="youtube.com" || host.endsWith(".youtube.com") || host=="youtu.be"))return true
                    val id=when {
                        host=="youtu.be" -> uri.lastPathSegment
                        uri.path?.startsWith("/shorts/")==true -> uri.pathSegments.getOrNull(1)
                        uri.path?.startsWith("/live/")==true -> uri.pathSegments.getOrNull(1)
                        else -> uri.getQueryParameter("v")
                    }
                    if(id!=null && id.matches(Regex("[A-Za-z0-9_-]{11}"))) {
                        openPlayer(VideoModel("https://www.youtube.com/watch?v="+id,"فيديو يوتيوب","",-1,-1,"","","",false,false))
                        return true
                    }
                    return false
                }
            }
        }
        root.addView(webResults,LinearLayout.LayoutParams(-1,0,1f))
        moreButton=baseText("عرض المزيد",14f,true,Palette.SECOND).apply {
            gravity=Gravity.CENTER
            setPadding(0,dp(6),0,dp(6))
            visibility=View.GONE
            setOnClickListener { loadMore() }
        }
        root.addView(moreButton,LinearLayout.LayoutParams(-1,dp(34)))

        footer=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.WHITE)
            elevation=dp(6).toFloat()
        }
        fun tab(icon:String,name:String,go:()->Unit) {
            val cell=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL
                gravity=Gravity.CENTER
                setOnClickListener {go()}
            }
            cell.addView(baseText(icon,24f,true).apply {gravity=Gravity.CENTER},
                LinearLayout.LayoutParams(-1,dp(28)))
            cell.addView(baseText(name,11f).apply {gravity=Gravity.CENTER},
                LinearLayout.LayoutParams(-1,dp(22)))
            footer.addView(cell,LinearLayout.LayoutParams(0,dp(61),1f))
        }
        tab("⌂","الرئيسية"){if(isWatching)leavePlayer();loadTrending()}
        tab("▤","Shorts"){if(isWatching)leavePlayer();showShorts()}
        tab("≡","الاشتراكات"){if(isWatching)leavePlayer();showSubscriptions()}
        tab("◉","أنت"){if(isWatching)leavePlayer();showLibrary()}
        root.addView(footer,LinearLayout.LayoutParams(-1,dp(64)))
        // A stable full-window stage keeps a floating in-app mini-player and PiP surface.
        screen=FrameLayout(this).apply {setBackgroundColor(Palette.BG)}
        screen.addView(root,FrameLayout.LayoutParams(-1,-1))
        // Floating bottom player keeps the SAME ExoPlayer instance.
        miniHost=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            visibility=View.GONE
            background=Palette.round(0xfff8e9ee.toInt(),dp(16).toFloat())
            elevation=dp(12).toFloat()
            clipToOutline=true
            setPadding(dp(5),dp(5),dp(7),dp(5))
        }
        miniSlot=FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            clipToOutline=true
        }
        miniHost.addView(miniSlot,LinearLayout.LayoutParams(dp(137),dp(74)))
        miniTitle=baseText("EGYTUBE",12f,true).apply {
            maxLines=2
            gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
            ellipsize=android.text.TextUtils.TruncateAt.END
            setPadding(dp(8),0,dp(5),0)
            setOnClickListener { expandMini() }
        }
        miniHost.addView(miniTitle,LinearLayout.LayoutParams(0,-1,1f))
        miniHost.addView(baseText("⌃",25f,true).apply {
            gravity=Gravity.CENTER
            contentDescription="توسيع المشغل"
            setOnClickListener {expandMini()}
        },LinearLayout.LayoutParams(dp(32),-1))
        miniHost.addView(baseText("×",23f,true).apply {
            gravity=Gravity.CENTER
            contentDescription="إغلاق المشغل"
            setOnClickListener {closeMini()}
        },LinearLayout.LayoutParams(dp(34),-1))
        screen.addView(miniHost,FrameLayout.LayoutParams(-1,dp(84),Gravity.BOTTOM).apply {
            bottomMargin=dp(71);leftMargin=dp(8);rightMargin=dp(8)
        })
        pipHost=FrameLayout(this).apply {
            visibility=View.GONE
            setBackgroundColor(Color.BLACK)
        }
        screen.addView(pipHost,FrameLayout.LayoutParams(-1,-1))
        setContentView(screen)
        // Edge-to-edge is enforced by Android 15+ for targetSdk 35.
        // Keep the header below status-bar icons and footer above gesture navigation.
        if(Build.VERSION.SDK_INT >= 35) {
            window.setDecorFitsSystemWindows(false)
            root.setOnApplyWindowInsetsListener { view,ins ->
                val bars=ins.getInsets(WindowInsets.Type.systemBars())
                val full=this::panel.isInitialized && panel.isExpanded()
                view.setPadding(0,if(full)0 else bars.top,0,if(full)0 else bars.bottom)
                ins
            }
            root.requestApplyInsets()
        }
    }

    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url=intent.data ?:return
        if(url.scheme!="https" || url.host!="egyshow.com" || url.path!="/watch")return
        val id=url.getQueryParameter("egy")?.takeIf{it.matches(Regex("[A-Za-z0-9_-]{11}"))} ?:return
        openPlayer(VideoModel("https://www.youtube.com/watch?v="+id,
            "فيديو يوتيوب","",-1L,-1L,"","","",false,false))
    }
    private fun showSearch() {
        if(isWatching)leavePlayer()
        searchRow.visibility=View.VISIBLE
        searchBox.requestFocus()
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(searchBox,InputMethodManager.SHOW_IMPLICIT)
    }
    private fun showMenu() {
        android.app.AlertDialog.Builder(this).setTitle("EGYTUBE")
            .setItems(arrayOf("الرئيسية","البحث","اختيار البلد","المشاهدة لاحقًا","الترتيب","النوع","التنزيلات")){_,n->
                when(n) {
                    0 -> loadTrending()
                    1 -> showSearch()
                    2 -> chooseCountry()
                    3 -> showLibrary()
                    4 -> chooseSort()
                    5 -> chooseType()
                    6 -> DownloadCenter.show(this)
                }
            }.show()
    }
    private fun showShorts() {
        searchRow.visibility=View.VISIBLE
        searchBox.setText("Shorts")
        search()
        sectionTitle.text="استكشاف Shorts"
    }
    private fun showSubscriptions() {
        val followed=getSharedPreferences("egytube_library",0)
            .getStringSet("followed_channels",emptySet()).orEmpty()
        val matched=cachedVideos.filter { followed.contains(it.channel) }
        if(matched.isEmpty()) {
            android.app.AlertDialog.Builder(this).setTitle("الاشتراكات المحلية")
                .setMessage("لم تتبع قنوات بعد. يمكنك متابعة قناة من قائمة الفيديو. هذه قائمة محلية داخل EGYTUBE، وليست مرتبطة بحساب يوتيوب.")
                .setPositiveButton("حسنًا",null).show()
        } else {
            adapter.replaceAll(matched)
            sectionTitle.text="قنواتك المتابعة محليًا"
        }
    }
    private fun showLibrary() {
        val urls=getSharedPreferences("egytube_library",0)
            .getStringSet("saved_urls",emptySet()).orEmpty()
        val matches=cachedVideos.filter {urls.contains(it.url)}
        val terms=getSharedPreferences("egytube_search",0)
            .getString("terms","").orEmpty().split("\n").filter {it.isNotBlank()}
        android.app.AlertDialog.Builder(this).setTitle("مكتبتك")
             .setItems(arrayOf("المشاهدة لاحقًا ("+urls.size+")","عمليات البحث السابقة","إدارة التنزيلات")){_,index->
                if(index==0) {
                    adapter.replaceAll(matches)
                    sectionTitle.text="المشاهدة لاحقًا"
                    status.text=if(matches.isEmpty()) "لا توجد فيديوهات محفوظة ضمن الصفحة المحمّلة حاليًا."
                        else "فيديوهات محفوظة: "+matches.size
                } else if(index==2) {
                    DownloadCenter.show(this)
                } else {
                    android.app.AlertDialog.Builder(this).setTitle("بحث سابق")
                        .setItems(terms.take(12).toTypedArray()){_,which->
                            if(which in terms.indices) {
                                searchBox.setText(terms[which]);search()
                            }
                        }.show()
                }
            }.show()
    }
    private fun chooseType() {
        val values=arrayOf("الكل","فيديوهات","مباشر","القنوات","قوائم التشغيل")
        android.app.AlertDialog.Builder(this).setTitle("تصفية الصفحة الحالية")
            .setSingleChoiceItems(values,values.indexOf(typeFilter)){dialog,which->
                typeFilter=values[which];applyFilters();dialog.dismiss()
            }.setNegativeButton("إلغاء",null).show()
    }
    private fun chooseSort() {
        val values=arrayOf("الملاءمة","الأكثر مشاهدة","الأحدث تقريبًا")
        android.app.AlertDialog.Builder(this).setTitle("ترتيب الصفحة الحالية")
            .setSingleChoiceItems(values,values.indexOf(sortMode)){dialog,which->
                sortMode=values[which];applyFilters();dialog.dismiss()
            }.setNegativeButton("إلغاء",null).show()
    }
    private fun approximateAge(published:String):Long {
        val t=published.lowercase(Locale.ROOT)
        val amount=Regex("[0-9]+").find(t)?.value?.toLongOrNull() ?: 1L
        val multiplier=when {
            t.contains("دقيق") || t.contains("minute") -> 1L
            t.contains("ساع") || t.contains("hour") -> 60L
            t.contains("يوم") || t.contains("day") -> 1440L
            t.contains("أسبوع") || t.contains("week") -> 10080L
            t.contains("شهر") || t.contains("month") -> 43200L
            t.contains("سن") || t.contains("year") -> 525600L
            else -> 1000000L
        }
        return amount*multiplier
    }
    private fun applyFilters() {
        val selected=when(typeFilter) {
            "مباشر" -> cachedVideos.filter {it.isLive}
            "القنوات","قوائم التشغيل" -> emptyList()
            else -> cachedVideos.toList()
        }
        val sorted=when(sortMode) {
            "الأكثر مشاهدة" -> selected.sortedByDescending{it.views}
            "الأحدث تقريبًا" -> selected.sortedBy{approximateAge(it.published)}
            else -> if(customTerm.isNullOrBlank() &&
                getSharedPreferences("egytube_interests",0).getInt("total",0)>0)
                selected.sortedByDescending { recommendedScore(it) }
                else selected.sortedByDescending { it.views }
        }
        if(!isWatching && customTerm.isNullOrBlank()) sectionTitle.text=homeTitle()
        adapter.replaceAll(sorted)
        extraRow.removeAllViews()
        val extra=extraItems.filter{
            when(typeFilter) {
                "القنوات" -> it is ChannelInfoItem
                "قوائم التشغيل" -> it is PlaylistInfoItem
                "الكل" -> it is ChannelInfoItem || it is PlaylistInfoItem
                else -> false
            }
        }
        extra.take(12).forEach { item ->
            val prefix=if(item is ChannelInfoItem)"قناة" else "قائمة"
            extraRow.addView(clickable(prefix+" • "+item.name,{
                resetWebFallback()
                webMode=true
                list.visibility=View.GONE
                webResults.visibility=View.VISIBLE
                webResults.loadUrl(item.url)
            }),LinearLayout.LayoutParams(-2,dp(48)).apply{rightMargin=dp(7)})
        }
        extraResults.visibility=if(extra.isEmpty())View.GONE else View.VISIBLE
        status.text="النتائج المحمّلة: "+sorted.size+
            if(extra.isNotEmpty())" • قنوات وقوائم: "+extra.size else ""
    }
    private fun chooseCountry() {
        val keys=options.keys.toList()
        val names=options.values.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("اختيار بلد المحتوى")
            .setSingleChoiceItems(names,keys.indexOf(country)) { dialog,which ->
                country=keys[which]; countryLabel=options[country] ?: country
                NewPipe.setPreferredContentCountry(ContentCountry(country))
                dialog.dismiss()
                buildUI()
                loadTrending()
            }.setNegativeButton("إلغاء",null).show()
    }
    private fun modelFrom(info:StreamInfoItem):VideoModel =
        VideoModel(
            info.url, info.name, info.uploaderName.orEmpty(),
            info.viewCount,info.duration,
            info.textualUploadDate.orEmpty(),
            info.thumbnails.maxByOrNull { if (it.width>0) it.width else 0 }?.url.orEmpty(),
            info.uploaderAvatars.firstOrNull()?.url.orEmpty(),
            info.isUploaderVerified,
            info.streamType.toString().contains("LIVE",true)
        )

    private fun displayWebFallback(term:String?, trending:Boolean) {
        extraItems.clear()
        extraResults.visibility=View.GONE
        webMode=true
        list.visibility=View.GONE
        webResults.visibility=View.VISIBLE
        moreButton.visibility=View.GONE
        val url=if(trending)
            "https://m.youtube.com/?gl="+Uri.encode(country)+"&hl=ar"
        else "https://m.youtube.com/results?search_query="+Uri.encode(term.orEmpty())+
            "&gl="+Uri.encode(country)+"&hl=ar"
        if(trending) {
            sectionTitle.text=homeTitle()
            status.text="تعذّر جلب المقترحات. عرضنا صفحة استكشاف الفيديوهات بديلًا؛ لا تمثل قائمة الأكثر مشاهدة."
        } else {
            status.text="تعذّر استخراج نتائج البحث؛ عُرضت صفحة نتائج يوتيوب داخل التطبيق كبديل."
        }
        webResults.loadUrl(url)
    }
    private fun resetWebFallback() {
        webMode=false
        webResults.visibility=View.GONE
        webResults.stopLoading()
        list.visibility=View.VISIBLE
    }
    private fun loadTrending() {
        shortSeriesSlider.root.visibility=View.VISIBLE
        categories.visibility=View.VISIBLE
        resetWebFallback()
        val token=++currentToken
        inFlight=true
        currentExtractor=null;nextPage=null;customTerm=null
        extraItems.clear()
        sectionTitle.text=homeTitle()
        status.text="جارٍ تجهيز مقترحات الفيديو..."
        moreButton.visibility=View.GONE
        worker.execute {
            try {
                NewPipe.setPreferredContentCountry(ContentCountry(country))
                val extractor=ServiceList.YouTube.kioskList.defaultKioskExtractor
                extractor.fetchPage()
                val page=extractor.initialPage
                val videos=page.items.filterIsInstance<StreamInfoItem>().map(::modelFrom)
                runOnUiThread {
                    if(isDestroyed || token!=currentToken)return@runOnUiThread
                    inFlight=false
                    currentExtractor=extractor
                    nextPage=page.nextPage
                    cachedVideos.clear();cachedVideos.addAll(videos)
                    extraItems.clear()
                    applyFilters()
                    status.text=if(videos.isEmpty()) "لا توجد فيديوهات متاحة حاليًا." else
                        "الفيديوهات المحمّلة: "+videos.size+" • اختر فئة من الأعلى"
                    moreButton.visibility=if(nextPage!=null)View.VISIBLE else View.GONE
                }
            } catch(e:Exception) {
                runOnUiThread {
                    if(token!=currentToken)return@runOnUiThread
                    inFlight=false
                    cachedVideos.clear()
                    adapter.replaceAll(emptyList())
                    displayWebFallback(null,true)
                    moreButton.visibility=View.GONE
                }
            }
        }
    }
    private fun search() {
        shortSeriesSlider.root.visibility=View.GONE
        val term=searchBox.text.toString().trim()
        searchBox.dismissDropDown()
        if(term.isBlank()) {loadTrending();return}
        val prefs=getSharedPreferences("egytube_search",0)
        val old=prefs.getString("terms","").orEmpty().split("\n").filter{it.isNotBlank()}
        prefs.edit().putString("terms",(listOf(term)+old.filter{it!=term}).take(30).joinToString("\n")).apply()
        val direct=Regex("(?i)^https://[^\\s]+\\.(mp4|m3u8|mpd)(\\?[^\\s]*)?$")
        if(direct.matches(term)) {
            pipAuthorizedSource=true
            openPlayer(VideoModel(term,Uri.parse(term).lastPathSegment ?: "فيديو شخصي","ملف فيديو",-1L,-1L,"","","",false,false))
            return
        }
        val link=Regex("https?://(?:www\\.)?(?:m\\.)?(?:youtube\\.com|youtu\\.be)/[^\\s]+",RegexOption.IGNORE_CASE).find(term)?.value
        if(link!=null) {
            pipAuthorizedSource=false
            openPlayer(VideoModel(link,"فيديو يوتيوب","", -1,-1,"","","",false,false))
            return
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchBox.windowToken,0)
        searchBox.clearFocus()
        resetWebFallback()
        val token=++currentToken
        inFlight=true;currentExtractor=null;nextPage=null;customTerm=term
        cachedVideos.clear();extraItems.clear()
        extraResults.visibility=View.GONE
        moreButton.visibility=View.GONE
        sectionTitle.text="نتائج البحث عن: "+term
        status.text="جارٍ البحث في الفيديوهات..."
        worker.execute {
            try {
                NewPipe.setPreferredContentCountry(ContentCountry(country))
                val extractor=ServiceList.YouTube.getSearchExtractor(term)
                extractor.fetchPage()
                val page=extractor.initialPage
                val videos=page.items.filterIsInstance<StreamInfoItem>().map(::modelFrom)
                val others=page.items.filter{it is ChannelInfoItem || it is PlaylistInfoItem}
                runOnUiThread {
                    if(isDestroyed || token!=currentToken)return@runOnUiThread
                    inFlight=false;currentExtractor=extractor;nextPage=page.nextPage
                    cachedVideos.clear();cachedVideos.addAll(videos)
                    extraItems.clear();extraItems.addAll(others)
                    applyFilters()
                    status.text=if(videos.isEmpty() && others.isEmpty()) "لم يتم العثور على فيديوهات." else videos.size.toString()+" نتيجة"
                    moreButton.visibility=if(nextPage!=null)View.VISIBLE else View.GONE
                }
            } catch(e:Exception) {
                runOnUiThread {
                    if(token!=currentToken)return@runOnUiThread
                    inFlight=false
                    displayWebFallback(term,false)
                }
            }
        }
    }
    private fun loadMore() {
        val ex=currentExtractor ?:return
        val pg=nextPage ?:return
        if(inFlight)return
        val token=currentToken
        inFlight=true
        moreButton.text="جارٍ التحميل..."
        worker.execute {
            try {
                val page=ex.getPage(pg)
                val videos=page.items.filterIsInstance<StreamInfoItem>().map(::modelFrom)
                runOnUiThread {
                    if(isDestroyed || token!=currentToken)return@runOnUiThread
                    inFlight=false;nextPage=page.nextPage
                    cachedVideos.addAll(videos)
                    extraItems.addAll(page.items.filter{it is ChannelInfoItem || it is PlaylistInfoItem})
                    applyFilters()
                    moreButton.text="عرض المزيد"
                    moreButton.visibility=if(nextPage!=null)View.VISIBLE else View.GONE
                }
            } catch(e:Exception) {
                runOnUiThread {
                    if(token!=currentToken)return@runOnUiThread
                    inFlight=false;moreButton.text="إعادة محاولة عرض المزيد"
                }
            }
        }
    }

    private fun openPlayer(model:VideoModel) {
        pipAuthorizedSource=model.url.matches(Regex("(?i)^https://[^\\s]+\\.(mp4|m3u8|mpd)(\\?[^\\s]*)?$"))
        // If a prior video is playing in the mini view, return the same video surface
        // to the standard player before beginning the selected video.
        if(miniActive) {
            miniActive=false
            miniHost.visibility=View.GONE
            attachPlayerToHost()
            panel.setMiniMode(false)
        }
        playingModel=model
        shortSeriesSlider.root.visibility=View.GONE
        lastRecordedUrl=null
        portraitVideo=false
        isWatching=true
        searchRow.visibility=View.GONE
        categories.visibility=View.GONE
        sectionTitle.visibility=View.GONE
        status.visibility=View.GONE
        searchTools.visibility=View.GONE
        extraResults.visibility=View.GONE
        adjustNormalPlayerHeight()
        panel.setTitle(model.title)
        panel.setSubtitle(model.channel)
        webResults.visibility=View.GONE
        list.visibility=View.VISIBLE
        panel.player.stop()
        playerHost.visibility=View.VISIBLE
        playerDetails.visibility=View.VISIBLE
        playerDetails.removeAllViews()
        watchNotice=baseText("جارٍ تجهيز الفيديو...",12f,false,Palette.SECOND).apply {
            setPadding(0,dp(5),0,dp(9))
        }
        val name=baseText(model.title,18f,true).apply { maxLines=3 }
        playerDetails.addView(name)
        playerDetails.addView(baseText(model.channel +
            (if(model.views>=0) "  •  "+Palette.count(model.views)+" مشاهدة" else ""),
            13f,false,Palette.SECOND).apply {setPadding(0,dp(6),0,dp(6))})
        val actions=HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false
            layoutDirection=View.LAYOUT_DIRECTION_RTL
        }
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(0,dp(6),0,dp(6))
        }
        row.addView(clickable("↗  مشاركة",{shareActiveVideo()}),
            LinearLayout.LayoutParams(-2,dp(46)).apply {rightMargin=dp(8)})
        row.addView(clickable("⧉  نسخ الرابط",{copyActiveVideoLink()}),
            LinearLayout.LayoutParams(-2,dp(46)).apply {rightMargin=dp(8)})
        row.addView(clickable("↓  تحميل",{downloadActiveVideo()}),
            LinearLayout.LayoutParams(-2,dp(46)).apply {rightMargin=dp(8)})
        actions.addView(row)
        playerDetails.addView(actions,LinearLayout.LayoutParams(-1,dp(62)))
        val relatedChips=HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled=false
        }
        val relatedRow=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(0,dp(8),0,dp(8))
        }
        fun relatedTab(label:String,filter:(VideoModel)->Boolean) {
            relatedRow.addView(clickable(label,{
                val matching=cachedVideos.filter(filter).filter{it.url!=model.url}
                adapter.replaceAll(matching.ifEmpty {cachedVideos.filter{it.url!=model.url}})
                status.text=label+" • "+adapter.itemCount+" فيديو"
            }),LinearLayout.LayoutParams(-2,dp(36)).apply {rightMargin=dp(8)})
        }
        relatedTab("الكل"){true}
        relatedTab("من القناة"){it.channel.isNotBlank() && it.channel==model.channel}
        relatedTab("مشابه"){categoryOf(it)==categoryOf(model)}
        relatedTab("من السلسلة"){
            val key=model.title.trim().split(Regex("\\s+")).take(2).joinToString(" ")
            key.length>5 && it.title.contains(key,true)
        }
        relatedChips.addView(relatedRow)
        playerDetails.addView(relatedChips,LinearLayout.LayoutParams(-1,dp(55)))
        playerDetails.addView(watchNotice)
        sectionTitle.text="قد يعجبك أيضًا"
        sectionTitle.visibility=View.GONE
        watchNotice.text="جارٍ تجهيز المشغل..."
        status.visibility=View.GONE
        moreButton.visibility=View.GONE
        list.scrollToPosition(0)
        sourceOptions.clear();currentQuality=-1
        val token=++currentToken
        if(pipAuthorizedSource) {
            val url=model.url
            val mime=when {
                url.contains(".m3u8",true) -> MimeTypes.APPLICATION_M3U8
                url.contains(".mpd",true) -> MimeTypes.APPLICATION_MPD
                else -> null
            }
            sourceOptions.clear()
            sourceOptions.add(StreamChoice("المصدر الأصلي",url,null,mime))
            selectQuality(0)
            return
        }
        worker.execute {
            try {
                val info=StreamInfo.getInfo(model.url)
                if(info.ageLimit>0) throw IllegalStateException("الفيديو مقيّد بالعمر")
                val opts=mutableListOf<StreamChoice>()
                info.videoStreams.filter { it.isUrl && it.content.startsWith("https://") }
                    .sortedByDescending { it.height }
                    .forEach { opts.add(StreamChoice(it.resolution+" · فيديو وصوت",it.content)) }
                val audio=info.audioStreams.filter { it.isUrl && it.content.startsWith("https://") }
                    .maxByOrNull { it.averageBitrate }
                info.videoOnlyStreams.filter { it.isUrl && it.content.startsWith("https://") }
                    .sortedByDescending { it.height }
                    .forEach { video ->
                        if(audio!=null) opts.add(StreamChoice(video.resolution+" · عالي الوضوح",video.content,audio.content))
                    }
                if(!info.hlsUrl.isNullOrBlank()) opts.add(StreamChoice("HLS · تلقائي",info.hlsUrl,null,MimeTypes.APPLICATION_M3U8))
                if(!info.dashMpdUrl.isNullOrBlank()) opts.add(StreamChoice("DASH · تلقائي",info.dashMpdUrl,null,MimeTypes.APPLICATION_MPD))
                runOnUiThread {
                    if(isDestroyed || token!=currentToken || !isWatching)return@runOnUiThread
                    panel.setTitle(info.name)
                    sourceOptions.clear();sourceOptions.addAll(opts)
                    if(opts.isEmpty())status.text="لم يُعثر على مصدر تشغيل متاح لهذا الفيديو."
                    else selectQuality(0)
                }
            } catch(e:Exception) {
                runOnUiThread { if(token==currentToken)status.text="تعذّر فتح الفيديو: "+(e.message?:"غير معروف") }
            }
        }
    }

    private fun youtubeVideoId(link:String):String? {
        val uri=try {Uri.parse(link)} catch(_:Exception) {return null}
        val host=uri.host.orEmpty().lowercase(Locale.ROOT)
        if(host!="youtube.com" && !host.endsWith(".youtube.com") && host!="youtu.be")return null
        val id=when {
            host=="youtu.be" -> uri.pathSegments.firstOrNull()
            uri.path.orEmpty().startsWith("/shorts/") -> uri.pathSegments.getOrNull(1)
            uri.path.orEmpty().startsWith("/live/") -> uri.pathSegments.getOrNull(1)
            uri.path.orEmpty().startsWith("/embed/") -> uri.pathSegments.getOrNull(1)
            else -> uri.getQueryParameter("v")
        }
        return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    }
    private fun shareLink(item:VideoModel):String {
        val id=youtubeVideoId(item.url)
        return if(id==null)item.url else "https://egyshow.com/watch?egy="+id
    }
    private fun copyActiveVideoLink() {
        val item=playingModel ?:return
        val value=shareLink(item)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("EGYTUBE video link",value))
        Toast.makeText(this,"تم نسخ رابط المشاركة",Toast.LENGTH_SHORT).show()
    }
    private fun shareActiveVideo() {
        val item=playingModel ?:return
        val intent=Intent(Intent.ACTION_SEND).apply {
            type="text/plain"
            putExtra(Intent.EXTRA_TEXT,item.title+"\n"+shareLink(item))
        }
        startActivity(Intent.createChooser(intent,"مشاركة الفيديو"))
    }
    private fun downloadActiveVideo() {
        val item=playingModel ?:return
        val url=item.url.trim()
        val uri=try {Uri.parse(url)}catch (_:Exception){null}
        val path=uri?.path?.lowercase(Locale.ROOT).orEmpty()
        // DownloadManager saves normal authorized progressive files. A YouTube watch page
        // or a media playlist (HLS/DASH) must never be saved as a fake MP4 file.
        val direct=uri?.scheme=="https" &&
            (path.endsWith(".mp4") || path.endsWith(".webm") || path.endsWith(".m4v"))
        val host=uri?.host.orEmpty().lowercase(Locale.ROOT)
        val youtube=host=="youtu.be" || host=="youtube.com" || host.endsWith(".youtube.com") ||
            host=="googlevideo.com" || host.endsWith(".googlevideo.com")
        if(!direct || youtube) {
            android.app.AlertDialog.Builder(this)
                .setTitle("تنزيل الفيديو")
                .setMessage("الحفظ المباشر متاح لملفات MP4 وWebM المصرح لك بتنزيلها. لا يدعم هذا الزر تنزيل فيديوهات يوتيوب أو بث HLS/DASH. يمكنك استخدام خيارات التنزيل الرسمية من الخدمة عند توفرها.")
                .setPositiveButton("حسنًا",null).show()
            return
        }
        android.app.AlertDialog.Builder(this)
            .setTitle("حفظ الفيديو على الجهاز")
            .setMessage("سيُحفظ الفيديو في مجلد التنزيلات على هاتفك. تابع فقط إذا كان لك حق تنزيل هذا المحتوى.")
            .setNegativeButton("إلغاء",null)
            .setPositiveButton("تحميل") { _,_ ->
                try {
                    val extension=when {
                        path.endsWith(".webm")->"webm"
                        path.endsWith(".m4v")->"m4v"
                        else->"mp4"
                    }
                    val safe=item.title.filter{it.isLetterOrDigit() || it==' ' || it=='-' || it=='_' }
                        .take(62).trim().ifBlank{"EGYTUBE_Video"}
                    val name=safe+"_"+System.currentTimeMillis()+"."+extension
                    val mime=if(extension=="webm")"video/webm" else "video/mp4"
                    val request=DownloadManager.Request(uri!!)
                        .setTitle(safe)
                        .setDescription("EGYTUBE • حفظ ملف فيديو")
                        .setMimeType(mime)
                        .setAllowedOverMetered(true)
                        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,name)
                    val manager=getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    val downloadId=manager.enqueue(request)
                    DownloadCenter.remember(this,downloadId,item.title)
                    Toast.makeText(this,"بدأ تنزيل الفيديو إلى مجلد التنزيلات",Toast.LENGTH_LONG).show()
                }catch(e:Exception){
                    Toast.makeText(this,"تعذّر بدء التنزيل: "+e.javaClass.simpleName,Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun chooseQuality() {
        if(sourceOptions.isEmpty()) {
            Toast.makeText(this,"لا توجد جودات متاحة",Toast.LENGTH_SHORT).show()
            return
        }
        android.app.AlertDialog.Builder(this).setTitle("جودة الفيديو")
            .setSingleChoiceItems(sourceOptions.map { it.title }.toTypedArray(),currentQuality) { dialog,which ->
                selectQuality(which);dialog.dismiss()
            }.setNegativeButton("إلغاء",null).show()
    }
    private fun selectQuality(which:Int) {
        if(which !in sourceOptions.indices)return
        val option=sourceOptions[which]
        val at=if(currentQuality>=0)panel.player.currentPosition else 0L
        currentQuality=which
        val http=DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        val ds=DefaultDataSource.Factory(this,http)
        fun progressive(url:String):MediaSource=
            ProgressiveMediaSource.Factory(ds).createMediaSource(MediaItem.fromUri(url))
        val media=when(option.mime) {
            MimeTypes.APPLICATION_M3U8 -> HlsMediaSource.Factory(ds).createMediaSource(
                MediaItem.Builder().setUri(option.url).setMimeType(MimeTypes.APPLICATION_M3U8).build())
            MimeTypes.APPLICATION_MPD -> DashMediaSource.Factory(ds).createMediaSource(
                MediaItem.Builder().setUri(option.url).setMimeType(MimeTypes.APPLICATION_MPD).build())
            else -> if(option.audio!=null) MergingMediaSource(progressive(option.url),progressive(option.audio))
                else progressive(option.url)
        }
        status.text="الجودة: "+option.title
        panel.setSource(media,at)
    }

    private fun attachPlayerToHost() {
        (panel.root.parent as? ViewGroup)?.removeView(panel.root)
        playerHost.addView(panel.root,LinearLayout.LayoutParams(-1,dp(233)))
    }
    private fun leavePlayer() {
        if(!isWatching)return
        if(panel.exitFullIfNeeded())return
        if(playingModel!=null && panel.player.mediaItemCount>0) {
            miniActive=true
            isWatching=false
            panel.setMiniMode(true)
            (panel.root.parent as? ViewGroup)?.removeView(panel.root)
            miniSlot.addView(panel.root,FrameLayout.LayoutParams(-1,-1))
            miniTitle.text=playingModel?.title ?: "EGYTUBE"
            miniHost.visibility=View.VISIBLE
        } else {
            ++currentToken
            panel.player.stop()
            isWatching=false
            playingModel=null
        }
        playerHost.visibility=View.GONE
        playerDetails.visibility=View.GONE
        searchRow.visibility=View.GONE
        categories.visibility=View.VISIBLE
        sectionTitle.visibility=View.VISIBLE
        status.visibility=View.VISIBLE
        searchTools.visibility=View.VISIBLE
        shortSeriesSlider.root.visibility=if(customTerm.isNullOrBlank())View.VISIBLE else View.GONE
        webResults.visibility=if(webMode)View.VISIBLE else View.GONE
        list.visibility=if(webMode)View.GONE else View.VISIBLE
        sectionTitle.text=if(customTerm.isNullOrBlank()) homeTitle()
            else "نتائج البحث عن: "+customTerm
        moreButton.visibility=if(nextPage!=null)View.VISIBLE else View.GONE
    }
    private fun expandMini() {
        if(!miniActive || inPip)return
        miniActive=false
        miniHost.visibility=View.GONE
        attachPlayerToHost()
        panel.setMiniMode(false)
        isWatching=true
        playerHost.visibility=View.VISIBLE
        playerDetails.visibility=View.VISIBLE
        searchRow.visibility=View.GONE
        categories.visibility=View.GONE
        sectionTitle.visibility=View.GONE
        status.visibility=View.GONE
        searchTools.visibility=View.GONE
        extraResults.visibility=View.GONE
        adjustNormalPlayerHeight()
        webResults.visibility=View.GONE
        list.visibility=View.VISIBLE
        moreButton.visibility=View.GONE
    }
    private fun closeMini() {
        if(!miniActive)return
        miniActive=false
        miniHost.visibility=View.GONE
        (panel.root.parent as? ViewGroup)?.removeView(panel.root)
        playerHost.addView(panel.root,LinearLayout.LayoutParams(-1,dp(233)))
        panel.setMiniMode(false)
        panel.player.stop()
        playingModel=null
    }
    private fun enterPip() {
        if(!pipAuthorizedSource) {
            Toast.makeText(this,"PiP متاح للمحتوى المصرح بتشغيله فقط؛ فيديوهات YouTube غير مضمونة أو مدعومة هنا.",Toast.LENGTH_LONG).show()
            return
        }
        if(Build.VERSION.SDK_INT<26 || !this::panel.isInitialized)return
        if(!(isWatching || miniActive) || panel.player.mediaItemCount<1)return
        val location=IntArray(2)
        panel.root.getLocationOnScreen(location)
        val rect=Rect(location[0],location[1],
            location[0]+panel.root.width,location[1]+panel.root.height)
        val builder=PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16,9))
        if(!rect.isEmpty)builder.setSourceRectHint(rect)
        try {
            if(Build.VERSION.SDK_INT>=31)builder.setAutoEnterEnabled(true)
            setPictureInPictureParams(builder.build())
            if(!isInPictureInPictureMode)enterPictureInPictureMode(builder.build())
        } catch (_:Exception) {
            Toast.makeText(this,"ميزة النافذة العائمة غير متاحة على هذا الجهاز",Toast.LENGTH_SHORT).show()
        }
    }
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if(Build.VERSION.SDK_INT>=26 && pipAuthorizedSource && (isWatching || miniActive) &&
            this::panel.isInitialized && panel.player.isPlaying)enterPip()
        else if(this::panel.isInitialized && !pipAuthorizedSource)panel.player.pause()
    }
    override fun onPictureInPictureRequested():Boolean {
        if(Build.VERSION.SDK_INT>=26 && pipAuthorizedSource && (isWatching || miniActive)) {
            enterPip()
            return true
        }
        return super.onPictureInPictureRequested()
    }
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode:Boolean,newConfig:Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode,newConfig)
        inPip=isInPictureInPictureMode
        if(isInPictureInPictureMode) {
            panel.setPipMode(true)
            miniHost.visibility=View.GONE
            (panel.root.parent as? ViewGroup)?.removeView(panel.root)
            pipHost.addView(panel.root,FrameLayout.LayoutParams(-1,-1))
            pipHost.visibility=View.VISIBLE
            root.visibility=View.GONE
        } else {
            pipHost.visibility=View.GONE
            root.visibility=View.VISIBLE
            miniActive=false
            miniHost.visibility=View.GONE
            attachPlayerToHost()
            panel.setPipMode(false)
            panel.setMiniMode(false)
            isWatching=true
            playerHost.visibility=View.VISIBLE
            playerDetails.visibility=View.VISIBLE
            searchRow.visibility=View.GONE
            categories.visibility=View.GONE
            webResults.visibility=View.GONE
            list.visibility=View.VISIBLE
        }
    }
    private fun resizePlayer(fullscreen:Boolean) {
        header.visibility=if(fullscreen)View.GONE else View.VISIBLE
        footer.visibility=if(fullscreen)View.GONE else View.VISIBLE
        searchRow.visibility=View.GONE
        categories.visibility=View.GONE
        sectionTitle.visibility=View.GONE
        status.visibility=View.GONE
        searchTools.visibility=View.GONE
        extraResults.visibility=View.GONE
        moreButton.visibility=View.GONE
        playerDetails.visibility=if(fullscreen)View.GONE else if(isWatching)View.VISIBLE else View.GONE
        list.visibility=if(fullscreen || webMode)View.GONE else View.VISIBLE
        webResults.visibility=View.GONE
        if(fullscreen) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            playerHost.layoutParams=LinearLayout.LayoutParams(-1,0,1f)
            panel.root.layoutParams=LinearLayout.LayoutParams(-1,-1)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            playerHost.layoutParams=LinearLayout.LayoutParams(-1,-2)
            panel.root.layoutParams=LinearLayout.LayoutParams(-1,normalVideoHeight())
        }
        if(Build.VERSION.SDK_INT>=35)root.requestApplyInsets()
    }
    override fun onConfigurationChanged(config:Configuration) {super.onConfigurationChanged(config)}
    override fun onStop() {
        if(this::panel.isInitialized && !pipAuthorizedSource)panel.player.pause()
        super.onStop()
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if(isWatching)leavePlayer()
        else if(webMode) {
            if(webResults.canGoBack())webResults.goBack()
            else {resetWebFallback();customTerm=null;loadTrending()}
        }
        else if(miniActive)closeMini()
        else super.onBackPressed()
    }
    override fun onDestroy() {
        ++currentToken
        if(this::shortSeriesSlider.isInitialized)shortSeriesSlider.close()
        worker.shutdownNow()
        panel.release()
        advertising.dispose()
        webResults.destroy()
        super.onDestroy()
    }
}
private data class StreamChoice(val title:String,val url:String,val audio:String?=null,val mime:String?=null)
