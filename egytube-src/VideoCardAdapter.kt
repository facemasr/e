package com.personal.ytplayer

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal data class VideoModel(
    val url: String, val title: String, val channel: String,
    val views: Long, val duration: Long, val published: String,
    val thumbnail: String, val avatar: String, val verified: Boolean,
    val isLive: Boolean
)

internal object Palette {
    const val BG = 0xffffffff.toInt()
    const val SURFACE = 0xfff2f3f5.toInt()
    const val SECOND = 0xff6a7078.toInt()
    const val RED = 0xffe62135.toInt()
    const val WHITE = 0xff14161a.toInt()
    fun dp(a: Activity, value: Int): Int = (a.resources.displayMetrics.density * value).toInt()
    fun round(color: Int, radius: Float): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = radius }
    fun formattedDuration(seconds: Long): String {
        if (seconds < 0) return ""
        if (seconds >= 3600) return String.format(java.util.Locale.US,"%d:%02d:%02d",seconds/3600,seconds%3600/60,seconds%60)
        return String.format(java.util.Locale.US,"%d:%02d",seconds/60,seconds%60)
    }
    fun count(n: Long): String {
        if (n < 0) return ""
        return when {
            n >= 1_000_000_000 -> String.format(java.util.Locale.US,"%.1f مليار",n / 1_000_000_000.0)
            n >= 1_000_000 -> String.format(java.util.Locale.US,"%.1f مليون",n / 1_000_000.0)
            n >= 1000 -> String.format(java.util.Locale.US,"%.1f ألف",n / 1000.0)
            else -> n.toString()
        }
    }
}

internal object Thumbnails {
    private val pool=Executors.newFixedThreadPool(5)
    private val cache = object : LruCache<String,Bitmap>(16*1024) {
        override fun sizeOf(key:String, value:Bitmap) = value.byteCount/1024
    }
    private val main = Handler(Looper.getMainLooper())
    fun show(target:ImageView, src:String) {
        target.tag=src
        target.setImageDrawable(null)
        if (src.isBlank() || !src.startsWith("https://")) return
        synchronized(cache) { cache.get(src) }?.let { target.setImageBitmap(it); return }
        pool.execute {
            try {
                val con=URL(src).openConnection() as HttpURLConnection
                con.connectTimeout=7000
                con.readTimeout=9000
                con.setRequestProperty("User-Agent","Mozilla/5.0")
                val bitmap=try { con.inputStream.use { BitmapFactory.decodeStream(it) } }
                  finally { con.disconnect() }
                if (bitmap!=null) {
                    synchronized(cache) { cache.put(src,bitmap) }
                    main.post { if (target.tag==src) target.setImageBitmap(bitmap) }
                }
            } catch (_:Exception) { }
        }
    }
}

