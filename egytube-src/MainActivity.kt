package com.personal.ytplayer

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.view.WindowInsets
import android.webkit.WebView
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
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
    private lateinit var searchBox:EditText
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
        window.statusBarColor=Palette.BG
        window.navigationBarColor=Palette.BG
        buildUI()
        if(intent?.action==Intent.ACTION_SEND && intent.type=="text/plain") {
            searchBox.setText(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            search()
        } else loadTrending()
    }

    private fun buildUI() {
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setBackgroundColor(Palette.BG)
        }
        header=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(12),dp(5),dp(12),dp(2))
        }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.egytube_logo)
            scaleType=ImageView.ScaleType.FIT_CENTER
            contentDescription="EGYTUBE"
        }, LinearLayout.LayoutParams(dp(42),dp(42)))
        header.addView(baseText("EGYTUBE",21f,true).apply {
            setPadding(dp(8),0,0,0)
        },LinearLayout.LayoutParams(0,-2,1f))
        header.addView(clickable("⌕", { searchBox.requestFocus();(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchBox,InputMethodManager.SHOW_IMPLICIT) }))
        header.addView(clickable("⟳",{ loadTrending() }).apply {
            setBackgroundColor(Color.TRANSPARENT)
        })
        root.addView(header, LinearLayout.LayoutParams(-1,dp(58)))

        searchRow=LinearLayout(this).apply {
            gravity=Gravity.CENTER_VERTICAL
            orientation=LinearLayout.HORIZONTAL
            setPadding(dp(12),dp(3),dp(12),dp(5))
        }
        searchBox=EditText(this).apply {
            hint="ابحث عن الفيديوهات أو ألصق رابطًا"
            textSize=14.5f
            setTextColor(Palette.WHITE)
            setHintTextColor(Palette.SECOND)
            background=Palette.round(Palette.SURFACE,dp(16).toFloat())
            setPadding(dp(14),0,dp(14),0)
            isSingleLine=true
            imeOptions=EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _,_,_ -> search();true }
        }
        searchRow.addView(searchBox,LinearLayout.LayoutParams(0,dp(48),1f))
        searchRow.addView(clickable("بحث",{search()}),LinearLayout.LayoutParams(dp(69),dp(45)).apply { leftMargin=dp(8) })
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
            "الرائج" to "", "موسيقى" to "موسيقى", "ألعاب" to "الألعاب",
            "تقنية" to "تكنولوجيا", "رياضة" to "الرياضة",
            "أفلام" to "أفلام"
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
        panel=VideoPlaybackPanel(this,{ leavePlayer() },{ chooseQuality() },{ fullscreen -> resizePlayer(fullscreen) })
        panel.playbackError={ error -> runOnUiThread { status.text=error;status.visibility=View.VISIBLE } }
        playerHost=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            visibility=View.GONE
        }
        playerHost.addView(panel.root,LinearLayout.LayoutParams(-1,dp(233)))
        root.addView(playerHost,LinearLayout.LayoutParams(-1,-2))
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
            gravity=Gravity.CENTER_VERTICAL
            background=Palette.round(0xff111724.toInt(),0f)
        }
        fun tab(t:String,go:()->Unit) {
            footer.addView(baseText(t,13.5f,true).apply {
                gravity=Gravity.CENTER
                setOnClickListener { go() }
            },LinearLayout.LayoutParams(0,dp(55),1f))
        }
        tab("⌂  الرئيسية"){leavePlayer();loadTrending()}
        tab("⌕  البحث"){leavePlayer();searchBox.requestFocus();(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchBox,InputMethodManager.SHOW_IMPLICIT)}
        footer.addView(ImageView(this).apply {
            setImageResource(R.drawable.egytube_logo)
            scaleType=ImageView.ScaleType.FIT_CENTER
            setOnClickListener {leavePlayer();loadTrending()}
        },LinearLayout.LayoutParams(dp(45),dp(45)))
        tab("◉  الرائج"){leavePlayer();loadTrending()}
        root.addView(footer,LinearLayout.LayoutParams(-1,dp(58)))
        setContentView(root)
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

    private fun leavePlayer() {
        if(!isWatching)return
        if(panel.exitFullIfNeeded())return
        ++currentToken
        panel.player.stop()
        isWatching=false
        playingModel=null
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
        if(isWatching)leavePlayer() else super.onBackPressed()
    }
    override fun onDestroy() {
        ++currentToken
        worker.shutdownNow()
        panel.release()
        webResults.destroy()
        super.onDestroy()
    }
}
private data class StreamChoice(val title:String,val url:String,val audio:String?=null,val mime:String?=null)
