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
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * In-player ad unit. Tapping the advertisement opens the device's default browser.
 * The separate dismiss control ONLY closes this impression, never clicks an ad.
 */
internal class AdvertisementBanner(private val host:Activity):LinearLayout(host) {
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
    private var loaded=false
    private var campaignHtml:String?=null
    private var campaignEnabled=true
    private var lastTouch=0L
    private val web=WebView(host)
    private fun recentTouch():Boolean = SystemClock.elapsedRealtime()-lastTouch < 7000L

    private fun openInDefaultBrowser(url:String) {
        try {
            val target=android.net.Uri.parse(url)
            if(target.scheme != "https" && target.scheme != "http")return
            host.startActivity(Intent(Intent.ACTION_VIEW,target).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_:Exception) {}
    }
    init {
        orientation=VERTICAL
        gravity=Gravity.CENTER_HORIZONTAL
        setPadding(dp(5),dp(2),dp(5),dp(4))
        background=Palette.round(0xe80a1220.toInt(),dp(10).toFloat())
        val labelRow=LinearLayout(host).apply {
            orientation=HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        labelRow.addView(TextView(host).apply {
            text="إعلان ممول"
            textSize=11f
            setTextColor(0xffd5dae4.toInt())
            gravity=Gravity.CENTER_VERTICAL
        },LayoutParams(0,dp(24),1f))
        labelRow.addView(TextView(host).apply {
            text="إغلاق ×"
            textSize=12f
            setTextColor(Color.WHITE)
            gravity=Gravity.CENTER
            setPadding(dp(10),0,dp(10),0)
            contentDescription="إغلاق الإعلان"
            setOnClickListener { visibility=View.GONE;web.onPause() }
        },LayoutParams(-2,dp(25)))
        addView(labelRow,LayoutParams(dp(320),dp(26)))
        val holder=FrameLayout(host).apply {
            setBackgroundColor(0xff131b29.toInt())
            clipToOutline=true
        }
        web.setBackgroundColor(Color.TRANSPARENT)
        web.settings.javaScriptEnabled=true
        web.settings.domStorageEnabled=true
        web.settings.allowFileAccess=false
        web.settings.allowContentAccess=false
        web.settings.javaScriptCanOpenWindowsAutomatically=false
        web.settings.setSupportMultipleWindows(true)
        if(Build.VERSION.SDK_INT>=26) web.settings.safeBrowsingEnabled=true
        web.settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        web.setOnTouchListener { _,event ->
            if(event.action==MotionEvent.ACTION_DOWN || event.action==MotionEvent.ACTION_UP)
                lastTouch=SystemClock.elapsedRealtime()
            false
        }
        web.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean {
                if(request==null || !request.isForMainFrame)return false
                if(request.hasGesture() || recentTouch())openInDefaultBrowser(request.url.toString())
                return true
            }
        }
        web.webChromeClient=object:WebChromeClient() {
            override fun onCreateWindow(view:WebView?,isDialog:Boolean,isUserGesture:Boolean,resultMsg:Message?):Boolean {
                if(!isUserGesture && !recentTouch())return false
                val popup=WebView(host)
                popup.webViewClient=object:WebViewClient() {
                    override fun shouldOverrideUrlLoading(v:WebView?,request:WebResourceRequest?):Boolean {
                        if(request!=null && request.isForMainFrame) {
                            openInDefaultBrowser(request.url.toString())
                        }
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
        holder.addView(web,FrameLayout.LayoutParams(-1,-1))
        addView(holder,LayoutParams(dp(320),dp(50)))
        visibility=View.GONE
    }
    fun configureCampaign(html:String?, allowDefault:Boolean) {
        campaignHtml=html?.trim()?.takeIf { it.isNotBlank() && it.length<16000 }
        campaignEnabled=campaignHtml!=null || allowDefault
        loaded=false
        web.stopLoading()
        web.loadUrl("about:blank")
        visibility=View.GONE
    }
    fun display() {
        if(!campaignEnabled)return
        visibility=View.VISIBLE
        web.onResume()
        if(!loaded) {
            loaded=true
            val html="""<!doctype html>
<html lang="ar"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
<style>html,body{margin:0;padding:0;width:320px;height:50px;overflow:hidden;background:transparent}</style></head><body>
<ins style="width:320px;height:50px;display:block" data-width="320" data-height="50"
 class="nc818243337" data-domain="//bonepa.com"
 data-affquery="/5b2e04174a8ba7f27eba/c818243337/?placementName=default"></ins>
<script src="https://bonepa.com/js/responsive.js" async></script></body></html>"""
            val htmlToShow=campaignHtml ?: html
            web.loadDataWithBaseURL(
                if(campaignHtml!=null)"https://egyshow.com/" else "https://bonepa.com/",
                htmlToShow,"text/html","UTF-8",null
            )
        }
    }
    fun hidePlacement() {visibility=View.GONE;web.onPause()}
    fun dispose() {web.stopLoading();web.destroy()}
}
