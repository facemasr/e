package com.personal.ytplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * YouTube's official embedded player is a last-resort playback fallback when
 * video extraction stops working. The rest of EGYTUBE remains native Android.
 * No site other than YouTube is required and no stream or player ads are intercepted.
 */
internal class YoutubeOfficialFallback(private val activity:Activity) {
    val root=FrameLayout(activity).apply {
        setBackgroundColor(Color.BLACK)
        visibility=View.GONE
    }
    private val web=WebView(activity)
    private var currentId:String?=null
    private var custom:View?=null
    private var callback:WebChromeClient.CustomViewCallback?=null
    init {
        web.setBackgroundColor(Color.BLACK)
        web.settings.apply {
            javaScriptEnabled=true
            domStorageEnabled=true
            loadsImagesAutomatically=true
            mediaPlaybackRequiresUserGesture=false
            allowFileAccess=false
            allowContentAccess=false
            mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        web.webViewClient=object:WebViewClient(){
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean {
                if(request==null || !request.isForMainFrame)return false
                val url=request.url ?:return true
                val domain=url.host.orEmpty().lowercase()
                if(url.scheme!="https")return true
                if(domain=="youtube.com" || domain.endsWith(".youtube.com") ||
                    domain=="youtube-nocookie.com" || domain.endsWith(".youtube-nocookie.com"))
                    return false
                if(request.hasGesture())try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW,url))
                }catch(_:Exception){}
                return true
            }
        }
        web.webChromeClient=object:WebChromeClient(){
            override fun onShowCustomView(view:View?,cb:CustomViewCallback?) {
                if(view==null || custom!=null)return
                custom=view
                callback=cb
                val decor=activity.window.decorView as ViewGroup
                (view.parent as? ViewGroup)?.removeView(view)
                decor.addView(view,ViewGroup.LayoutParams(-1,-1))
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
            }
            override fun onHideCustomView() { exitFull() }
        }
        root.addView(web,FrameLayout.LayoutParams(-1,-1))
    }
    fun show(id:String) {
        if(!id.matches(Regex("[A-Za-z0-9_-]{11}")))return
        root.visibility=View.VISIBLE
        if(currentId==id){web.onResume();return}
        currentId=id
        // Use YouTube's own embedded UI; ads and content limitations remain enforced by YouTube.
        val url="https://www.youtube.com/embed/$id?playsinline=1&autoplay=1&rel=0"
        val html="""<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
            <style>html,body{padding:0;margin:0;width:100%;height:100%;background:#000}iframe{width:100%;height:100%;border:0}</style>
            </head><body><iframe src="$url" title="YouTube video player"
            allow="autoplay; encrypted-media; picture-in-picture; fullscreen"
            referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe></body></html>"""
        web.loadDataWithBaseURL("https://www.youtube.com/",html,"text/html","UTF-8",null)
        web.onResume()
    }
    fun exitFull():Boolean {
        val view=custom?:return false
        (view.parent as? ViewGroup)?.removeView(view)
        custom=null
        callback?.onCustomViewHidden()
        callback=null
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        return true
    }
    fun stop() {
        exitFull()
        currentId=null
        web.onPause()
        web.stopLoading()
        web.loadUrl("about:blank")
        root.visibility=View.GONE
    }
    fun dispose() {
        stop()
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
    }
}
