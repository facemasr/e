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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import java.util.Locale

@OptIn(UnstableApi::class)
internal class VideoPlaybackPanel(
    private val activity:Activity,
    private val goBack:()->Unit,
    private val chooseQuality:()->Unit,
    private val fullScreenChanged:(Boolean)->Unit,
    private val requestPip:()->Unit,
    private val expandMini:()->Unit
) {
    val player=ExoPlayer.Builder(activity).build()
    val root=FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
    private val preview=PlayerView(activity).apply {
        useController=false
        player=this@VideoPlaybackPanel.player
        setShutterBackgroundColor(Color.BLACK)
    }
    private val overlay=FrameLayout(activity)
    private lateinit var watermark:LinearLayout
    private var miniMode=false
    private var pipMode=false
    private var adBanner:AdvertisementBanner?=null
    private lateinit var play:TextView
    private lateinit var elapsed:TextView
    private lateinit var duration:TextView
    private val seek=SeekBar(activity)
    private lateinit var speed:TextView
    private lateinit var quality:TextView
    private lateinit var full:TextView
    private val title=TextView(activity)
    private val handler=Handler(Looper.getMainLooper())
    private var scrubbing=false
    private var expanded=false
    var playbackError:((String)->Unit)?=null
    private var released=false
    private val hideControls=Runnable { if(player.isPlaying && !miniMode && !pipMode) overlay.visibility=View.INVISIBLE }
    private val hideAd=Runnable { adBanner?.hidePlacement() }
    private val showAd=object:Runnable {
        override fun run() {
            if(released)return
            if(player.isPlaying && !miniMode && !pipMode) {
                overlay.visibility=View.INVISIBLE
                adBanner?.display()
                handler.removeCallbacks(hideAd)
                handler.postDelayed(hideAd,9000L)
            }
            handler.postDelayed(this,135000L)
        }
    }
    private var adCycleScheduled=false
    private val progressLoop=object:Runnable {
        override fun run() {
            if(released)return
            val d=player.duration
            val p=player.currentPosition
            elapsed.text=if(p>=0) Palette.formattedDuration(p/1000) else "0:00"
            duration.text=if(d>0) Palette.formattedDuration(d/1000) else "0:00"
            if(!scrubbing) seek.progress=if(d>0) ((p*1000)/d).coerceIn(0,1000).toInt() else 0
            play.text=if(player.isPlaying) "Ⅱ" else "▶"
            handler.postDelayed(this,800)
        }
    }
    private fun dp(n:Int)=Palette.dp(activity,n)
    private fun button(value:String, size:Float=20f)=TextView(activity).apply {
        text=value; textSize=size; gravity=Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface=Typeface.DEFAULT_BOLD
        setPadding(dp(6),dp(6),dp(6),dp(6))
        isClickable=true
    }
    init {
        root.addView(preview,FrameLayout.LayoutParams(-1,-1))
        val gesture=GestureDetector(activity,object:GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e:MotionEvent)=true
            override fun onSingleTapConfirmed(e:MotionEvent):Boolean {
                if(miniMode) expandMini()
                else if(!pipMode) toggle()
                return true
            }
            override fun onDoubleTap(e:MotionEvent):Boolean {
                if(miniMode) {expandMini();return true}
                if(pipMode)return true
                val forward=e.x > root.width/2f
                val d=player.duration
                val next=player.currentPosition + if(forward) 10000L else -10000L
                player.seekTo(if(d>0) next.coerceIn(0,d) else next.coerceAtLeast(0L))
                reveal()
                return true
            }
        })
        val gestures=View(activity).apply {
            setOnTouchListener { _,event -> gesture.onTouchEvent(event) }
            contentDescription="اضغط لعرض الأدوات أو مرتين للتقديم والتأخير"
        }
        root.addView(gestures,FrameLayout.LayoutParams(-1,-1))
        watermark=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(7),dp(3),dp(7),dp(3))
            alpha=0.68f
            background=Palette.round(0x9e06090f.toInt(),dp(9).toFloat())
        }
        watermark.addView(ImageView(activity).apply {
            setImageResource(R.drawable.egytube_logo)
            scaleType=ImageView.ScaleType.FIT_CENTER
        },LinearLayout.LayoutParams(dp(28),dp(28)))
        watermark.addView(button("EGYTUBE",11f))
        root.addView(watermark,FrameLayout.LayoutParams(-2,dp(36),Gravity.TOP or Gravity.END).apply {
            topMargin=dp(10);rightMargin=dp(10)
        })
        val panel=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL }
        val top=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
            setBackgroundColor(0x8a000000.toInt())
        }
        val back=button("‹",34f).apply { setOnClickListener { goBack() } }
        top.addView(back,LinearLayout.LayoutParams(dp(52),dp(48)))
        title.text="EGYTUBE";title.maxLines=1
        title.ellipsize=android.text.TextUtils.TruncateAt.END
        title.textSize=15f;title.setTextColor(Color.WHITE)
        top.addView(title,LinearLayout.LayoutParams(0,-2,1f))
        val pip=button("▣",19f).apply {
            contentDescription="مشغل نافذة صغيرة فوق التطبيقات"
            setOnClickListener { requestPip() }
        }
        top.addView(pip,LinearLayout.LayoutParams(dp(42),dp(48)))
        full=button("⛶",21f).apply { setOnClickListener { toggleFull() } }
        top.addView(full,LinearLayout.LayoutParams(dp(46),dp(48)))
        panel.addView(top,LinearLayout.LayoutParams(-1,dp(50)))
        val middle=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
        }
        val previous=button("↶ 10",20f).apply {
            setOnClickListener { player.seekTo((player.currentPosition-10000L).coerceAtLeast(0)); reveal() }
        }
        play=button("▶",29f).apply {
            setOnClickListener {
                if(player.isPlaying) player.pause() else player.play()
                reveal()
            }
        }
        val next=button("10 ↷",20f).apply {
            setOnClickListener {
                val d=player.duration;val target=player.currentPosition+10000
                player.seekTo(if(d>0) target.coerceAtMost(d) else target)
                reveal()
            }
        }
        middle.addView(previous,LinearLayout.LayoutParams(dp(86),dp(70)))
        middle.addView(play,LinearLayout.LayoutParams(dp(86),dp(70)))
        middle.addView(next,LinearLayout.LayoutParams(dp(86),dp(70)))
        panel.addView(middle,LinearLayout.LayoutParams(-1,0,1f))
        val bottom=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(5),dp(2),dp(5),dp(2))
            setBackgroundColor(0x96000000.toInt())
        }
        seek.progressTintList=android.content.res.ColorStateList.valueOf(Palette.RED)
        seek.thumbTintList=android.content.res.ColorStateList.valueOf(Palette.RED)
        seek.max=1000
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(b:SeekBar?) { scrubbing=true;handler.removeCallbacks(hideControls) }
            override fun onProgressChanged(b:SeekBar?,p:Int,byUser:Boolean) {
                if(byUser && player.duration>0) elapsed.text=Palette.formattedDuration(player.duration*p/1000/1000)
            }
            override fun onStopTrackingTouch(b:SeekBar?) {
                scrubbing=false
                if(player.duration>0)player.seekTo(player.duration*seek.progress/1000L)
                reveal()
            }
        })
        bottom.addView(seek,LinearLayout.LayoutParams(-1,dp(32)))
        val settings=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER_VERTICAL
        }
        elapsed=button("0:00",12f)
        duration=button("0:00",12f)
        settings.addView(elapsed)
        settings.addView(button("/",12f))
        settings.addView(duration)
        settings.addView(View(activity),LinearLayout.LayoutParams(0,1,1f))
        speed=button("1×",13f).apply { setOnClickListener { showSpeeds() } }
        quality=button("الجودة ⚙",13f).apply { setOnClickListener { chooseQuality();reveal() } }
        settings.addView(speed)
        settings.addView(quality)
        bottom.addView(settings,LinearLayout.LayoutParams(-1,dp(40)))
        panel.addView(bottom)
        overlay.addView(panel,FrameLayout.LayoutParams(-1,-1))
        root.addView(overlay,FrameLayout.LayoutParams(-1,-1))
        player.addListener(object:Player.Listener {
            override fun onPlaybackStateChanged(state:Int) {
                if(state==Player.STATE_READY)hideLater()
            }
            override fun onIsPlayingChanged(isPlaying:Boolean) {
                play.text=if(isPlaying)"Ⅱ" else "▶"
                if(isPlaying)hideLater() else reveal(false)
            }
            override fun onPlayerError(error:PlaybackException) {
                playbackError?.invoke("تعذّر تشغيل هذا المصدر: "+error.errorCodeName)
                reveal(false)
            }
        })
        handler.post(progressLoop)
    }
    private fun toggle() {
        adBanner?.hidePlacement()
        if(overlay.visibility==View.VISIBLE) {
            overlay.visibility=View.INVISIBLE;handler.removeCallbacks(hideControls)
        } else reveal()
    }
    private fun reveal(autohide:Boolean=true) {
        adBanner?.hidePlacement()
        if(miniMode || pipMode)return
        overlay.visibility=View.VISIBLE
        handler.removeCallbacks(hideControls)
        if(autohide)hideLater()
    }
    private fun hideLater() {
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls,4500)
    }
    fun setTitle(value:String) { title.text=value }
    fun attachAd(banner:AdvertisementBanner) {
        adBanner=banner
        val width=kotlin.math.min(dp(336),activity.resources.displayMetrics.widthPixels-dp(12))
        root.addView(banner,FrameLayout.LayoutParams(width,dp(84),Gravity.CENTER))
        banner.hidePlacement()
    }
    fun setMiniMode(active:Boolean) {
        miniMode=active
        if(active) {
            overlay.visibility=View.GONE
            watermark.visibility=View.GONE
            adBanner?.hidePlacement()
        } else if(!pipMode) {
            watermark.visibility=View.VISIBLE
            reveal()
        }
    }
    fun setPipMode(active:Boolean) {
        pipMode=active
        if(active) {
            overlay.visibility=View.GONE
            watermark.visibility=View.GONE
            adBanner?.hidePlacement()
        } else if(!miniMode) {
            watermark.visibility=View.VISIBLE
            reveal()
        }
    }

    fun setSource(media:MediaSource, resumePosition:Long) {
        if(!adCycleScheduled) {
            adCycleScheduled=true
            handler.postDelayed(showAd,16000L)
        }
        player.setMediaSource(media)
        player.prepare()
        if(resumePosition>0L)player.seekTo(resumePosition)
        player.playWhenReady=true
        reveal()
    }
    private fun showSpeeds() {
        val values=floatArrayOf(0.5f,0.75f,1f,1.25f,1.5f,1.75f,2f)
        val labels=arrayOf("0.5×","0.75×","1×","1.25×","1.5×","1.75×","2×")
        android.app.AlertDialog.Builder(activity).setTitle("سرعة التشغيل")
            .setSingleChoiceItems(labels,values.indexOfFirst { it==player.playbackParameters.speed }) { dialog,which ->
                player.setPlaybackSpeed(values[which]); speed.text=labels[which];dialog.dismiss();reveal()
            }.setNegativeButton("إلغاء",null).show()
    }
    private fun toggleFull() {
        expanded=!expanded
        activity.requestedOrientation=if(expanded)ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        fullScreenChanged(expanded)
        reveal()
    }
    fun exitFullIfNeeded():Boolean {
        if(!expanded)return false
        toggleFull()
        return true
    }
    fun isExpanded()=expanded
    fun release() {
        released=true
        adBanner?.hidePlacement()
        handler.removeCallbacksAndMessages(null)
        preview.player=null;player.release()
    }
}
