package com.personal.ytplayer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.jsoup.Jsoup
import java.util.concurrent.Executors

/** Carousel of real EGYSHOW short-series listings. No hard-coded fake posters or items. */
internal class ShortSeriesCarousel(
    private val activity:Activity,
    private val onOpen:(String)->Unit
) {
    val root=LinearLayout(activity).apply {
        orientation=LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(0,8,0,12)
    }
    private val executor=Executors.newSingleThreadExecutor()
    private val cards=LinearLayout(activity).apply {
        orientation=LinearLayout.HORIZONTAL
        layoutDirection=View.LAYOUT_DIRECTION_RTL
        setPadding(dp(10),0,dp(10),0)
    }
    private val slider=HorizontalScrollView(activity).apply {
        isHorizontalScrollBarEnabled=false
        isFillViewport=false
        layoutDirection=View.LAYOUT_DIRECTION_RTL
        addView(cards)
    }
    private val status=TextView(activity).apply {
        text="جارٍ جلب المسلسلات القصيرة من إيجي شو..."
        setTextColor(Palette.SECOND)
        textSize=12f
        setPadding(dp(14),dp(4),dp(14),dp(8))
    }
    private fun dp(v:Int)=Palette.dp(activity,v)
    private data class Series(val url:String,val title:String,val poster:String)
    init {
        val header=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(13),dp(9),dp(12),dp(8))
        }
        header.addView(TextView(activity).apply {
            text="المسلسلات القصيرة"
            setTextColor(Palette.WHITE)
            textSize=19f
            typeface=Typeface.DEFAULT_BOLD
        },LinearLayout.LayoutParams(0,dp(40),1f))
        val all=TextView(activity).apply {
            text="عرض الكل ←"
            setTextColor(0xffcf2035.toInt())
            textSize=13f
            gravity=Gravity.CENTER
            setOnClickListener { onOpen("https://egyshow.com/ar/short-series") }
        }
        header.addView(all,LinearLayout.LayoutParams(-2,dp(40)))
        root.addView(header)
        root.addView(status)
        root.addView(slider,LinearLayout.LayoutParams(-1,dp(222)))
    }
    fun load() {
        executor.execute {
            try {
                val site="https://egyshow.com/ar/short-series"
                val doc=Jsoup.connect(site)
                    .userAgent("Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 Chrome/127.0 Mobile Safari/537.36")
                    .referrer("https://egyshow.com/")
                    .timeout(14000).get()
                val seen=mutableSetOf<String>()
                val series=doc.select("a[href]").mapNotNull { anchor ->
                    val url=anchor.absUrl("href").substringBefore("#").substringBefore("?")
                    val parsed=Uri.parse(url)
                    val pathname=parsed.path.orEmpty()
                    if(parsed.scheme!="https" || parsed.host!="egyshow.com" ||
                        !pathname.startsWith("/ar/short-series/") ||
                        pathname.removePrefix("/ar/short-series/").isBlank() ||
                        !seen.add(url))return@mapNotNull null
                    val posterEl=anchor.selectFirst("img")
                    val image=if(posterEl==null)"" else {
                        listOf("data-src","data-lazy-src","src","data-original")
                            .firstNotNullOfOrNull { key ->
                                val raw=posterEl.attr(key)
                                if(raw.isBlank() || raw.startsWith("data:"))null else {
                                    val resolved=org.jsoup.helper.StringUtil.resolve(site,raw)
                                    if(resolved.startsWith("https://"))resolved else null
                                }
                            }.orEmpty()
                    }
                    val title=anchor.attr("title").ifBlank {
                        posterEl?.attr("alt").orEmpty()
                    }.ifBlank { anchor.text() }.trim().replace(Regex("\\s+")," ")
                        .removePrefix("مسلسل قصير").trim()
                    if(title.length<2) return@mapNotNull null
                    Series(url,title,image)
                }.take(12)
                activity.runOnUiThread {
                    if(activity.isDestroyed)return@runOnUiThread
                    if(series.isEmpty()) {
                        status.text="تصفح المسلسلات القصيرة على إيجي شو"
                        return@runOnUiThread
                    }
                    cards.removeAllViews()
                    series.forEach { item ->
                        val card=LinearLayout(activity).apply {
                            orientation=LinearLayout.VERTICAL
                            setBackgroundColor(0xfff7f7f7.toInt())
                            clipToOutline=true
                            background=Palette.round(0xfff5f5f5.toInt(),dp(13).toFloat())
                            setOnClickListener { onOpen(item.url) }
                        }
                        val poster=ImageView(activity).apply {
                            scaleType=ImageView.ScaleType.CENTER_CROP
                            setBackgroundColor(0xffe5e5e5.toInt())
                            contentDescription=item.title
                        }
                        Thumbnails.show(poster,item.poster)
                        card.addView(poster,LinearLayout.LayoutParams(-1,dp(171)))
                        card.addView(TextView(activity).apply {
                            text=item.title
                            setTextColor(Palette.WHITE)
                            textSize=12.5f
                            typeface=Typeface.DEFAULT_BOLD
                            maxLines=2
                            ellipsize=android.text.TextUtils.TruncateAt.END
                            gravity=Gravity.RIGHT
                            setPadding(dp(6),dp(7),dp(6),0)
                        },LinearLayout.LayoutParams(-1,dp(50)))
                        cards.addView(card,LinearLayout.LayoutParams(dp(126),dp(221)).apply {
                            marginEnd=dp(9)
                        })
                    }
                    status.visibility=View.GONE
                }
            }catch(_:Exception){
                activity.runOnUiThread {
                    if(!activity.isDestroyed)status.text="عرض المسلسلات القصيرة على موقع إيجي شو"
                }
            }
        }
    }
    fun close(){executor.shutdownNow()}
}
