package com.personal.ytplayer

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Independent native catalogue powered by EGYSHOW public APIs (NOT a website WebView). */
internal class EgyshowNativeCatalog(
    private val activity:Activity,
    private val onPlay:(type:String,id:Long,title:String,poster:String,shareUrl:String)->Unit
) {
    companion object { const val HOST="https://egyshow.com" }
    private val executor=Executors.newSingleThreadExecutor()
    private var disposed=false
    private var requestVersion=0
    private var category="movie"
    private var page=1
    private var moreAvailable=true
    private var displayedCount=0
    private var currentCardRow:LinearLayout?=null
    private val cardWidth=((activity.resources.displayMetrics.widthPixels/d)-48f)/2f
    private val d=activity.resources.displayMetrics.density
    private fun dp(n:Int)=(d*n).toInt()
    private fun text(value:String,size:Float=13f,bold:Boolean=false):TextView=TextView(activity).apply{
        text=value;textSize=size;setTextColor(0xff151515.toInt())
        gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
        if(bold)typeface=Typeface.DEFAULT_BOLD
    }
    val root=LinearLayout(activity).apply {
        orientation=LinearLayout.VERTICAL
        setBackgroundColor(Color.WHITE)
        visibility=View.GONE
    }
    private val rows=LinearLayout(activity).apply {
        orientation=LinearLayout.VERTICAL
        setPadding(dp(10),dp(4),dp(10),dp(8))
    }
    private val scroll=ScrollView(activity).apply {
        isFillViewport=false
        addView(rows)
    }
    private val info=text("جاري التحميل...",12f).apply {
        setTextColor(Palette.SECOND)
        setPadding(dp(12),dp(6),dp(12),dp(8))
    }
    private val more=TextView(activity).apply {
        text="عرض المزيد"
        gravity=Gravity.CENTER
        textSize=15f
        setTextColor(0xffdf1d38.toInt())
        setPadding(dp(12),dp(10),dp(12),dp(10))
        setOnClickListener { if(moreAvailable)load(false) }
    }
    init {
        val title=text("مكتبة EGYSHOW",21f,true).apply {
            setPadding(dp(14),dp(11),dp(13),dp(7))
        }
        root.addView(title)
        val chips=HorizontalScrollView(activity).apply { isHorizontalScrollBarEnabled=false }
        val chipRow=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(dp(9),dp(4),dp(9),dp(6))
        }
        for ((key,caption) in listOf(
            "movie" to "الأفلام","series" to "المسلسلات",
            "short_series" to "المسلسلات القصيرة","matches" to "المباريات"
        )) {
            val cell=text(caption,13f,true).apply {
                gravity=Gravity.CENTER
                setPadding(dp(16),dp(9),dp(16),dp(9))
                background=Palette.round(0xffeeeeee.toInt(),dp(17).toFloat())
                setOnClickListener{show(key)}
            }
            chipRow.addView(cell,LinearLayout.LayoutParams(-2,dp(40)).apply{rightMargin=dp(7)})
        }
        chips.addView(chipRow)
        root.addView(chips,LinearLayout.LayoutParams(-1,dp(48)))
        root.addView(info)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(more,LinearLayout.LayoutParams(-1,dp(46)))
    }
    fun show(kind:String="movie") {
        if(disposed)return
        category=kind
        root.visibility=View.VISIBLE
        more.visibility=View.GONE
        page=1;moreAvailable=true;rows.removeAllViews()
        displayedCount=0;currentCardRow=null
        val name=when(kind){
            "movie"->"الأفلام";"series"->"المسلسلات"
            "short_series"->"المسلسلات القصيرة";else->"القنوات الرياضية"
        }
        info.text=if(kind=="matches")
            "القنوات الرياضية • جدول المباريات المباشر لم يتوفر له API بعد"
            else "تحميل "+name+" من EGYSHOW..."
        load(true)
    }
    fun hide(){root.visibility=View.GONE}
    private fun httpJson(path:String):JSONObject {
        val conn=URL(HOST+path).openConnection() as HttpURLConnection
        conn.connectTimeout=8500;conn.readTimeout=10000
        conn.setRequestProperty("Accept","application/json")
        conn.setRequestProperty("User-Agent","EGYSHOW-Android-Native/0.4")
        try {
            val code=conn.responseCode
            val input=if(code in 200..299)conn.inputStream else conn.errorStream
            val raw=input?.bufferedReader()?.use{it.readText()}.orEmpty()
            if(code !in 200..299)throw Exception("خطأ الخادم $code")
            return JSONObject(raw)
        } finally {conn.disconnect()}
    }
    private fun jsonItems(payload:JSONObject,key:String)=payload.optJSONArray(key)?:JSONArray()
    private fun load(reset:Boolean) {
        if(disposed)return
        val token=++requestVersion
        val group=category
        val number=if(reset)1 else page
        more.visibility=View.GONE
        if(reset && group!="matches")info.text="جارٍ جلب المحتوى..."
        executor.execute {
            try {
                val json=if(group=="matches")httpJson("/api/v1/live?limit=100")
                  else httpJson("/api/v1/media?type="+group+"&limit=24&page="+number)
                val list=jsonItems(json,if(group=="matches")"channels" else "items")
                val media=mutableListOf<JSONObject>()
                for(i in 0 until list.length()){
                    val obj=list.optJSONObject(i)?:continue
                    if(group=="matches") {
                        val label=obj.optString("category_ar").lowercase()
                        if(!label.contains("رياض") && !label.contains("sport"))continue
                    }
                    media.add(obj)
                }
                activity.runOnUiThread {
                    if(disposed||activity.isDestroyed||token!=requestVersion ||group!=category)return@runOnUiThread
                    if(reset){
                        rows.removeAllViews()
                        displayedCount=0
                        currentCardRow=null
                    }
                    media.forEach { renderItem(it,group) }
                    info.text=if(group=="matches")
                        "القنوات الرياضية المتاحة: "+media.size+" • لا توجد مواعيد مباريات مؤكدة من API"
                        else "EGYSHOW • "+json.optInt("total",media.size)+" عنوان"
                    page=number+1
                    moreAvailable=group!="matches" && number<json.optInt("totalPages",number)
                    more.visibility=if(moreAvailable)View.VISIBLE else View.GONE
                    if(media.isEmpty() && reset)rows.addView(text("لا توجد بيانات متاحة لهذا القسم",14f))
                }
            }catch(e:Exception){
                activity.runOnUiThread {
                    if(disposed||activity.isDestroyed||token!=requestVersion)return@runOnUiThread
                    info.text="تعذّر الاتصال بالكتالوج. "+(e.message?:"")
                    more.visibility=View.VISIBLE
                }
            }
        }
    }
    private fun renderItem(item:JSONObject,group:String) {
        val id=item.optLong("id",0)
        if(id<=0)return
        val title=if(group=="matches")item.optString("name_ar").ifBlank{item.optString("name_en")}
          else item.optString("title_ar").ifBlank{item.optString("title_en")}
        val poster=if(group=="matches")item.optString("logo_url") else item.optString("poster_path")
        val posterHeight=if(group=="short_series")dp(247) else dp(225)
        val card=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            background=Palette.round(Color.WHITE,dp(14).toFloat())
            clipToOutline=true
            elevation=dp(3).toFloat()
            setOnClickListener { detail(item,group) }
            contentDescription="عرض تفاصيل "+title
        }
        val picture=FrameLayout(activity).apply {
            setBackgroundColor(0xffe7e8ed.toInt())
            background=Palette.round(0xffe7e8ed.toInt(),dp(13).toFloat())
            clipToOutline=true
        }
        val image=ImageView(activity).apply {
            scaleType=ImageView.ScaleType.CENTER_CROP
            contentDescription=title
        }
        Thumbnails.show(image,poster)
        picture.addView(image,FrameLayout.LayoutParams(-1,-1))
        val play=TextView(activity).apply {
            text="▶";textSize=24f;setTextColor(Color.WHITE)
            gravity=Gravity.CENTER
            background=Palette.round(0xaa050608.toInt(),dp(50).toFloat())
        }
        picture.addView(play,FrameLayout.LayoutParams(dp(55),dp(55),Gravity.CENTER))
        val metadata=when(group) {
            "matches" -> "● مباشر"
            "short_series" -> {
                val episodes=item.optInt("episode_count",0)
                if(episodes>0)"$episodes حلقة" else "مسلسل قصير"
            }
            "series" -> {
                val episodes=item.optInt("episode_count",0)
                if(episodes>0)"$episodes حلقة" else "مسلسل"
            }
            else -> {
                val year=item.optString("release_year").takeIf{it.matches(Regex("[12][0-9]{3}"))}
                year ?: "فيلم"
            }
        }
        val badge=TextView(activity).apply {
            text=metadata
            textSize=11.5f
            typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity=Gravity.CENTER
            background=Palette.round(0xdd101019.toInt(),dp(7).toFloat())
            setPadding(dp(8),dp(3),dp(8),dp(3))
        }
        picture.addView(badge,FrameLayout.LayoutParams(-2,dp(29),
            Gravity.BOTTOM or Gravity.END).apply {
            rightMargin=dp(7);bottomMargin=dp(7)
        })
        if(group!="matches"){
            val rating=item.optDouble("vote_average",0.0)
            if(rating in 0.1..10.0) {
                val score=TextView(activity).apply{
                    text=String.format(java.util.Locale.US,"★ %.1f",rating)
                    textSize=11f
                    typeface=Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity=Gravity.CENTER
                    background=Palette.round(0xc0090b11.toInt(),dp(7).toFloat())
                    setPadding(dp(7),dp(3),dp(7),dp(3))
                }
                picture.addView(score,FrameLayout.LayoutParams(-2,dp(29),
                    Gravity.TOP or Gravity.END).apply{
                    rightMargin=dp(7);topMargin=dp(7)
                })
            }
        }
        card.addView(picture,LinearLayout.LayoutParams(-1,posterHeight))
        val heading=text(title,13.8f,true).apply {
            maxLines=2
            gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
            ellipsize=android.text.TextUtils.TruncateAt.END
            setPadding(dp(9),dp(7),dp(8),0)
            minHeight=dp(52)
        }
        card.addView(heading,LinearLayout.LayoutParams(-1,dp(58)))
        val secondary=if(group=="matches")"قناة رياضية"
        else {
            val year=item.optString("release_year")
                .takeIf{it.matches(Regex("[12][0-9]{3}"))}.orEmpty()
            val type=if(group=="movie")"فيلم" else if(group=="short_series")"دراما قصيرة" else "مسلسل"
            listOf(type,year).filter{it.isNotBlank()}.joinToString(" • ")
        }
        card.addView(text(secondary,11.5f).apply {
            setTextColor(Palette.SECOND)
            maxLines=1
            ellipsize=android.text.TextUtils.TruncateAt.END
            setPadding(dp(9),0,dp(8),dp(8))
        },LinearLayout.LayoutParams(-1,dp(28)))
        if(displayedCount%2==0){
            currentCardRow=LinearLayout(activity).apply {
                orientation=LinearLayout.HORIZONTAL
                layoutDirection=View.LAYOUT_DIRECTION_RTL
                gravity=Gravity.TOP
                setPadding(0,0,0,dp(11))
            }
            rows.addView(currentCardRow,LinearLayout.LayoutParams(-1,-2))
        }
        val width=dp(cardWidth.toInt())
        currentCardRow?.addView(card,LinearLayout.LayoutParams(width,-2).apply{
            rightMargin=dp(7);leftMargin=dp(6)
        })
        displayedCount++
    }
    private fun shareUrl(item:JSONObject,kind:String):String {
        val base=HOST+"/ar/"
        if(kind=="matches"){
            val slug=item.optString("slug").trim()
            return if(slug.isBlank())base+"channels" else base+"channel/"+android.net.Uri.encode(slug)
        }
        val slug=item.optString("slug").trim()
        val segment=when(kind){"movie"->"movie";"short_series"->"short-series";else->"series"}
        return if(slug.isBlank())base+(if(kind=="movie")"movies" else if(kind=="series")"series" else "short-series")
            else base+segment+"/"+android.net.Uri.encode(slug)
    }
    private fun detail(item:JSONObject,group:String) {
        val id=item.optLong("id",0)
        if(id<=0)return
        val title=if(group=="matches")item.optString("name_ar") else
            item.optString("title_ar").ifBlank{item.optString("title_en")}
        val poster=if(group=="matches")item.optString("logo_url") else item.optString("poster_path")
        if(group=="movie" || group=="matches") {
            val type=if(group=="matches")"channel" else "movie"
            android.app.AlertDialog.Builder(activity).setTitle(title)
                .setMessage(if(type=="movie")"تشغيل الفيلم عبر مصدر EGYSHOW المعتمد"
                    else "تشغيل القناة الرياضية عبر EGYSHOW عند توفر صلاحية المشاهدة")
                .setPositiveButton("مشاهدة"){_,_->onPlay(type,id,title,poster,shareUrl(item,group))}
                .setNegativeButton("رجوع",null).show()
            return
        }
        info.text="جارٍ جلب حلقات "+title+"..."
        executor.execute {
            try{
                val episodes=jsonItems(httpJson("/api/v1/media/"+id+"/episodes"),"episodes")
                val items=mutableListOf<JSONObject>()
                for(i in 0 until episodes.length())episodes.optJSONObject(i)?.let(items::add)
                activity.runOnUiThread{
                    if(disposed||activity.isDestroyed)return@runOnUiThread
                    info.text="اختر الحلقة للمشاهدة"
                    if(items.isEmpty()) {
                        android.app.AlertDialog.Builder(activity).setTitle(title)
                            .setMessage("لا توجد حلقات منشورة متاحة").setPositiveButton("حسنًا",null).show()
                        return@runOnUiThread
                    }
                    val labels=items.map { e->
                        val ep=e.optInt("episode_number",1)
                        val season=e.optInt("season_number",1)
                        val name=e.optString("title_ar").ifBlank{"الحلقة $ep"}
                        "م$season • ح$ep — $name"
                    }
                    android.app.AlertDialog.Builder(activity).setTitle(title+" • الحلقات")
                        .setItems(labels.toTypedArray()){_,which->
                            val ep=items[which]
                            onPlay("episode",ep.optLong("id"),title+" • "+labels[which],
                                ep.optString("still_path").ifBlank{poster},shareUrl(item,group))
                        }.setNegativeButton("إغلاق",null).show()
                }
            }catch(e:Exception){
                activity.runOnUiThread {
                    if(!disposed)info.text="تعذّر تحميل الحلقات: "+e.javaClass.simpleName
                }
            }
        }
    }
    fun dispose(){disposed=true;requestVersion++;executor.shutdownNow()}
}
