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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.VideoSize
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import java.util.Locale

@OptIn(UnstableApi::class)
internal class VideoPlaybackPanel(
    private val activity:Activity,
    private val goBack:()->Unit,
    private val chooseQuality:()->Unit,
    private val fullScreenChanged:(Boolean)->Unit,
    private val requestPip:()->Unit,
    private val expandMini:()->Unit,
    private val onVideoOrientationChanged:(Boolean)->Unit,
    private val requestShare:()->Unit
) {
    val player=ExoPlayer.Builder(activity).build()
    val root=FrameLayout(activity).apply { setBackgroundColor(Color.BLACK) }
    private val preview=PlayerView(activity).apply {
        useController=false
        resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT
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
    private val subtitle=TextView(activity)
    private var muted=false
    private lateinit var volume:TextView
    private lateinit var captions:TextView
    private var subtitlesOn=false
    private val buffering=ProgressBar(activity).apply {
        isIndeterminate=true
        indeterminateTintList=android.content.res.ColorStateList.valueOf(Color.WHITE)
        visibility=View.GONE
    }
    private lateinit var share:TextView
    private var centerIsPlaying=false
    private val handler=Handler(Looper.getMainLooper())
    private var scrubbing=false
    private var expanded=false
    private var hasVideoDimensions=false
    private var landscapeVideo=false
    private fun displayOrientation():Int {
        return if(landscapeVideo && hasVideoDimensions)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
    private fun updateVideoDimensions(size:VideoSize) {
        if(size.width<=0 || size.height<=0)return
        val effectiveWidth=size.width.toDouble()*size.pixelWidthHeightRatio
        val isLandscape=effectiveWidth > size.height*1.03
        if(hasVideoDimensions && landscapeVideo==isLandscape)return
        hasVideoDimensions=true
        landscapeVideo=isLandscape
        // Expand portrait clips vertically without rotating the handset.
        onVideoOrientationChanged(!isLandscape)
        if(expanded && !miniMode && !pipMode) {
            activity.requestedOrientation=displayOrientation()
        }
    }
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
            if(!scrubbing)seek.progress=if(d>0) ((p*1000)/d).coerceIn(0,1000).toInt() else 0
            seek.secondaryProgress=if(d>0)
                ((player.bufferedPosition*1000L)/d).coerceIn(0,1000).toInt() else 0
            play.text=if(player.isPlaying) "Ⅱ" else "▶"
            play.background=Palette.round(0x99000000.toInt(),dp(42).toFloat())
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
        watermark.visibility=View.GONE // Keep the YouTube-style picture unobstructed.
        root.addView(watermark,FrameLayout.LayoutParams(-2,dp(36),Gravity.TOP or Gravity.END).apply {
            topMargin=dp(10);rightMargin=dp(10)
        })
        // Full-frame custom controls; all controls float over the native video texture.
        val panel=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(3),0,dp(3),dp(3))
        }
        val top=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(7),dp(4),dp(7),dp(4))
            setBackgroundColor(0xba050508.toInt())
        }
        val back=button("⌄",27f).apply {
            contentDescription="تصغير المشغّل والعودة إلى الفيديوهات"
            setOnClickListener { goBack() }
        }
        top.addView(back,LinearLayout.LayoutParams(dp(43),dp(48)))
        // YouTube-like overlay: collapse on the right, playback tools on the left.
        top.addView(View(activity),LinearLayout.LayoutParams(0,1,1f))
        val pip=button("▣",21f).apply {
            contentDescription="صورة داخل صورة للمحتوى المسموح"
            setOnClickListener { requestPip() }
        }
        top.addView(pip,LinearLayout.LayoutParams(dp(44),dp(46)))
        captions=button("CC",14f).apply {
            contentDescription="الترجمة"
            setOnClickListener { toggleCaptions() }
        }
        top.addView(captions,LinearLayout.LayoutParams(dp(44),dp(46)))
        quality=button("⚙",23f).apply {
            contentDescription="إعدادات الجودة والسرعة والترجمة"
            setOnClickListener { showPlayerSettings() }
        }
        top.addView(quality,LinearLayout.LayoutParams(dp(45),dp(46)))
        panel.addView(top,LinearLayout.LayoutParams(-1,dp(57)))

        val middle=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            gravity=Gravity.CENTER
            setPadding(0,0,0,0)
        }
        val previous=button("↶10",21f).apply {
            contentDescription="الرجوع عشر ثوانٍ"
            setOnClickListener {
                player.seekTo((player.currentPosition-10000L).coerceAtLeast(0L))
                reveal()
            }
        }
        play=button("▶",29f).apply {
            contentDescription="تشغيل أو إيقاف مؤقت"
            background=Palette.round(0xa8000000.toInt(),dp(45).toFloat())
            setOnClickListener {
                if(player.isPlaying)player.pause() else player.play()
                reveal()
            }
        }
        val next=button("10↷",21f).apply {
            contentDescription="التقدم عشر ثوانٍ"
            setOnClickListener {
                val d=player.duration
                val target=player.currentPosition+10000L
                player.seekTo(if(d>0)target.coerceAtMost(d) else target)
                reveal()
            }
        }
        middle.addView(previous,LinearLayout.LayoutParams(dp(75),dp(68)))
        middle.addView(play,LinearLayout.LayoutParams(dp(74),dp(68)))
        middle.addView(next,LinearLayout.LayoutParams(dp(75),dp(68)))
        panel.addView(middle,LinearLayout.LayoutParams(-1,0,1f))

        val bottom=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(7),dp(2),dp(7),dp(4))
            setBackgroundColor(0xb8060608.toInt())
        }
        seek.progressTintList=android.content.res.ColorStateList.valueOf(Palette.RED)
        seek.progressBackgroundTintList=android.content.res.ColorStateList.valueOf(0xff929292.toInt())
        seek.thumbTintList=android.content.res.ColorStateList.valueOf(Palette.RED)
        seek.max=1000
        seek.setPadding(0,0,0,0)
        seek.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(b:SeekBar?) {
                scrubbing=true
                handler.removeCallbacks(hideControls)
            }
            override fun onProgressChanged(b:SeekBar?,progress:Int,fromUser:Boolean) {
                if(fromUser && player.duration>0)
                    elapsed.text=Palette.formattedDuration(player.duration*progress/1000L/1000L)
            }
            override fun onStopTrackingTouch(b:SeekBar?) {
                scrubbing=false
                if(player.duration>0)player.seekTo(player.duration*seek.progress/1000L)
                reveal()
            }
        })
        val toolRow=LinearLayout(activity).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
        }
        full=button("⛶",22f).apply {
            contentDescription="تكبير حسب اتجاه الفيديو"
            setOnClickListener { toggleFull() }
        }
        toolRow.addView(full,LinearLayout.LayoutParams(dp(40),dp(39)))
        share=button("↗",23f).apply {
            contentDescription="مشاركة الفيديو"
            setOnClickListener { requestShare();reveal() }
        }
        toolRow.addView(share,LinearLayout.LayoutParams(dp(40),dp(39)))
        speed=button("1×",14f).apply {
            contentDescription="تغيير سرعة التشغيل"
            setOnClickListener { showSpeeds() }
        }
        toolRow.addView(speed,LinearLayout.LayoutParams(dp(40),dp(39)))
        toolRow.addView(View(activity),LinearLayout.LayoutParams(0,1,1f))
        elapsed=button("0:00",11f)
        duration=button("0:00",11f)
        toolRow.addView(elapsed)
        toolRow.addView(button("/",11f))
        toolRow.addView(duration)
        volume=button("◖))",16f).apply {
            contentDescription="كتم الصوت أو تشغيله"
            setOnClickListener {
                muted=!muted
                player.volume=if(muted)0f else 1f
                text=if(muted)"×♫" else "◖))"
                reveal()
            }
        }
        toolRow.addView(volume,LinearLayout.LayoutParams(dp(43),dp(39)))
        bottom.addView(toolRow,LinearLayout.LayoutParams(-1,dp(43)))
        bottom.addView(seek,LinearLayout.LayoutParams(-1,dp(24)))
        panel.addView(bottom,LinearLayout.LayoutParams(-1,dp(74)))
        overlay.addView(panel,FrameLayout.LayoutParams(-1,-1))
        root.addView(overlay,FrameLayout.LayoutParams(-1,-1))
        root.addView(buffering,FrameLayout.LayoutParams(dp(46),dp(46),Gravity.CENTER))
        player.addListener(object:Player.Listener {
            override fun onVideoSizeChanged(videoSize:VideoSize) {
                updateVideoDimensions(videoSize)
            }
            override fun onPlaybackStateChanged(state:Int) {
                buffering.visibility=if(state==Player.STATE_BUFFERING &&
                    !miniMode && !pipMode)View.VISIBLE else View.GONE
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
    fun setSubtitle(value:String) {
        subtitle.text=if(value.isBlank())"EGYTUBE" else value
    }
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
            watermark.visibility=View.GONE
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
        subtitlesOn=false
        captions.text="CC"
        hasVideoDimensions=false
        landscapeVideo=false
        if(expanded)activity.requestedOrientation=displayOrientation()
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
    private fun showPlayerSettings() {
        val values=arrayOf("الجودة","سرعة التشغيل","الترجمة","صورة داخل صورة")
        android.app.AlertDialog.Builder(activity)
            .setTitle("إعدادات المشغّل")
            .setItems(values){dialog,which->
                when(which){
                    0->chooseQuality()
                    1->showSpeeds()
                    2->toggleCaptions()
                    3->requestPip()
                }
                dialog.dismiss()
                reveal()
            }.show()
    }
    private fun toggleCaptions() {
        val hasCaptions=player.currentTracks.groups.any {
            it.type==C.TRACK_TYPE_TEXT && (0 until it.length).any { index->it.isTrackSupported(index) }
        }
        if(!hasCaptions) {
            android.widget.Toast.makeText(activity,"لا تتوفر ترجمة لهذا الفيديو",
                android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        subtitlesOn=!subtitlesOn
        player.trackSelectionParameters=player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!subtitlesOn).build()
        captions.text=if(subtitlesOn)"CC✓" else "CC"
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
        // Unknown or vertical media stays in portrait. Horizontal media rotates only when needed.
        activity.requestedOrientation=if(expanded)displayOrientation()
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
        activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        released=true
        adBanner?.hidePlacement()
        handler.removeCallbacksAndMessages(null)
        preview.player=null;player.release()
    }
}