internal class VideoCardAdapter(
    private val activity: Activity,
    private val onClick:(VideoModel)->Unit
): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val slotAds=mutableListOf<AdvertisementBanner>()
    private val rectIndex=5
    private val scriptIndex=13
    private fun hasRectangle()=items.size>rectIndex
    private fun hasScript()=items.size>12
    private fun indexFor(position:Int):Int=position-
        (if(hasRectangle() && position>rectIndex)1 else 0)-
        (if(hasScript() && position>scriptIndex)1 else 0)
    override fun getItemViewType(position:Int):Int=when {
        hasRectangle() && position==rectIndex->1
        hasScript() && position==scriptIndex->2
        else->0
    }
    private class AdHolder(view:View,val ad:AdvertisementBanner):RecyclerView.ViewHolder(view)

    val items= mutableListOf<VideoModel>()
    private val d=activity.resources.displayMetrics.density
    private fun dp(n:Int)=(n*d).toInt()
    class Holder(view:View, val cover:ImageView, val avatar:ImageView,
                 val name:TextView,val details:TextView,val length:TextView,
                 val more:TextView):RecyclerView.ViewHolder(view)
    override fun getItemCount():Int=items.size+
        (if(hasRectangle())1 else 0)+(if(hasScript())1 else 0)
    override fun onCreateViewHolder(parent:ViewGroup,viewType:Int):RecyclerView.ViewHolder {
        if(viewType!=0) {
            val kind=if(viewType==1)AdvertisementBanner.Placement.HOME_RECTANGLE
                else AdvertisementBanner.Placement.FEED_SCRIPT
            val ad=AdvertisementBanner(activity,kind)
            slotAds.add(ad)
            val holder=FrameLayout(activity).apply {
                setPadding(dp(8),dp(10),dp(8),dp(12))
                addView(ad,FrameLayout.LayoutParams(-1,-2,Gravity.CENTER))
                setBackgroundColor(Color.WHITE)
            }
            return AdHolder(holder,ad)
        }
        val outer=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(0,dp(6),0,dp(16))
            setBackgroundColor(Color.WHITE)
        }
        val width=activity.resources.displayMetrics.widthPixels
        val imgHeight = width * 9 / 16
        val frame=FrameLayout(activity).apply {
            setBackgroundColor(Palette.SURFACE)
        }
        val cover=ImageView(activity).apply {
            scaleType=ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Palette.SURFACE)
        }
        frame.addView(cover,FrameLayout.LayoutParams(-1,-1))
        val duration=TextView(activity).apply {
            textSize=12f
            typeface=Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(7),dp(3),dp(7),dp(3))
            background=Palette.round(0xdc070c13.toInt(),dp(5).toFloat())
        }
        frame.addView(duration,FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.END).apply {
            bottomMargin=dp(9); rightMargin=dp(9)
        })
        outer.addView(frame,LinearLayout.LayoutParams(-1,imgHeight))
        val info=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.TOP
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(dp(13),dp(11),dp(12),dp(12))
        }
        val avatarFrame=FrameLayout(activity).apply {
            background=Palette.round(0xffe6e7ec.toInt(), dp(40).toFloat())
            clipToOutline=true
        }
        val avatar=ImageView(activity).apply { scaleType=ImageView.ScaleType.CENTER_CROP }
        avatarFrame.addView(avatar,FrameLayout.LayoutParams(-1,-1))
        info.addView(avatarFrame,LinearLayout.LayoutParams(dp(40),dp(40)))
        val texts=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(12),0,dp(8),0)
            gravity=Gravity.RIGHT
        }
        val name=TextView(activity).apply {
            textSize=16f
            typeface=Typeface.DEFAULT_BOLD
            setTextColor(Palette.WHITE)
            maxLines=2
            gravity=Gravity.RIGHT
            textDirection=View.TEXT_DIRECTION_FIRST_STRONG
            ellipsize=android.text.TextUtils.TruncateAt.END
        }
        val details=TextView(activity).apply {
            textSize=12.5f
            setTextColor(Palette.SECOND)
            maxLines=2
            gravity=Gravity.RIGHT
            textDirection=View.TEXT_DIRECTION_FIRST_STRONG
            setPadding(0,dp(6),0,0)
        }
        texts.addView(name)
        texts.addView(details)
        info.addView(texts,LinearLayout.LayoutParams(0,-2,1f))
        val more=TextView(activity).apply {
            text="⋮"; textSize=25f; setTextColor(Palette.SECOND)
            gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL
            contentDescription="المزيد من الخيارات"
        }
        info.addView(more, LinearLayout.LayoutParams(dp(26),dp(45)))
        outer.addView(info)
        outer.addView(View(activity).apply {setBackgroundColor(0xfff1f1f1.toInt())},
            LinearLayout.LayoutParams(-1,dp(1)))
        return Holder(outer,cover,avatar,name,details,duration,more)
    }
    override fun onBindViewHolder(holder:RecyclerView.ViewHolder,position:Int) {
        if(holder is AdHolder) {
            holder.ad.display()
            return
        }
        val h=holder as Holder
        val video=items[indexFor(position)]
        h.name.text=video.title
        val verified = if (video.verified) " ✓" else ""
        val views=if (video.views>=0) " • "+Palette.count(video.views)+" مشاهدة" else ""
        val whenText=if(video.published.isNotBlank()) " • "+video.published else ""
        h.details.text=video.channel + verified + views + whenText
        h.length.text=if(video.isLive) "مباشر" else Palette.formattedDuration(video.duration)
        h.length.visibility=if(video.isLive || video.duration>=0) View.VISIBLE else View.GONE
        Thumbnails.show(h.cover,video.thumbnail)
        Thumbnails.show(h.avatar,video.avatar)
        h.itemView.setOnClickListener { onClick(video) }
        h.more.setOnClickListener {
            android.app.AlertDialog.Builder(activity).setItems(arrayOf("تشغيل الفيديو","نسخ رابط الفيديو","حفظ للمشاهدة لاحقًا","متابعة القناة محليًا")) { _,which ->
                when(which) {
                    0 -> onClick(video)
                    1 -> (activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager)
                        .setPrimaryClip(android.content.ClipData.newPlainText("رابط الفيديو",video.url))
                    2 -> {
                        val prefs=activity.getSharedPreferences("egytube_library",0)
                        val saved=prefs.getStringSet("saved_urls", emptySet()).orEmpty().toMutableSet()
                        saved.add(video.url)
                        prefs.edit().putStringSet("saved_urls",saved).apply()
                        android.widget.Toast.makeText(activity,"تم الحفظ في مكتبتك",android.widget.Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        if(video.channel.isNotBlank()) {
                            val prefs=activity.getSharedPreferences("egytube_library",0)
                            val followed=prefs.getStringSet("followed_channels",emptySet()).orEmpty().toMutableSet()
                            followed.add(video.channel)
                            prefs.edit().putStringSet("followed_channels",followed).apply()
                            android.widget.Toast.makeText(activity,"تتابع "+video.channel+" داخل EGYTUBE",android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }.show()
        }
    }
    fun replaceAll(all:List<VideoModel>) {
        items.clear()
        items.addAll(all)
        notifyDataSetChanged()
    }
    fun disposeAds(){
        slotAds.forEach{it.dispose()}
        slotAds.clear()
    }
    fun append(all:List<VideoModel>) {
        val old=items.size
        items.addAll(all)
        notifyDataSetChanged()
    }
}
