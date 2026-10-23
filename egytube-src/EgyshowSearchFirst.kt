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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

/**
 * EGYSHOW matches are inserted ahead of the YouTube results in native search.
 * No fake matched results; server owns relevance and publication eligibility.
 */
internal class EgyshowSearchFirst(
    private val activity:Activity,
    private val onSelect:(JSONObject)->Unit
){
    private val executor=Executors.newSingleThreadExecutor()
    private var discarded=false
    private var seq=0
    private fun dp(v:Int)=(v*activity.resources.displayMetrics.density).toInt()
    val root=LinearLayout(activity).apply {
        orientation=LinearLayout.VERTICAL
        visibility=View.GONE
        setBackgroundColor(Color.WHITE)
        setPadding(0,dp(4),0,dp(9))
    }
    private val cards=LinearLayout(activity).apply {
        orientation=LinearLayout.HORIZONTAL
        layoutDirection=View.LAYOUT_DIRECTION_RTL
        setPadding(dp(12),dp(3),dp(12),dp(4))
    }
    init {
        root.addView(TextView(activity).apply {
            text="نتائج EGYSHOW"
            textSize=18f;typeface=Typeface.DEFAULT_BOLD
            setTextColor(Palette.WHITE)
            gravity=Gravity.RIGHT
            setPadding(dp(16),dp(7),dp(12),dp(6))
        })
        val scroll=HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled=false
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            addView(cards)
        }
        root.addView(scroll,LinearLayout.LayoutParams(-1,dp(209)))
        root.addView(View(activity).apply{setBackgroundColor(0xffececec.toInt())},
            LinearLayout.LayoutParams(-1,dp(1)))
    }
    fun hide(){
        ++seq
        root.visibility=View.GONE
        cards.removeAllViews()
    }
    fun search(term:String) {
        val token=++seq
        if(discarded || term.isBlank()){hide();return}
        root.visibility=View.GONE
        val query=URLEncoder.encode(term,"UTF-8")
        executor.execute {
            var matches=emptyList<JSONObject>()
            try {
                val conn=URL("https://egyshow.com/api/v1/search?q=$query&limit=9").openConnection() as HttpURLConnection
                conn.connectTimeout=9000
                conn.readTimeout=12000
                conn.setRequestProperty("Accept","application/json")
                try {
                    if(conn.responseCode==200) {
                        val data=conn.inputStream.bufferedReader().use{it.readText()}
                        val array=JSONObject(data).optJSONArray("items")
                        if(array!=null){
                            val found=mutableListOf<JSONObject>()
                            for(i in 0 until array.length()){
                                val row=array.optJSONObject(i)?:continue
                                if(row.optString("media_type") in setOf("movie","series","short_series") && row.optLong("id")>0)
                                    found.add(row)
                            }
                            matches=found.take(9)
                        }
                    }
                }finally{conn.disconnect()}
            }catch(_:Exception){}
            activity.runOnUiThread {
                if(discarded || activity.isDestroyed || seq!=token)return@runOnUiThread
                cards.removeAllViews()
                if(matches.isEmpty()){root.visibility=View.GONE;return@runOnUiThread}
                root.visibility=View.VISIBLE
                matches.forEach { item ->
                    val title=item.optString("title_ar").ifBlank {item.optString("title_en")}
                    val type=when(item.optString("media_type")){
                        "movie"->"فيلم"
                        "short_series"->"مسلسل قصير"
                        else->"مسلسل"
                    }
                    val card=LinearLayout(activity).apply {
                        orientation=LinearLayout.VERTICAL
                        setOnClickListener{onSelect(item)}
                        contentDescription="$type: $title"
                    }
                    val image=ImageView(activity).apply {
                        scaleType=ImageView.ScaleType.CENTER_CROP
                        setBackgroundColor(0xffeeeeee.toInt())
                        background=Palette.round(0xffeeeeee.toInt(),dp(10).toFloat())
                        clipToOutline=true
                    }
                    Thumbnails.show(image,item.optString("poster_path"))
                    val frame=FrameLayout(activity)
                    frame.addView(image,FrameLayout.LayoutParams(-1,-1))
                    frame.addView(TextView(activity).apply{
                        text=type
                        textSize=11f
                        setTextColor(Color.WHITE)
                        background=Palette.round(0xc7151725.toInt(),dp(6).toFloat())
                        setPadding(dp(5),dp(2),dp(5),dp(2))
                    },FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.END).apply{
                        bottomMargin=dp(5);rightMargin=dp(5)
                    })
                    card.addView(frame,LinearLayout.LayoutParams(-1,dp(139)))
                    card.addView(TextView(activity).apply {
                        text=title
                        textSize=12.5f
                        typeface=Typeface.DEFAULT_BOLD
                        setTextColor(Palette.WHITE)
                        maxLines=2
                        ellipsize=android.text.TextUtils.TruncateAt.END
                        gravity=Gravity.RIGHT
                        setPadding(dp(5),dp(5),dp(4),0)
                    },LinearLayout.LayoutParams(-1,dp(59)))
                    cards.addView(card,LinearLayout.LayoutParams(dp(113),-2).apply {
                        rightMargin=dp(7);leftMargin=dp(4)
                    })
                }
            }
        }
    }
    fun dispose(){
        discarded=true
        hide()
        executor.shutdownNow()
    }
}
