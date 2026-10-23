package com.personal.ytplayer

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * The exact production EGYSHOW watch player, not an imitation.
 * Only the player surface is WebView: search, cards, browsing & navigation remain native.
 * The page owns its ads, playback settings, servers, episode navigation and access gates.
 * Important: this surface is ONLY for EGYSHOW media; YouTube uses the separate native player.
 */
internal class OriginalEgyshowPlayer(private val activity:Activity) {
    val root=FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        visibility=View.GONE
    }
    val web=WebView(activity)
    private var fullscreenView:View?=null
    private var fullscreenCallback:WebChromeClient.CustomViewCallback?=null
    private var destroyed=false
    private var lastUrl=""
    private fun browser(url:String){
        try{
            val uri=Uri.parse(url)
            if(uri.scheme=="https" || uri.scheme=="http")
                activity.startActivity(Intent(Intent.ACTION_VIEW,uri).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch(_:Exception){}
    }
    init {
        web.setBackgroundColor(Color.BLACK)
        web.settings.apply {
            javaScriptEnabled=true
            domStorageEnabled=true
            databaseEnabled=true
            loadsImagesAutomatically=true
            mediaPlaybackRequiresUserGesture=false
            javaScriptCanOpenWindowsAutomatically=false
            setSupportMultipleWindows(true)
            allowFileAccess=false
            allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if(Build.VERSION.SDK_INT>=26)safeBrowsingEnabled=true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web,true)
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean {
                if(request==null || !request.isForMainFrame)return false
                val url=request.url ?:return true
                val host=url.host.orEmpty().lowercase()
                if(url.scheme!="https")return true
                if(host=="egyshow.com" || host=="www.egyshow.com")return false
                if(request.hasGesture())browser(url.toString())
                return true
            }
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onShowCustomView(view:View?,callback:CustomViewCallback?) {
                if(view==null || fullscreenView!=null)return
                fullscreenView=view;fullscreenCallback=callback
                val decor=activity.window.decorView as ViewGroup
                decor.addView(view,ViewGroup.LayoutParams(-1,-1))
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                @Suppress("DEPRECATION")
                run {decor.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY}
            }
            override fun onHideCustomView() {exitFullscreen()}
            override fun onCreateWindow(view:WebView?,dialog:Boolean,userGesture:Boolean,resultMsg:Message?):Boolean {
                if(!userGesture)return false
                val popup=WebView(activity)
                popup.webViewClient=object:WebViewClient(){
                    override fun shouldOverrideUrlLoading(v:WebView?,request:WebResourceRequest?):Boolean {
                        if(request?.isForMainFrame==true)browser(request.url.toString())
                        popup.post {popup.destroy()}
                        return true
                    }
                }
                val transport=resultMsg?.obj as? WebView.WebViewTransport ?:return false
                transport.webView=popup
                resultMsg.sendToTarget()
                return true
            }
        }
        root.addView(web,FrameLayout.LayoutParams(-1,-1))
    }
    fun show(targetType:String,targetId:Long) {
        if(destroyed || targetType !in listOf("movie","episode","channel") || targetId<=0L)return
        val destination="https://egyshow.com/watch?target_type=$targetType&target_id=$targetId&embed=1"
        display(destination)
    }
    private fun display(url:String) {
        root.visibility=View.VISIBLE
        web.onResume()
        if(lastUrl!=url){
            lastUrl=url
            web.loadUrl(url)
        }
    }
    fun hide() {
        exitFullscreen()
        web.onPause()
        root.visibility=View.GONE
    }
    fun stop() {
        exitFullscreen()
        web.stopLoading()
        web.loadUrl("about:blank")
        lastUrl=""
        root.visibility=View.GONE
    }
    fun isVisible()=root.visibility==View.VISIBLE
    fun isFullscreen()=fullscreenView!=null
    fun exitFullscreen(){
        val view=fullscreenView ?:return
        try{(view.parent as? ViewGroup)?.removeView(view)}catch(_:Exception){}
        fullscreenView=null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback=null
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }
    fun dispose(){
        if(destroyed)return
        destroyed=true
        exitFullscreen()
        web.stopLoading()
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
    }
}
