package com.personal.ytplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Separate 320x50 banner placement. Never intercepts video controls,
 * manufactures clicks or treats a close action as a click.
 */
internal class AdvertisementBanner(private val host: Activity):LinearLayout(host) {
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private var closed=false
    private var loaded=false
    private val web=WebView(host)
    init {
        orientation=VERTICAL
        gravity=Gravity.CENTER_HORIZONTAL
        setPadding(dp(8),dp(3),dp(8),dp(5))
        setBackgroundColor(0xff101725.toInt())
        val head=LinearLayout(host).apply {
            orientation=HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        val label=TextView(host).apply {
            text="إعلان"
            textSize=11f
            setTextColor(0xffa3aec2.toInt())
            gravity=Gravity.CENTER_VERTICAL
            contentDescription="مساحة إعلانية"
        }
        head.addView(label,LayoutParams(0,dp(24),1f))
        val close=TextView(host).apply {
            text="إغلاق ×"
            textSize=12f
            setTextColor(Color.WHITE)
            gravity=Gravity.CENTER
            setPadding(dp(12),0,dp(12),0)
            contentDescription="إغلاق الإعلان"
            setOnClickListener {
                closed=true
                visibility=View.GONE
                web.stopLoading()
                // No ad-click invocation; close only hides the placement.
            }
        }
        head.addView(close,LayoutParams(-2,dp(26)))
        addView(head,LayoutParams(dp(320),dp(28)))
        val frame=FrameLayout(host).apply {
            setBackgroundColor(0xff111a2b.toInt())
            clipToPadding=true
        }
        web.setBackgroundColor(Color.TRANSPARENT)
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.settings.allowFileAccess=false
        web.settings.allowContentAccess=false
        web.settings.javaScriptCanOpenWindowsAutomatically=false
        web.settings.setSupportMultipleWindows(false)
        if(Build.VERSION.SDK_INT>=26)web.settings.safeBrowsingEnabled=true
        web.settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.webChromeClient=WebChromeClient()
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean {
                if(request==null || !request.isForMainFrame)return false
                // Only a real touch on an ad may open an external landing page.
                if(!request.hasGesture())return true
                val dest=request.url
                if(dest.scheme=="https") {
                    try { host.startActivity(Intent(Intent.ACTION_VIEW,dest)) }catch (_:Exception){}
                }
                return true
            }
        }
        frame.addView(web,FrameLayout.LayoutParams(-1,-1))
        addView(frame,LayoutParams(dp(320),dp(50)))
        visibility=View.GONE
    }
    fun display() {
        if(closed)return
        visibility=View.VISIBLE
        if(!loaded) {
            loaded=true
            val html="""<!doctype html>
<html lang="ar"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>html,body{margin:0;padding:0;width:320px;height:50px;overflow:hidden;background:transparent}</style>
</head><body>
<ins style="width:320px;height:50px;display:block" data-width="320" data-height="50"
 class="nc818243337" data-domain="//bonepa.com"
 data-affquery="/5b2e04174a8ba7f27eba/c818243337/?placementName=default"></ins>
<script src="https://bonepa.com/js/responsive.js" async></script>
</body></html>"""
            web.loadDataWithBaseURL("https://bonepa.com/",html,"text/html","UTF-8",null)
        }
    }
    fun hideForFullScreen(){visibility=View.GONE}
    fun restore(){if(!closed)display()}
    fun dispose(){web.stopLoading();web.destroy()}
}
