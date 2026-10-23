package com.personal.ytplayer

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * EGYTUBE custom player UI for licensed / permitted sources.
 * Never stretches video. Portrait fullscreen remains portrait;
 * horizontal video can enter sensor landscape.
 */
@OptIn(UnstableApi::class)
internal class VideoPlaybackPanel(
    private val activity:Activity,
    private val goBack:()->Unit,
    private val chooseQuality:()->Unit,
    private val fullScreenChanged:(Boolean)->Unit,
    private val requestPip:()->Unit,
    private val expandMini:()->Unit,
    private val onVideoOrientationChanged:(Boolean)->Unit,
    private val shareCurrentVideo:()->Unit,
    private val saveCurrentVideo:()->Unit
) {
    val player=ExoPlayer.Builder(activity).build()
    val root=FrameLayout(activity).apply {setBackgroundColor(Color.BLACK)}
    private val preview=PlayerView(activity).apply {
        useController=false
        resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT
        player=this@VideoPlaybackPanel.player
        setShutterBackgroundColor(Color.BLACK)
    }
    private val overlay=FrameLayout(activity)
    private lateinit var watermark:LinearLayout
    private lateinit var play:TextView
    private lateinit var elapsed:TextView
    private lateinit var duration:TextView
    private lateinit var volume:TextView
    private lateinit var fullscreen:TextView
    private val title=TextView(activity)
    private val seek=SeekBar(activity)
    private val handler=Handler(Looper.getMainLooper())
    private var miniMode=false
    private var pipMode=false
    private var expanded=false
    private var landscapeVideo=false
    private var videoDimensionsKnown=false
    private var wasMuted=false
    private var scrubbing=false
    private var released=false
    private var adCycleStarted=false
    private var adBanner:AdvertisementBanner?=null
    var playbackError:((String)->Unit)?=null
    private fun dp(v:Int)=Palette.dp(activity,v)
    private fun label(value:String,size:Float=18f):TextView=TextView(activity).apply {
        text=value; textSize=size;gravity=Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface=Typeface.DEFAULT_BOLD
        isClickable=true
        setPadding(dp(4),dp(4),dp(4),dp(4))
    }
    private val hideControls=Runnable {
        if(player.isPlaying && !miniMode && !pipMode)
            overlay.visibility=View.INVISIBLE
    }
    private val hideAd=Runnable {adBanner?.hidePlacement()}
    private val showAd=object:Runnable {
        override fun run() {
            if(released)return
            if(player.isPlaying && !miniMode && !pipMode) {
                overlay.visibility=View.INVISIBLE
                adBanner?.display()
                handler.removeCallbacks(hideAd)
                handler.postDelayed(hideAd,8500L)
            }
            handler.postDelayed(this,135000L)
        }
    }
    private val progressUpdate=object:Runnable {
        override fun run() {
            if(released)return
            val pos=player.currentPosition.coerceAtLeast(0)
            val dur=player.duration
            elapsed.text=Palette.formattedDuration(pos/1000L)
            duration.text=if(dur>0) Palette.formattedDuration(dur/1000L) else "LIVE"
            if(!scrubbing)
                seek.progress=if(dur>0) ((pos*1000L)/dur).coerceIn(0,1000).toInt() else 0
            play.text=if(player.isPlaying)"Ⅱ" else "▶"
            handler.postDelayed(this,750L)
        }
    }
    init {
        root.addView(preview,FrameLayout.LayoutParams(-1,-1))
        val detector=GestureDetector(activity,object:GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event:MotionEvent)=true
            override fun onSingleTapConfirmed(event:MotionEvent):Boolean {
                if(miniMode) expandMini()
                else if(!pipMode)toggleControls()
                return true
            }
            override fun onDoubleTap(event:MotionEvent):Boolean {
                if(miniMode){expandMini();return true}
                if(pipMode)return true
                val dur=player.duration
                val next=player.currentPosition + if(event.x>root.width/2f)10000L else -10000L
                player.seekTo(if(dur>0)next.coerceIn(0L,dur) else next.coerceAtLeast(0L))
                showControls()
                return true
            }
        })
        root.addView(View(activity).apply {
            contentDescription="إظهار التحكم؛ النقر المزدوج للتقديم أو الرجوع عشر ثوان"
            setOnTouchListener { _,event ->detector.onTouchEvent(event) }
        },FrameLayout.LayoutParams(-1,-1))

        watermark=LinearLayout(activity).apply {
            gravity=Gravity.CENTER_VERTICAL
            orientation=LinearLayout.HORIZONTAL
            alpha=0.72f
            setPadding(dp(5),0,dp(7),0)
            background=Palette.round(0xaa000000.toInt(),dp(12).toFloat())
        }
        watermark.addView(ImageView(activity).apply {
            setImageResource(R.drawable.egytube_logo)
            scaleType=ImageView.ScaleType.FIT_CENTER
        },LinearLayout.LayoutParams(dp(25),dp(25)))
        watermark.addView(label("EGYTUBE",10f))
        root.addView(watermark,FrameLayout.LayoutParams(-2,dp(32),Gravity.TOP or Gravity.START).apply {
            topMargin=dp(9);leftMargin=dp(10)
        })

        val top=LinearLayout(activity).apply {
            gravity=Gravity.CENTER_VERTICAL
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            setPadding(dp(6),dp(3),dp(6),dp(3))
            setBackgroundColor(0xa9000000.toInt())
        }
        val close=label("‹",32f).apply{
            contentDescription="العودة إلى التصفح"
            setOnClickListener {goBack()}
        }
        top.addView(close,LinearLayout.LayoutParams(dp(44),dp(48)))
        val titleStack=LinearLayout(activity).apply{orientation=LinearLayout.VERTICAL}
        title.text="EGYTUBE"
        title.maxLines=1
        title.ellipsize=android.text.TextUtils.TruncateAt.END
        title.textSize=15f
        title.setTextColor(Color.WHITE)
        title.typeface=Typeface.DEFAULT_BOLD
        title.gravity=Gravity.RIGHT
        val sub=TextView(activity).apply{
            text="مشغل EGYTUBE"
            textSize=11f
            setTextColor(0xffd1d1d1.toInt())
            gravity=Gravity.RIGHT
        }
        titleStack.addView(title)
        titleStack.addView(sub)
        top.addView(titleStack,LinearLayout.LayoutParams(0,-2,1f))
        val menu=label("☷",23f).apply{
            contentDescription="خيارات المشغل"
            setOnClickListener{showPlayerMenu()}
        }
        top.addView(menu,LinearLayout.LayoutParams(dp(44),dp(48)))
        val pip=label("▣",21f).apply{
            contentDescription="صورة داخل صورة عند السماح"
            setOnClickListener{requestPip()}
        }
        top.addView(pip,LinearLayout.LayoutParams(dp(42),dp(48)))
        overlay.addView(top,FrameLayout.LayoutParams(-1,dp(56),Gravity.TOP))

        val center=LinearLayout(activity).apply{
            gravity=Gravity.CENTER
            orientation=LinearLayout.HORIZONTAL
        }
        val rewind=label("↶ 10",23f).apply{
            contentDescription="العودة عشر ثوان"
            setOnClickListener {
                player.seekTo((player.currentPosition-10000L).coerceAtLeast(0))
                showControls()
            }
        }
        play=label("▶",36f).apply {
            contentDescription="تشغيل أو إيقاف الفيديو"
            background=Palette.round(0xa9000000.toInt(),dp(18).toFloat())
            setOnClickListener{
                if(player.isPlaying)player.pause() else player.play()
                showControls()
            }
        }
        val forward=label("10 ↷",23f).apply{
            contentDescription="التقديم عشر ثوان"
            setOnClickListener {
                val dur=player.duration
                val next=player.currentPosition+10000L
                player.seekTo(if(dur>0)next.coerceAtMost(dur) else next)
                showControls()
            }
        }
        center.addView(rewind,LinearLayout.LayoutParams(dp(92),dp(62)))
        center.addView(play,LinearLayout.LayoutParams(dp(77),dp(62)))
        center.addView(forward,LinearLayout.LayoutParams(dp(92),dp(62)))
        overlay.addView(center,FrameLayout.LayoutParams(-1,dp(64),Gravity.CENTER))

        val bottom=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(8),0,dp(8),dp(3))
            setBackgroundColor(0xb8000000.toInt())
        }
        seek.max=1000
        seek.progressTintList=android.content.res.ColorStateList.valueOf(0xffff2239.toInt())
        seek.thumbTintList=android.content.res.ColorStateList.valueOf(0xffff2239.toInt())
        seek.progressBackgroundTintList=android.content.res.ColorStateList.valueOf(0xff777777.toInt())
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(s:SeekBar?){scrubbing=true;handler.removeCallbacks(hideControls)}
            override fun onProgressChanged(s:SeekBar?,progress:Int,fromUser:Boolean){
                if(fromUser && player.duration>0)
                    elapsed.text=Palette.formattedDuration(player.duration*progress/1_000_000L)
            }
            override fun onStopTrackingTouch(s:SeekBar?){
                scrubbing=false
                if(player.duration>0)player.seekTo(player.duration*seek.progress/1000L)
                showControls()
            }
        })
        bottom.addView(seek,LinearLayout.LayoutParams(-1,dp(36)))

        val bar=LinearLayout(activity).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        fullscreen=label("⛶",23f).apply{
            contentDescription="تكبير الفيديو حسب اتجاهه"
            setOnClickListener{toggleFull()}
        }
        bar.addView(fullscreen,LinearLayout.LayoutParams(dp(42),dp(42)))
        bar.addView(label("⚙",22f).apply{
            contentDescription="جودة وسرعة الفيديو"
            setOnClickListener{showPlayerMenu()}
        },LinearLayout.LayoutParams(dp(42),dp(42)))
        bar.addView(label("↓",24f).apply{
            contentDescription="تنزيل فيديو مصرح بحفظه"
            setOnClickListener{saveCurrentVideo()}
        },LinearLayout.LayoutParams(dp(38),dp(42)))
        bar.addView(label("↗",24f).apply{
            contentDescription="مشاركة الفيديو"
            setOnClickListener{shareCurrentVideo()}
        },LinearLayout.LayoutParams(dp(39),dp(42)))
        bar.addView(View(activity),LinearLayout.LayoutParams(0,1,1f))
        elapsed=label("0:00",11f)
        duration=label("0:00",11f)
        val times=LinearLayout(activity).apply{
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        times.addView(elapsed)
        times.addView(label("/",10f).apply{isClickable=false})
        times.addView(duration)
        bar.addView(times,LinearLayout.LayoutParams(-2,dp(42)))
        volume=label("◖))",17f).apply{
            contentDescription="كتم أو تشغيل الصوت"
            setOnClickListener {
                wasMuted=!wasMuted
                player.volume=if(wasMuted)0f else 1f
                text=if(wasMuted)"×♪" else "◖))"
                showControls()
            }
        }
        bar.addView(volume,LinearLayout.LayoutParams(dp(43),dp(42)))
        bottom.addView(bar,LinearLayout.LayoutParams(-1,dp(46)))
        overlay.addView(bottom,FrameLayout.LayoutParams(-1,dp(91),Gravity.BOTTOM))
        root.addView(overlay,FrameLayout.LayoutParams(-1,-1))
        player.addListener(object:Player.Listener{
            override fun onVideoSizeChanged(videoSize:VideoSize){updateVideoDimensions(videoSize)}
            override fun onIsPlayingChanged(isPlaying:Boolean){
                play.text=if(isPlaying)"Ⅱ" else "▶"
                if(isPlaying)hideSoon() else showControls(false)
            }
            override fun onPlaybackStateChanged(state:Int){
                if(state==Player.STATE_READY)hideSoon()
            }
            override fun onPlayerError(error:PlaybackException){
                playbackError?.invoke("تعذّر تشغيل الفيديو: "+error.errorCodeName)
                showControls(false)
            }
        })
        handler.post(progressUpdate)
    }
    private fun showPlayerMenu(){
        val choices=arrayOf("اختيار الجودة","سرعة التشغيل","مشاركة الفيديو","حفظ الفيديو على الجهاز")
        android.app.AlertDialog.Builder(activity).setTitle("إعدادات المشغل")
            .setItems(choices){_,which->
                when(which){
                    0->chooseQuality()
                    1->showSpeeds()
                    2->shareCurrentVideo()
                    3->saveCurrentVideo()
                }
                showControls()
            }.show()
    }
    private fun showSpeeds(){
        val values=floatArrayOf(0.5f,0.75f,1f,1.25f,1.5f,1.75f,2f)
        val text=arrayOf("0.5×","0.75×","1×","1.25×","1.5×","1.75×","2×")
        android.app.AlertDialog.Builder(activity).setTitle("سرعة التشغيل")
            .setSingleChoiceItems(text,values.indexOfFirst{it==player.playbackParameters.speed}){dialog,i->
                player.setPlaybackSpeed(values[i])
                dialog.dismiss()
                showControls()
            }.setNegativeButton("إلغاء",null).show()
    }
    private fun toggleControls(){
        adBanner?.hidePlacement()
        if(overlay.visibility==View.VISIBLE){
            overlay.visibility=View.INVISIBLE
            handler.removeCallbacks(hideControls)
        }else showControls()
    }
    private fun showControls(autoHide:Boolean=true){
        adBanner?.hidePlacement()
        if(miniMode || pipMode)return
        overlay.visibility=View.VISIBLE
        handler.removeCallbacks(hideControls)
        if(autoHide)hideSoon()
    }
    private fun hideSoon(){
        handler.removeCallbacks(hideControls)
        if(player.isPlaying)handler.postDelayed(hideControls,4700L)
    }
    private fun displayOrientation():Int{
        return if(videoDimensionsKnown && landscapeVideo)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
    private fun updateVideoDimensions(size:VideoSize){
        if(size.width<=0 || size.height<=0)return
        val isLandscape=size.width.toDouble()*size.pixelWidthHeightRatio > size.height*1.03
        if(videoDimensionsKnown && landscapeVideo==isLandscape)return
        videoDimensionsKnown=true
        landscapeVideo=isLandscape
        onVideoOrientationChanged(!isLandscape)
        if(expanded && !miniMode && !pipMode)
            activity.requestedOrientation=displayOrientation()
    }
    private fun toggleFull(){
        expanded=!expanded
        activity.requestedOrientation=if(expanded)displayOrientation()
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        fullScreenChanged(expanded)
        showControls()
    }
    fun exitFullIfNeeded():Boolean{
        if(!expanded)return false
        toggleFull()
        return true
    }
    fun isExpanded()=expanded
    fun setTitle(value:String){title.text=value}
    fun attachAd(banner:AdvertisementBanner){
        adBanner=banner
        val width=kotlin.math.min(dp(336),activity.resources.displayMetrics.widthPixels-dp(12))
        root.addView(banner,FrameLayout.LayoutParams(width,dp(84),Gravity.CENTER))
        banner.hidePlacement()
    }
    fun setMiniMode(active:Boolean){
        miniMode=active
        if(active){
            overlay.visibility=View.GONE
            watermark.visibility=View.GONE
            adBanner?.hidePlacement()
        }else if(!pipMode){
            watermark.visibility=View.VISIBLE
            showControls()
        }
    }
    fun setPipMode(active:Boolean){
        pipMode=active
        if(active){
            overlay.visibility=View.GONE
            watermark.visibility=View.GONE
            adBanner?.hidePlacement()
        }else if(!miniMode){
            watermark.visibility=View.VISIBLE
            showControls()
        }
    }
    fun setSource(media:MediaSource,resumePosition:Long){
        videoDimensionsKnown=false
        landscapeVideo=false
        if(expanded)activity.requestedOrientation=displayOrientation()
        if(!adCycleStarted){
            adCycleStarted=true
            handler.postDelayed(showAd,16000L)
        }
        player.setMediaSource(media)
        player.prepare()
        if(resumePosition>0L)player.seekTo(resumePosition)
        player.playWhenReady=true
        showControls()
    }
    fun release(){
        activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        released=true
        adBanner?.hidePlacement()
        handler.removeCallbacksAndMessages(null)
        preview.player=null
        player.release()
    }
}
