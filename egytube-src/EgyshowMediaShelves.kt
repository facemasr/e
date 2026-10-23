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
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Two independent native EGYSHOW rails:
 * - home selector for movies / series
 * - recommendations for the same media type after playback
 *
 * These are real catalog API results; no embedded website pages or sample records.
 */
internal class EgyshowMediaShelves(
    private val activity:Activity,
    private val onSelect:(JSONObject)->Unit
) {
    val root=LinearLayout(activity).apply {
        orientation=LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        visibility=View.GONE
    }
    private val executor=Executors.newSingleThreadExecutor()
    private var closed=false
    private var version=0
    private fun dp(v:Int)=Palette.dp(activity,v)
    private val row=LinearLayout(activity).apply {
        orientation=LinearLayout.HORIZONTAL
        layoutDirection=View.LAYOUT_DIRECTION_RTL
        setPadding(dp(12),dp(4),dp(12),dp(6))
    }
    private val title=TextView(activity).apply {
        text="من مكتبة EGYSHOW"
        textSize=18.5f
        typeface=Typeface.DEFAULT_BOLD
        setTextColor(Palette.WHITE)
        gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
    }
    private val tabs=LinearLayout(activity).apply {
        layoutDirection=View.LAYOUT_DIRECTION_RTL
        orientation=LinearLayout.HORIZONTAL
    }
    private val movies=tab("الأفلام") { load("movie",0L,true) }
    private val series=tab("المسلسلات") { load("series",0L,true) }
    private var home=false
    init {
        val header=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(14),dp(5),dp(14),0)
        }
        header.addView(title,LinearLayout.LayoutParams(0,dp(45),1f))
        tabs.addView(movies)
        tabs.addView(series)
        header.addView(tabs)
        root.addView(header)
        root.addView(HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled=false
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            addView(row)
        },LinearLayout.LayoutParams(-1,dp(215)))
    }
    private fun tab(label:String,doClick:()->Unit)=TextView(activity).apply {
        text=label
        textSize=12.5f
        gravity=Gravity.CENTER
        setTextColor(Palette.WHITE)
        setPadding(dp(10),0,dp(10),0)
        setOnClickListener {doClick()}
    }
    private fun http(type:String):List<JSONObject> {
        val conn=URL("https://egyshow.com/api/v1/media?type="+type+"&limit=20&page=1").openConnection() as HttpURLConnection
        conn.connectTimeout=8500
        conn.readTimeout=11000
        conn.setRequestProperty("Accept","application/json")
        try {
            if(conn.responseCode!=200)return emptyList()
            val data=JSONObject(conn.inputStream.bufferedReader().use{it.readText()})
            val items=data.optJSONArray("items")?:JSONArray()
            return (0 until items.length()).mapNotNull{items.optJSONObject(it)}
                .filter{it.optLong("id")>0 && it.optString("poster_path").startsWith("https://")}
        } finally {conn.disconnect()}
    }
    fun showHome() {
        home=true
        root.visibility=View.VISIBLE
        tabs.visibility=View.VISIBLE
        load("movie",0L,true)
    }
    fun showRelated(type:String,excludeId:Long=0L) {
        home=false
        if(type !in listOf("movie","series","short_series")) {
            hide();return
        }
        root.visibility=View.VISIBLE
        tabs.visibility=View.GONE
        load(type,excludeId,false)
    }
    private fun load(type:String,excludeId:Long,homeMode:Boolean) {
        if(closed)return
        val stamp=++version
        row.removeAllViews()
        title.text=if(homeMode)"أفلام ومسلسلات EGYSHOW" else when(type) {
            "movie"->"أفلام مشابهة من EGYSHOW"
            "short_series"->"مسلسلات قصيرة مشابهة"
            else->"مسلسلات مشابهة من EGYSHOW"
        }
        executor.execute {
            val items=try{http(type).filter{it.optLong("id")!=excludeId}.take(16)}
                catch(_:Exception){emptyList()}
            activity.runOnUiThread {
                if(closed || activity.isDestroyed || stamp!=version)return@runOnUiThread
                if(items.isEmpty()) {
                    root.visibility=View.GONE
                    return@runOnUiThread
                }
                root.visibility=View.VISIBLE
                items.forEach{item->makeCard(item,type)}
            }
        }
    }
    private fun makeCard(item:JSONObject,type:String) {
        val label=item.optString("title_ar").ifBlank{item.optString("title_en")}
        val poster=item.optString("poster_path")
        val card=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            background=Palette.round(0xfff7f7f8.toInt(),dp(12).toFloat())
            clipToOutline=true
            setOnClickListener{onSelect(item)}
            contentDescription=label
        }
        val frame=FrameLayout(activity)
        val image=ImageView(activity).apply {
            scaleType=ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0xffe3e3e6.toInt())
        }
        Thumbnails.show(image,poster)
        frame.addView(image,FrameLayout.LayoutParams(-1,-1))
        val play=TextView(activity).apply {
            text="▶";textSize=17f;setTextColor(Color.WHITE)
            background=Palette.round(0xaa050505.toInt(),dp(35).toFloat())
            gravity=Gravity.CENTER
        }
        frame.addView(play,FrameLayout.LayoutParams(dp(40),dp(40),Gravity.CENTER))
        val score=item.optDouble("vote_average",0.0)
        if(score in 0.1..10.0){
            val badge=TextView(activity).apply{
                text=String.format(java.util.Locale.US,"★ %.1f",score)
                textSize=11f
                setTextColor(Color.WHITE)
                background=Palette.round(0xba050505.toInt(),dp(6).toFloat())
                setPadding(dp(5),dp(2),dp(5),dp(2))
            }
            frame.addView(badge,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.END).apply{
                topMargin=dp(5);rightMargin=dp(6)
            })
        }
        card.addView(frame,LinearLayout.LayoutParams(-1,dp(148)))
        card.addView(TextView(activity).apply {
            text=label
            textSize=12.5f
            typeface=Typeface.DEFAULT_BOLD
            setTextColor(Palette.WHITE)
            maxLines=2
            ellipsize=android.text.TextUtils.TruncateAt.END
            gravity=Gravity.RIGHT
            setPadding(dp(5),dp(5),dp(5),0)
        },LinearLayout.LayoutParams(-1,dp(54)))
        row.addView(card,LinearLayout.LayoutParams(dp(114),dp(204)).apply {
            rightMargin=dp(8)
        })
    }
    fun hide(){version++;root.visibility=View.GONE}
    fun dispose(){closed=true;version++;executor.shutdownNow()}
}
