package mx.tec.vanttec.dron

import android.content.Context
import android.graphics.Bitmap
import android.preference.PreferenceManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.Socket

class BaseConnection(context: Context) {
    private val socket: Socket

    init {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val baseAddress = prefs.getString("base_addr", "192.168.0.201")
        val port = prefs.getString("base_port", "5000")

        socket = Socket(baseAddress, port.toInt())
    }

    fun sendDock(num: Int) {
        val header = "DockNum"

        send(header + END_OF_HEADER + num.toString())
    }

    fun sendMap(width: Int, height: Int, obstacles: List<VTPoint>, boat: VTPoint, post: VTPoint) {
        val header = "Map"

        val body = JSONObject(mapOf(
                "width" to width,
                "height" to height,
                "buoypos" to JSONArray(obstacles.map {
                    (x, y) -> JSONArray(arrayListOf(x, y))
                }),
                "boatLocation" to JSONArray(arrayListOf(boat.first, boat.second)),
                "circleCan" to JSONArray(arrayListOf(post.first, post.second))
        ))

        send(header + body.toString())
    }

    fun sendImage(bitmap: Bitmap) {
        val os = ByteArrayOutputStream(bitmap.byteCount)

        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, os)

        val compressed = os.toByteArray()
        var offset = 0

        val header = "DockPhoto,${compressed.size},$offset,"

        val payloadSize = MAX_TRANSMIT - (header.length)

        while(compressed.size - offset > payloadSize) {

        }
    }

    fun send(data: String) {
        val writer = socket.getOutputStream()
                .writer()

        writer.write(data)
    }

    companion object {
        const val MAX_TRANSMIT = 1024
        const val END_OF_HEADER = "kthanksbye"
    }
}