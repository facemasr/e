package com.personal.ytplayer

import android.app.Activity
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
    private lateinit var extraRow:LinearLayout
    private var typeFilter="الكل"
    private var sortMode="الملاءمة"
    private lateinit var categories:HorizontalScrollView
    private lateinit var sectionTitle:TextView
    private lateinit var status:TextView
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
        if(intent?.action==Intent.ACTION_SEND && intent.type=="text/plain") {
            searchBox.setText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            search()
        } else loadTrending()
    }

    private fun buildUI() {
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
            "الكل" to "", "موسيقى" to "موسيقى", "ألعاب" to "الألعاب",
            "مباشر" to "بث مباشر", "أخبار" to "الأخبار",
            "رياضة" to "الرياضة", "أفلام" to "أفلام"
        )) {
            val term=entry.second
            chips.addView(clickable(entry.first,{
                if(term.isEmpty())loadTrending()
                else {
                    searchBox.setText(term)
                    search()
                }
            }),LinearLayout.LayoutParams(-2,dp(40)).apply { rightMargin=dp(7) })
        }
        categories.addView(chips)
        root.addView(categories,LinearLayout.LayoutParams(-1,dp(49)))

        sectionTitle=baseText("الرائج في "+countryLabel,19f,true).apply {
            setPadding(dp(17),dp(12),dp(10),dp(4))
        }
        root.addView(sectionTitle)
        status=baseText("جارٍ تحميل الفيديوهات الرائجة...",12.5f,false,Palette.SECOND).apply {
            setPadding(dp(17),0,dp(17),dp(8))
        }
        root.addView(status)
        val searchTools=LinearLayout(this).apply {
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
            { expandMini() }
        )
        panel.playbackError={ error -> runOnUiThread { status.text=error;status.visibility=View.VISIBLE } }
        playerHost=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=View.GONE
        }
        playerHost.addView(panel.root,LinearLayout.LayoutParams(-1,dp(233)))
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
        miniHost=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=View.GONE
            background=Palette.round(Color.WHITE,dp(14).toFloat())
            elevation=dp(12).toFloat()
            clipToOutline=true
        }
        miniSlot=FrameLayout(this).apply {setBackgroundColor(Color.BLACK)}
        miniHost.addView(miniSlot,LinearLayout.LayoutParams(-1,dp(126)))
        val miniBar=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(5),0,dp(3),0)
        }
        miniTitle=baseText("EGYTUBE",11f,true).apply {
            maxLines=1
            ellipsize=android.text.TextUtils.TruncateAt.END
            setOnClickListener { expandMini() }
        }
        miniBar.addView(miniTitle,LinearLayout.LayoutParams(0,-1,1f))
        miniBar.addView(baseText("▣",17f,true).apply {
            gravity=Gravity.CENTER
            contentDescription="تكبير الفيديو"
            setOnClickListener { expandMini() }
        },LinearLayout.LayoutParams(dp(32),dp(37)))
        miniBar.addView(baseText("×",23f,true).apply {
            gravity=Gravity.CENTER
            contentDescription="إغلاق المشغل المصغر"
            setOnClickListener {closeMini()}
        },LinearLayout.LayoutParams(dp(33),dp(37)))
        miniHost.addView(miniBar,LinearLayout.LayoutParams(-1,dp(39)))
        screen.addView(miniHost,FrameLayout.LayoutParams(dp(228),dp(165),
            Gravity.BOTTOM or Gravity.END).apply {
                bottomMargin=dp(79);rightMargin=dp(12)
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

    private fun chooseCountry() {
        val keys=options.keys.toList()
        val names=options.values.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("اختر البلد للفيديوهات الرائجة")
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
        webMode=true
        list.visibility=View.GONE
        webResults.visibility=View.VISIBLE
        moreButton.visibility=View.GONE
        val url=if(trending)
            "https://m.youtube.com/?gl="+Uri.encode(country)+"&hl=ar"
        else "https://m.youtube.com/results?search_query="+Uri.encode(term.orEmpty())+
            "&gl="+Uri.encode(country)+"&hl=ar"
        if(trending) {
            sectionTitle.text="استكشاف الفيديوهات في "+countryLabel
            status.text="تعذّر تحميل قائمة الرائج من محرك الجلب. هذه صفحة استكشاف يوتيوب، وليست ترتيبًا رسميًا للأكثر بحثًا."
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
        resetWebFallback()
        val token=++currentToken
        inFlight=true
        currentExtractor=null;nextPage=null;customTerm=null
        sectionTitle.text="الرائج في "+countryLabel
        status.text="جارٍ جلب الفيديوهات الرائجة في "+countryLabel
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
                    adapter.replaceAll(videos)
                    status.text=if(videos.isEmpty()) "لا توجد فيديوهات رائجة متاحة حاليًا." else
                        "فيديوهات رائجة في "+countryLabel+" • "+videos.size+" نتيجة"
                    moreButton.visibility=if(nextPage!=null)View.VISIBLE else View.GONE
                }
            } catch(e:Exception) {
                runOnUiThread {
                    if(token!=currentToken)return@runOnUiThread
                    inFlight=false
                    adapter.replaceAll(emptyList())
                    displayWebFallback(null,true)
                    moreButton.visibility=View.GONE
                }
            }
        }
    }
    private fun search() {
        val term=searchBox.text.toString().trim()
        if(term.isBlank()) {loadTrending();return}
        val link=Regex("https?://(?:www\\.)?(?:m\\.)?(?:youtube\\.com|youtu\\.be)/[^\\s]+",RegexOption.IGNORE_CASE).find(term)?.value
        if(link!=null) {
            openPlayer(VideoModel(link,"فيديو يوتيوب","", -1,-1,"","","",false,false))
            return
        }
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchBox.windowToken,0)
        searchBox.clearFocus()
        resetWebFallback()
        val token=++currentToken
        inFlight=true;currentExtractor=null;nextPage=null;customTerm=term
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
                runOnUiThread {
                    if(isDestroyed || token!=currentToken)return@runOnUiThread
                    inFlight=false;currentExtractor=extractor;nextPage=page.nextPage
                    adapter.replaceAll(videos)
                    status.text=if(videos.isEmpty()) "لم يتم العثور على فيديوهات." else videos.size.toString()+" نتيجة"
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
                    adapter.append(videos)
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
        // If a prior video is playing in the mini view, return the same video surface
        // to the standard player before beginning the selected video.
        if(miniActive) {
            miniActive=false
            miniHost.visibility=View.GONE
            attachPlayerToHost()
            panel.setMiniMode(false)
        }
        playingModel=model
        isWatching=true
        searchRow.visibility=View.GONE
        categories.visibility=View.GONE
        panel.setTitle(model.title)
        webResults.visibility=View.GONE
        list.visibility=View.VISIBLE
        panel.player.stop()
        playerHost.visibility=View.VISIBLE
        playerDetails.visibility=View.VISIBLE
        playerDetails.removeAllViews()
        val name=baseText(model.title,18f,true).apply { maxLines=3 }
        playerDetails.addView(name)
        playerDetails.addView(baseText(model.channel +
            (if(model.views>=0) "  •  "+Palette.count(model.views)+" مشاهدة" else ""),
            13f,false,Palette.SECOND).apply {setPadding(0,dp(6),0,dp(6))})
        val actions=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        actions.addView(clickable("مشاركة ↗") {
            val intent=Intent(Intent.ACTION_SEND).apply {
                type="text/plain";putExtra(Intent.EXTRA_TEXT,model.url)
            }
            startActivity(Intent.createChooser(intent,"مشاركة الفيديو"))
        })
        actions.addView(clickable("نسخ الرابط") {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText("رابط الفيديو",model.url))
            Toast.makeText(this,"تم نسخ الرابط",Toast.LENGTH_SHORT).show()
        },LinearLayout.LayoutParams(-2,-2).apply {leftMargin=dp(10)})
        playerDetails.addView(actions)
        sectionTitle.text="قد يعجبك أيضًا"
        sectionTitle.visibility=View.VISIBLE
        status.text="جارٍ تجهيز المشغل..."
        status.visibility=View.VISIBLE
        moreButton.visibility=View.GONE
        list.scrollToPosition(0)
        sourceOptions.clear();currentQuality=-1
        val token=++currentToken
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
        searchRow.visibility=View.VISIBLE
        categories.visibility=View.VISIBLE
        webResults.visibility=if(webMode)View.VISIBLE else View.GONE
        list.visibility=if(webMode)View.GONE else View.VISIBLE
        sectionTitle.text=if(customTerm.isNullOrBlank()) "الرائج في "+countryLabel
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
        if(Build.VERSION.SDK_INT>=26 && (isWatching || miniActive) &&
            this::panel.isInitialized && panel.player.isPlaying)enterPip()
    }
    override fun onPictureInPictureRequested():Boolean {
        if(Build.VERSION.SDK_INT>=26 && (isWatching || miniActive)) {
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
        sectionTitle.visibility=if(fullscreen)View.GONE else View.VISIBLE
        status.visibility=if(fullscreen)View.GONE else View.VISIBLE
        playerDetails.visibility=if(fullscreen)View.GONE else if(isWatching)View.VISIBLE else View.GONE
        list.visibility=if(fullscreen || webMode)View.GONE else View.VISIBLE
        webResults.visibility=if(fullscreen || isWatching || !webMode)View.GONE else View.VISIBLE
        if(Build.VERSION.SDK_INT>=35)root.requestApplyInsets()
        if(fullscreen) {
            window.decorView.systemUiVisibility=(View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            playerHost.layoutParams=LinearLayout.LayoutParams(-1,0,1f)
            panel.root.layoutParams=LinearLayout.LayoutParams(-1,-1)
        } else {
            window.decorView.systemUiVisibility=0
            playerHost.layoutParams=LinearLayout.LayoutParams(-1,-2)
            panel.root.layoutParams=LinearLayout.LayoutParams(-1,dp(233))
        }
    }
    override fun onConfigurationChanged(config:Configuration) {super.onConfigurationChanged(config)}
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if(isWatching)leavePlayer()
        else if(miniActive)closeMini()
        else super.onBackPressed()
    }
    override fun onDestroy() {
        ++currentToken
        worker.shutdownNow()
        panel.release()
        advertising.dispose()
        webResults.destroy()
        super.onDestroy()
    }
}
private data class StreamChoice(val title:String,val url:String,val audio:String?=null,val mime:String?=null)
