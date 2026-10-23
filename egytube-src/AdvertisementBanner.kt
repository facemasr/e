package com.personal.ytplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Message
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Four independent, labeled ad placements.
 * Only actual clicks open a destination in the Android default browser.
 * Never transform impressions, close actions, or page-loaded scripts into clicks.
 */
internal class AdvertisementBanner(
    private val host:Activity,
    val placement:Placement=Placement.PLAYER_320
):LinearLayout(host) {
    enum class Placement(val width:Int,val height:Int) {
        HOME_RECTANGLE(300,250),
        HOME_WIDE(468,60),
        PLAYER_320(320,50),
        FEED_SCRIPT(320,90)
    }
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private var loaded=false
    private var dismissed=false
    private var lastTouch=0L
    private val web=WebView(host)
    private fun recentlyTapped()=SystemClock.elapsedRealtime()-lastTouch < 1500L
    private fun openDefaultBrowser(url:String){
        try{
            val target=android.net.Uri.parse(url)
            if(target.scheme !in listOf("https","http"))return
            host.startActivity(Intent(Intent.ACTION_VIEW,target)
                .addCategory(Intent.CATEGORY_BROWSABLE))
        }catch(_:Exception){}
    }
    init {
        orientation=VERTICAL
        gravity=Gravity.CENTER_HORIZONTAL
        setPadding(dp(3),dp(2),dp(3),dp(5))
        background=Palette.round(0xfff5f5f5.toInt(),dp(10).toFloat())
        val labelRow=LinearLayout(host).apply {
            orientation=HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        labelRow.addView(TextView(host).apply {
            text="إعلان"
            textSize=10.5f
            setTextColor(0xff5d626d.toInt())
            gravity=Gravity.CENTER_VERTICAL
        },LayoutParams(0,dp(24),1f))
        labelRow.addView(TextView(host).apply {
            text="إغلاق ×"
            textSize=12f
            setTextColor(0xff4b5059.toInt())
            gravity=Gravity.CENTER
            setPadding(dp(9),0,dp(9),0)
            contentDescription="إغلاق الإعلان"
            setOnClickListener {
                dismissed=true
                hidePlacement()
            }
        },LayoutParams(-2,dp(24)))
        addView(labelRow,LayoutParams(-1,dp(24)))

        val container=FrameLayout(host).apply {
            setBackgroundColor(Color.WHITE)
            clipChildren=true
            clipToPadding=true
        }
        web.setBackgroundColor(Color.TRANSPARENT)
        web.settings.apply {
            javaScriptEnabled=true
            domStorageEnabled=true
            allowFileAccess=false
            allowContentAccess=false
            javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(true)
            mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if(Build.VERSION.SDK_INT>=26)safeBrowsingEnabled=true
        }
        web.setOnTouchListener{_,event->
            if(event.actionMasked==MotionEvent.ACTION_DOWN ||
                event.actionMasked==MotionEvent.ACTION_UP)lastTouch=SystemClock.elapsedRealtime()
            false
        }
        web.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean{
                if(request==null)return true
                val pressed=request.hasGesture() || recentlyTapped()
                if(!request.isForMainFrame) {
                    if(pressed){openDefaultBrowser(request.url.toString());return true}
                    return false
                }
                if(pressed)openDefaultBrowser(request.url.toString())
                return true
            }
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onCreateWindow(view:WebView?,dialog:Boolean,userGesture:Boolean,resultMsg:Message?):Boolean{
                if(!userGesture || !recentlyTapped())return false
                val popup=WebView(host)
                popup.webViewClient=object:WebViewClient(){
                    override fun shouldOverrideUrlLoading(v:WebView?,r:WebResourceRequest?):Boolean{
                        if(r?.isForMainFrame==true)openDefaultBrowser(r.url.toString())
                        popup.post{popup.destroy()}
                        return true
                    }
                    override fun onPageStarted(v:WebView?,url:String?,icon:android.graphics.Bitmap?){
                        if(!url.isNullOrBlank() && url!="about:blank")openDefaultBrowser(url)
                        popup.post{popup.destroy()}
                    }
                }
                val transport=resultMsg?.obj as? WebView.WebViewTransport ?:return false
                transport.webView=popup
                resultMsg.sendToTarget()
                return true
            }
        }
        container.addView(web,FrameLayout.LayoutParams(-1,-1))
        addView(container,LayoutParams(-1,dp(placement.height)))
        visibility=View.GONE
    }
    private fun creative():String=when(placement){
        Placement.HOME_RECTANGLE -> """
            <ins style="width:300px;height:250px" data-width="300" data-height="250"
              class="zbdb0fb2d79" data-domain="//data527.click"
              data-affquery="/6782610a11f54f817b52/bdb0fb2d79/?placementName=default"></ins>
            <script src="https://data527.click/js/responsive.js" async></script>
        """.trimIndent()
        Placement.HOME_WIDE -> """
            <ins style="width:468px;height:60px" data-width="468" data-height="60"
              class="efcafa07e48" data-domain="//data527.click"
              data-affquery="/24c433963a2c516b9146/fcafa07e48/?placementName=default"></ins>
            <script src="https://data527.click/js/responsive.js" async></script>
        """.trimIndent()
        Placement.PLAYER_320 -> """
            <ins style="width:320px;height:50px" data-width="320" data-height="50"
              class="w34be394bb1" data-domain="//data527.click"
              data-affquery="/e3ed80826e23881d9411/34be394bb1/?placementName=default"></ins>
            <script src="https://data527.click/js/responsive.js" async></script>
        """.trimIndent()
        Placement.FEED_SCRIPT -> """
            <script type="text/javascript"
              src="https://bonepa.com/2de39a55427445e4c1ee/23caae49eb/?placementName=default"></script>
        """.trimIndent()
    }
    private fun page():String {
        val contentWidth=placement.width
        val contentHeight=placement.height
        val prelude=if(placement==Placement.HOME_WIDE)
          "<style>#creative{transform:scale(min(1,calc(100vw / 468px)));transform-origin:top center}</style>"
          else ""
        return "<!doctype html><html><head><meta charset='UTF-8'>"+
            "<meta name='viewport' content='width=device-width,initial-scale=1'>"+
            "<style>html,body{margin:0;padding:0;width:100%;height:100%;background:transparent;overflow:hidden}"+
            "#creative{width:"+contentWidth+"px;height:"+contentHeight+"px;position:relative;margin:0 auto}</style>"+
            prelude+"</head><body><div id='creative'>"+creative()+"</div></body></html>"
    }
    fun display(){
        if(dismissed)return
        visibility=View.VISIBLE
        web.onResume()
        if(!loaded){
            loaded=true
            val base=if(placement==Placement.FEED_SCRIPT)"https://bonepa.com/"
                else "https://data527.click/"
            web.loadDataWithBaseURL(base,page(),"text/html","UTF-8",null)
        }
    }
    fun hidePlacement(){visibility=View.GONE;web.onPause()}
    fun dispose(){
        web.stopLoading()
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
    }
}
