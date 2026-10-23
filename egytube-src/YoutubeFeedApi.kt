package com.personal.ytplayer

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Server-supported YouTube video discovery, independent of fragile YouTube kiosk extraction. */
internal object YoutubeFeedApi {
    private const val base="https://egyshow.com/api/v1/youtube"
    private val categories=listOf("entertainment","music","sports","gaming","technology","education")
    private fun fetch(path:String):List<VideoModel> {
        val conn=URL(base+path).openConnection() as HttpURLConnection
        conn.connectTimeout=8500
        conn.readTimeout=35000
        conn.setRequestProperty("Accept","application/json")
        conn.setRequestProperty("User-Agent","EGYTUBE-Android/0.4.6")
        try {
            if(conn.responseCode!=200)throw IllegalStateException("YouTube feed HTTP "+conn.responseCode)
            val raw=conn.inputStream.bufferedReader().use{it.readText()}
            val json=JSONObject(raw)
            if(!json.optBoolean("ok",false))throw IllegalStateException("YouTube feed unavailable")
            val items=json.optJSONArray("items")?:return emptyList()
            val videos=mutableListOf<VideoModel>()
            for(i in 0 until items.length()){
                val item=items.optJSONObject(i)?:continue
                val id=item.optString("id")
                if(!id.matches(Regex("[A-Za-z0-9_-]{11}")))continue
                val title=item.optString("title")
                if(title.isBlank())continue
                // Discovery API filters live streams, but reject live-looking entries again locally.
                val stream=item.optString("live_status")
                if(stream in listOf("is_live","is_upcoming")||item.optBoolean("is_live",false))continue
                videos.add(VideoModel("https://www.youtube.com/watch?v="+id,title,
                    item.optString("channel"),item.optLong("views",-1L),
                    item.optLong("duration",-1L),"",item.optString("thumbnail").ifBlank{
                        "https://i.ytimg.com/vi/"+id+"/hqdefault.jpg"
                    },"",false,false))
            }
            return videos.distinctBy{it.url}
        }finally{conn.disconnect()}
    }
    fun search(term:String):List<VideoModel> {
        if(term.length<2)return emptyList()
        return fetch("/search?q="+Uri.encode(term.take(110))+"&lang=ar")
    }
    /** Cached content first, one failing category must never blank the homepage. */
    fun home():List<VideoModel> {
        val all=mutableListOf<VideoModel>()
        for(cat in categories.take(4)){
            try {
                all.addAll(fetch("/discover?category="+cat+"&lang=ar"))
            }catch(_:Exception){}
            if(all.size>=28)break
        }
        if(all.isEmpty())all.addAll(search("فيديوهات عربية جديدة"))
        return all.distinctBy{it.url}.take(75)
    }
}
