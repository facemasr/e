package com.personal.ytplayer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * EGYTUBE's native, RTL-first discovery screens. These views use real cached
 * YouTube results and locally stored preferences; they never fabricate an
 * authenticated YouTube account, followers, downloads, or view counts.
 */
internal object EgyTubeScreens {
    private fun dp(a:Activity, n:Int)=Palette.dp(a,n)
    private fun label(a:Activity,value:String,size:Float=15f,bold:Boolean=false,muted:Boolean=false):TextView =
        TextView(a).apply {
            text=value
            textSize=size
            setTextColor(if(muted)Palette.SECOND else Palette.WHITE)
            if(bold)typeface=Typeface.DEFAULT_BOLD
            gravity=Gravity.RIGHT or Gravity.CENTER_VERTICAL
            textDirection=View.TEXT_DIRECTION_FIRST_STRONG
        }
    private fun column(a:Activity)=LinearLayout(a).apply {
        orientation=LinearLayout.VERTICAL
        layoutDirection=View.LAYOUT_DIRECTION_RTL
    }
    private fun page(a:Activity,title:String, build:(LinearLayout)->Unit):View {
        val body=column(a).apply {
            setPadding(dp(a,15),dp(a,20),dp(a,15),dp(a,30))
        }
        body.addView(label(a,title,28f,true),LinearLayout.LayoutParams(-1,dp(a,55)))
        build(body)
        return ScrollView(a).apply {
            fillViewport=true
            setBackgroundColor(Palette.BG)
            addView(body,ScrollView.LayoutParams(-1,-2))
        }
    }
    private fun section(a:Activity,parent:LinearLayout,title:String) {
        val t=label(a,title,20f,true)
        t.setPadding(0,dp(a,19),0,dp(a,10))
        parent.addView(t)
    }
    private fun action(a:Activity,parent:LinearLayout,title:String,subtitle:String="",onTap:()->Unit) {
        val row=LinearLayout(a).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            background=Palette.round(Palette.SURFACE,dp(a,16).toFloat())
            setPadding(dp(a,15),dp(a,11),dp(a,15),dp(a,11))
            setOnClickListener{onTap()}
            isClickable=true
            isFocusable=true
        }
        val copy=column(a)
        copy.addView(label(a,title,15.5f,true))
        if(subtitle.isNotBlank())copy.addView(label(a,subtitle,12f,false,true))
        row.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
        row.addView(label(a,"‹",25f).apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(dp(a,30),-1))
        parent.addView(row,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(a,9)})
    }
    private fun videoRow(a:Activity,parent:LinearLayout,video:VideoModel,play:(VideoModel)->Unit) {
        val item=LinearLayout(a).apply {
            orientation=LinearLayout.HORIZONTAL
            layoutDirection=View.LAYOUT_DIRECTION_RTL
            gravity=Gravity.CENTER_VERTICAL
            setPadding(0,dp(a,5),0,dp(a,10))
            setOnClickListener{play(video)}
        }
        val image=FrameLayout(a).apply {
            background=Palette.round(Palette.SURFACE,dp(a,12).toFloat())
            clipToOutline=true
        }
        image.addView(ImageView(a).apply {
            scaleType=ImageView.ScaleType.CENTER_CROP
            Thumbnails.show(this,video.thumbnail)
        },FrameLayout.LayoutParams(-1,-1))
        if(video.duration>=0 || video.isLive) {
            image.addView(label(a,if(video.isLive)"مباشر" else Palette.formattedDuration(video.duration),
                11f,true).apply {
                setTextColor(Color.WHITE)
                background=Palette.round(0xdd101010.toInt(),dp(a,5).toFloat())
                setPadding(dp(a,5),dp(a,1),dp(a,5),dp(a,1))
            },FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.RIGHT).apply {
                bottomMargin=dp(a,7);rightMargin=dp(a,7)
            })
        }
        item.addView(image,LinearLayout.LayoutParams(dp(a,148),dp(a,84)))
        val copy=column(a).apply{setPadding(dp(a,10),0,dp(a,5),0)}
        copy.addView(label(a,video.title,14f,true).apply{maxLines=2})
        copy.addView(label(a,video.channel,12f,false,true))
        val views=if(video.views>=0) " · "+Palette.count(video.views)+" مشاهدة" else ""
        copy.addView(label(a,video.published+views,11f,false,true).apply{maxLines=1})
        item.addView(copy,LinearLayout.LayoutParams(0,-2,1f))
        parent.addView(item)
    }
    fun subscriptions(a:Activity,followed:Set<String>,items:List<VideoModel>,
                      play:(VideoModel)->Unit,openChannel:(String)->Unit):View =
        page(a,"الاشتراكات") {body->
            section(a,body,"القنوات التي تتابعها محليًا")
            if(followed.isEmpty()) {
                body.addView(label(a,"لم تتابع أي قناة بعد. افتح قائمة أي فيديو ثم اختر متابعة القناة.",14f))
            } else {
                val scroll=HorizontalScrollView(a).apply{isHorizontalScrollBarEnabled=false}
                val rail=LinearLayout(a).apply {orientation=LinearLayout.HORIZONTAL;layoutDirection=View.LAYOUT_DIRECTION_RTL}
                followed.sorted().forEach{name->
                    val tile=column(a).apply {
                        gravity=Gravity.CENTER
                        setPadding(dp(a,8),0,dp(a,8),0)
                        setOnClickListener{openChannel(name)}
                    }
                    val pic=FrameLayout(a).apply {
                        background=Palette.round(0xfffde8eb.toInt(),dp(a,32).toFloat())
                        clipToOutline=true
                    }
                    val avatar=items.firstOrNull{it.channel==name}?.avatar.orEmpty()
                    if(avatar.isNotBlank())pic.addView(ImageView(a).apply{
                        scaleType=ImageView.ScaleType.CENTER_CROP
                        Thumbnails.show(this,avatar)
                    },FrameLayout.LayoutParams(-1,-1))
                    else pic.addView(label(a,name.take(1),24f,true).apply{
                        setTextColor(Palette.RED);gravity=Gravity.CENTER
                    },FrameLayout.LayoutParams(-1,-1))
                    tile.addView(pic,LinearLayout.LayoutParams(dp(a,64),dp(a,64)))
                    tile.addView(label(a,name,12f,true).apply{
                        gravity=Gravity.CENTER;maxLines=2
                    },LinearLayout.LayoutParams(dp(a,92),dp(a,40)))
                    rail.addView(tile)
                }
                scroll.addView(rail)
                body.addView(scroll)
            }
            section(a,body,"أحدث فيديوهات القنوات")
            val results=items.filter{it.channel in followed}
            if(results.isEmpty()) body.addView(label(a,
                "لا توجد نتائج من القنوات المتابعة في الفيديوهات المحمّلة حاليًا.",13f,false,true))
            results.forEach{videoRow(a,body,it,play)}
        }
    fun library(a:Activity,items:List<VideoModel>,saved:Set<String>,history:Set<String>,
                play:(VideoModel)->Unit,downloads:()->Unit):View =
        page(a,"المكتبة") {body->
            section(a,body,"متابعة المشاهدة")
            val recent=items.filter{it.url in history}
            if(recent.isEmpty()) body.addView(label(a,
                "ستظهر هنا الفيديوهات التي شاهدتها والموجودة في النتائج المحمّلة.",13f,false,true))
            recent.take(8).forEach{videoRow(a,body,it,play)}
            section(a,body,"قوائمك ومحتواك")
            action(a,body,"المشاهدة لاحقًا","عدد العناصر المحفوظة: "+saved.size) {
                android.app.AlertDialog.Builder(a).setTitle("المشاهدة لاحقًا").apply {
                    val matching=items.filter{it.url in saved}
                    setItems(matching.map{it.title}.toTypedArray()){_,n->play(matching[n])}
                    setNegativeButton("إغلاق",null)
                }.show()
            }
            action(a,body,"التنزيلات","إدارة الفيديوهات المحمّلة على الجهاز",downloads)
            section(a,body,"الفيديوهات المحفوظة")
            val matches=items.filter{it.url in saved}
            if(matches.isEmpty())body.addView(label(a,
                "لم يتم العثور على فيديوهات محفوظة ضمن النتائج الحالية.",13f,false,true))
            matches.forEach{videoRow(a,body,it,play)}
        }
    fun profile(a:Activity):View=page(a,"الملف الشخصي والإعدادات"){body->
        val header=column(a).apply {
            gravity=Gravity.CENTER
            background=Palette.round(0xffffebee.toInt(),dp(a,18).toFloat())
            setPadding(dp(a,18),dp(a,18),dp(a,18),dp(a,18))
        }
        header.addView(label(a,"EGYTUBE",23f,true).apply{setTextColor(Palette.RED)})
        header.addView(label(a,"إعدادات هذا الجهاز",14f,false,true))
        header.addView(label(a,"لا يتطلب تصفح الفيديوهات تسجيل الدخول إلى حساب.",12f,false,true))
        body.addView(header)
        section(a,body,"تفضيلات التشغيل")
        val prefs=a.getSharedPreferences("egytube_settings",0)
        val checked=prefs.getBoolean("background_allowed_files",true)
        action(a,body,"التشغيل في الخلفية",if(checked)"مفعّل للمصادر المسموح بها" else "متوقف") {
            val options=arrayOf("تشغيل للمصادر المباشرة المسموح بها","إيقاف")
            android.app.AlertDialog.Builder(a).setTitle("التشغيل في الخلفية")
                .setSingleChoiceItems(options,if(checked)0 else 1){dialog,which->
                    prefs.edit().putBoolean("background_allowed_files",which==0).apply()
                    dialog.dismiss()
                }.setNegativeButton("إلغاء",null).show()
        }
        section(a,body,"الخصوصية والمعلومات")
        action(a,body,"مكتبة محلية","المفضلة والمتابعات محفوظة على هذا الجهاز"){}
        action(a,body,"حول EGYTUBE","تطبيق فيديوهات مستقل بواجهة عربية أصلية"){}
    }
    fun channel(a:Activity,name:String,items:List<VideoModel>,
                play:(VideoModel)->Unit,follow:()->Unit):View =
        page(a,name) {body->
            section(a,body,"القناة")
            action(a,body,"متابعة هذه القناة","إضافتها إلى اشتراكات EGYTUBE المحلية",follow)
            section(a,body,"فيديوهات القناة")
            val matches=items.filter{it.channel==name}
            if(matches.isEmpty())body.addView(label(a,"لا توجد فيديوهات من هذه القناة في النتائج الحالية.",13f,false,true))
            matches.forEach{videoRow(a,body,it,play)}
        }
    fun shorts(a:Activity,items:List<VideoModel>,play:(VideoModel)->Unit):View =
        page(a,"المقاطع القصيرة") {body->
            val shorts=items.filter{
                it.duration in 1L..180L || it.title.contains("shorts",true) ||
                    it.title.contains("#short",true) || it.title.contains("قصير")
            }.take(15)
            if(shorts.isEmpty()) {
                body.addView(label(a,"جارٍ عرض المقاطع القصيرة المتاحة...",13f,false,true))
            }
            shorts.forEach{video->
                val poster=FrameLayout(a).apply {
                    background=Palette.round(Color.BLACK,dp(a,20).toFloat())
                    clipToOutline=true
                    setOnClickListener{play(video)}
                }
                poster.addView(ImageView(a).apply{
                    scaleType=ImageView.ScaleType.CENTER_CROP
                    Thumbnails.show(this,video.thumbnail)
                },FrameLayout.LayoutParams(-1,-1))
                val overlay=column(a).apply{
                    setPadding(dp(a,16),dp(a,10),dp(a,16),dp(a,15))
                    setBackgroundColor(0x88000000.toInt())
                }
                overlay.addView(label(a,video.title,18f,true).apply {
                    setTextColor(Color.WHITE);maxLines=2
                })
                overlay.addView(label(a,video.channel+"   •   ▶",14f,true).apply{
                    setTextColor(Color.WHITE)
                })
                poster.addView(overlay,FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM))
                body.addView(poster,LinearLayout.LayoutParams(-1,dp(a,475)).apply{
                    bottomMargin=dp(a,14)
                })
            }
        }
}
