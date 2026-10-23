package com.personal.ytplayer

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast

/**
 * Local download history, progress and open/cancel controls for files queued by EGYTUBE.
 * Download authorization and file type checks are performed by MainActivity.
 */
internal object DownloadCenter {
    private const val KEY="egytube_downloads"
    fun remember(context:Context,id:Long,title:String) {
        val pref=context.getSharedPreferences(KEY,0)
        val ids=pref.getStringSet("ids",emptySet()).orEmpty().toMutableSet()
        ids.add(id.toString())
        pref.edit().putStringSet("ids",ids).putString("title_$id",title).apply()
    }
    private data class Item(val id:Long,val title:String,val status:Int,
                            val percent:Int)
    fun show(activity:Activity) {
        val prefs=activity.getSharedPreferences(KEY,0)
        val dm=activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val saved=prefs.getStringSet("ids",emptySet()).orEmpty()
            .mapNotNull { it.toLongOrNull() }.sortedDescending()
        val items=mutableListOf<Item>()
        saved.forEach { id ->
            try {
                dm.query(DownloadManager.Query().setFilterById(id)).use { c ->
                    if(c!=null && c.moveToFirst()) {
                        val status=c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val current=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total=c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val percentage=if(total>0) ((current*100)/total).coerceIn(0,100).toInt() else -1
                        items.add(Item(id,prefs.getString("title_$id","فيديو EGYTUBE").orEmpty(),status,percentage))
                    }
                }
            }catch(_:Exception){}
        }
        if(items.isEmpty()) {
            android.app.AlertDialog.Builder(activity).setTitle("التنزيلات")
                .setMessage("لم تحفظ فيديوهات مباشرة على هذا الجهاز بعد.")
                .setPositiveButton("حسنًا",null).show()
            return
        }
        val labels=items.map { item ->
            val stage=when(item.status) {
                DownloadManager.STATUS_SUCCESSFUL -> "مكتمل"
                DownloadManager.STATUS_RUNNING -> "جارٍ التحميل"
                DownloadManager.STATUS_PENDING -> "في الانتظار"
                DownloadManager.STATUS_PAUSED -> "متوقف مؤقتًا"
                DownloadManager.STATUS_FAILED -> "فشل"
                else -> "غير معروف"
            }
            val progress=if(item.percent>=0 && item.status!=DownloadManager.STATUS_SUCCESSFUL)
                " • "+item.percent+"%" else ""
            item.title+" • "+stage+progress
        }
        android.app.AlertDialog.Builder(activity).setTitle("إدارة التنزيلات")
            .setItems(labels.toTypedArray()) { _,which ->
                val item=items[which]
                if(item.status==DownloadManager.STATUS_SUCCESSFUL) {
                    try {
                        val file=dm.getUriForDownloadedFile(item.id)
                        if(file!=null) {
                            val intent=Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(file,"video/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            activity.startActivity(Intent.createChooser(intent,"فتح الفيديو"))
                        } else Toast.makeText(activity,"الملف غير متاح",Toast.LENGTH_SHORT).show()
                    }catch(_:Exception){Toast.makeText(activity,"تعذّر فتح الملف",Toast.LENGTH_SHORT).show()}
                } else {
                    android.app.AlertDialog.Builder(activity)
                        .setTitle(item.title).setMessage("هل تريد إلغاء هذه العملية؟")
                        .setNegativeButton("رجوع",null)
                        .setPositiveButton("إلغاء التنزيل"){_,_ ->
                            try {dm.remove(item.id)}catch(_:Exception){}
                            val remaining=prefs.getStringSet("ids",emptySet()).orEmpty().toMutableSet()
                            remaining.remove(item.id.toString())
                            prefs.edit().putStringSet("ids",remaining).remove("title_"+item.id).apply()
                            Toast.makeText(activity,"تم إلغاء العملية",Toast.LENGTH_SHORT).show()
                        }.show()
                }
            }.setNegativeButton("إغلاق",null).show()
    }
}
