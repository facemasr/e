package com.personal.ytplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * YouTube-only official embedded player. Used solely when NewPipe/Media3 extraction
 * fails, so changes to ytInitialData do not leave a blank black player.
 *
 * Does NOT use EGYSHOW, proxy media, hide YouTube controls/ads or spoof clicks.
 */
internal class YoutubeOfficialFallback(private val activity:Activity) {
    val root=FrameLayout(activity).apply{
        setBackgroundColor(Color.BLACK)
        visibility=View.GONE
    }
    private var active=false
    private var expanded:View?=null
    private var expandedCallback:WebChromeClient.CustomViewCallback?=null
    val web=WebView(activity).apply {
        setBackgroundColor(Color.BLACK)
        settings.javaScriptEnabled=true
        settings.domStorageEnabled=true
        settings.loadsImagesAutomatically=true
        settings.mediaPlaybackRequiresUserGesture=true
        settings.allowFileAccess=false
        settings.allowContentAccess=false
        settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        if(Build.VERSION.SDK_INT>=26)settings.safeBrowsingEnabled=true
        webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean{
                if(request==null || !request.isForMainFrame)return false
                val url=request.url
                val host=url.host.orEmpty().lowercase()
                if(url.scheme!="https")return true
                if(host=="youtube.com"||host.endsWith(".youtube.com")||
                    host=="youtube-nocookie.com"||host.endsWith(".youtube-nocookie.com"))return false
                // Only explicit user-initiated external navigation.
                if(request.hasGesture()){
                    try {
                        activity.startActivity(Intent(Intent.ACTION_VIEW,url)
                            .addCategory(Intent.CATEGORY_BROWSABLE))
                    }catch(_:Exception){}
                }
                return true
            }
        }
        webChromeClient=object:WebChromeClient(){
            override fun onShowCustomView(view:View?,callback:CustomViewCallback?){
                if(view==null || expanded!=null)return
                expanded=view;expandedCallback=callback
                (activity.window.decorView as ViewGroup).addView(view,
                    ViewGroup.LayoutParams(-1,-1))
            }
            override fun onHideCustomView(){exitFullScreen()}
        }
    }
    init{ root.addView(web,FrameLayout.LayoutParams(-1,-1)) }

    fun open(id:String):Boolean {
        if(!id.matches(Regex("[A-Za-z0-9_-]{11}")))return false
        active=true
        root.visibility=View.VISIBLE
        web.onResume()
        // A YouTube-origin base provides the reference context expected by official embeds.
        val html="""<!doctype html><html lang="ar"><head>
          <meta name="viewport" content="width=device-width,initial-scale=1.0,user-scalable=no"/>
          <style>html,body{margin:0;background:#000;width:100%;height:100%;overflow:hidden}
          iframe{display:block;border:0;width:100vw;height:100vh}</style></head><body>
          <iframe src="https://www.youtube.com/embed/$id?playsinline=1&amp;rel=0&amp;enablejsapi=1"
            allow="accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture; web-share"
            allowfullscreen title="YouTube player"></iframe>
          </body></html>"""
        web.loadDataWithBaseURL("https://www.youtube.com/",html,"text/html","UTF-8",null)
        return true
    }
    fun isActive()=active
    fun hasFullScreen()=expanded!=null
    fun exitFullScreen(){
        val current=expanded ?:return
        try{(current.parent as? ViewGroup)?.removeView(current)}catch(_:Exception){}
        expanded=null
        expandedCallback?.onCustomViewHidden()
        expandedCallback=null
    }
    fun close(){
        active=false
        exitFullScreen()
        web.stopLoading()
        web.loadUrl("about:blank")
        web.onPause()
        root.visibility=View.GONE
    }
    fun dispose(){
        close()
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
    }
}
