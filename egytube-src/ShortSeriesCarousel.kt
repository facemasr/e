package com.personal.ytplayer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Genuine YouTube short-drama video recommendations.
 * Unlike earlier versions, this component never requests EGYSHOW website content.
 */
internal class ShortSeriesCarousel(
    private val activity:Activity,
    private val onVideo:(VideoModel)->Unit,
    private val onShowAll:()->Unit
) {
    private val executor=Executors.newSingleThreadExecutor()
    private val closed=AtomicBoolean(false)
    private fun dp(value:Int)=Palette.dp(activity,value)
    private val status=TextView(activity).apply {
        text="جارٍ البحث عن مسلسلات قصيرة على يوتيوب..."
        textSize=12f
        setTextColor(Palette.SECOND)
        gravity=Gravity.RIGHT
        setPadding(dp(13),dp(4),dp(13),dp(10))
    }
    val root=LinearLayout(activity).apply {
        orientation=LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        setPadding(0,dp(3),0,dp(12))
    }
    private val cards=LinearLayout(activity).apply {
        orientation=LinearLayout.HORIZONTAL
        layoutDirection=View.LAYOUT_DIRECTION_RTL
        setPadding(dp(12),dp(4),dp(12),0)
    }
    init {
        val head=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(13),dp(6),dp(11),dp(5))
        }
        head.addView(TextView(activity).apply {
            text="مسلسلات قصيرة من يوتيوب"
            textSize=18.5f
            setTextColor(Palette.WHITE)
            typeface=Typeface.DEFAULT_BOLD
            gravity=Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0,dp(39),1f))
        head.addView(TextView(activity).apply {
            text="عرض الكل ←"
            textSize=13f
            setTextColor(0xffdc2236.toInt())
            gravity=Gravity.CENTER_VERTICAL
            setOnClickListener { onShowAll() }
        }, LinearLayout.LayoutParams(-2,dp(39)))
        root.addView(head)
        root.addView(status)
        val scroll=HorizontalScrollView(activity).apply {
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            isHorizontalScrollBarEnabled=false
            isFillViewport=false
            addView(cards)
        }
        root.addView(scroll,LinearLayout.LayoutParams(-1,dp(228)))
        root.visibility=View.VISIBLE
    }
    private fun asVideo(info:StreamInfoItem)=VideoModel(
        info.url,info.name,info.uploaderName.orEmpty(),
        info.viewCount,info.duration,info.textualUploadDate.orEmpty(),
        info.thumbnails.maxByOrNull{it.width.coerceAtLeast(0)}?.url.orEmpty(),
        info.uploaderAvatars.firstOrNull()?.url.orEmpty(),
        info.isUploaderVerified,
        info.streamType.toString().contains("LIVE",true)
    )
    fun load() {
        if(closed.get())return
        status.text="جارٍ عرض حلقات المسلسلات القصيرة من يوتيوب..."
        status.visibility=View.VISIBLE
        executor.execute {
            val seen=mutableSetOf<String>()
            val videos=mutableListOf<VideoModel>()
            val phrases=listOf(
                "مسلسل قصير الحلقة 1 دراما",
                "دراما قصيرة مدبلجة الحلقة الأولى",
                "مسلسلات قصيرة حلقات كاملة"
            )
            for (phrase in phrases) {
                if(closed.get())return@execute
                try {
                    // The same YouTube extractor that powers EGYTUBE's search page.
                    val extractor=ServiceList.YouTube.getSearchExtractor(phrase)
                    extractor.fetchPage()
                    val found=extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
                        .filter{ it.url.startsWith("https://") &&
                                 it.name.isNotBlank() &&
                                 !it.streamType.toString().contains("LIVE",true)}
                        .map(::asVideo)
                    found.forEach { item ->
                        if(seen.add(item.url))videos.add(item)
                    }
                } catch (_:Exception) {
                    // Search availability can vary by country. Try the next query.
                }
                if(videos.size>=18)break
            }
            activity.runOnUiThread {
                if(closed.get() || activity.isDestroyed)return@runOnUiThread
                cards.removeAllViews()
                if(videos.isEmpty()) {
                    status.text="تعذّر تحميل المسلسلات من يوتيوب. اضغط هنا لإعادة المحاولة."
                    status.setOnClickListener { load() }
                    return@runOnUiThread
                }
                status.text="حلقات منشورة على YouTube • "+videos.size+" فيديو"
                status.setOnClickListener(null)
                videos.take(24).forEach { item ->
                    val container=LinearLayout(activity).apply {
                        orientation=LinearLayout.VERTICAL
                        background=Palette.round(0xfff7f7f7.toInt(),dp(12).toFloat())
                        clipToOutline=true
                        setOnClickListener { onVideo(item) }
                    }
                    val imageHolder=FrameLayout(activity).apply {
                        setBackgroundColor(0xffeaeaea.toInt())
                    }
                    val img=ImageView(activity).apply {
                        scaleType=ImageView.ScaleType.CENTER_CROP
                        contentDescription=item.title
                    }
                    Thumbnails.show(img,item.thumbnail)
                    imageHolder.addView(img, FrameLayout.LayoutParams(-1,-1))
                    if(item.duration>=0) {
                        val duration=TextView(activity).apply {
                            text=Palette.formattedDuration(item.duration)
                            textSize=11f
                            setTextColor(Color.WHITE)
                            typeface=Typeface.DEFAULT_BOLD
                            background=Palette.round(0xdf000000.toInt(),dp(4).toFloat())
                            setPadding(dp(5),dp(2),dp(5),dp(2))
                        }
                        imageHolder.addView(duration,FrameLayout.LayoutParams(-2,-2,
                            Gravity.BOTTOM or Gravity.END).apply {
                            rightMargin=dp(7)
                            bottomMargin=dp(7)
                        })
                    }
                    container.addView(imageHolder,LinearLayout.LayoutParams(-1,dp(117)))
                    container.addView(TextView(activity).apply {
                        text=item.title
                        textSize=13f
                        typeface=Typeface.DEFAULT_BOLD
                        setTextColor(Palette.WHITE)
                        maxLines=2
                        ellipsize=android.text.TextUtils.TruncateAt.END
                        gravity=Gravity.RIGHT
                        setPadding(dp(8),dp(7),dp(8),dp(2))
                    },LinearLayout.LayoutParams(-1,dp(55)))
                    container.addView(TextView(activity).apply {
                        text=item.channel + (if(item.views>=0)" • "+Palette.count(item.views)+" مشاهدة" else "")
                        textSize=11f
                        setTextColor(Palette.SECOND)
                        maxLines=2
                        ellipsize=android.text.TextUtils.TruncateAt.END
                        gravity=Gravity.RIGHT
                        setPadding(dp(8),0,dp(8),0)
                    },LinearLayout.LayoutParams(-1,dp(42)))
                    cards.addView(container,LinearLayout.LayoutParams(dp(210),dp(214)).apply {
                        rightMargin=dp(9)
                    })
                }
            }
        }
    }
    fun close() {
        closed.set(true)
        executor.shutdownNow()
    }
}
