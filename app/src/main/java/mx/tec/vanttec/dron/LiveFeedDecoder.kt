package mx.tec.vanttec.dron

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import android.util.Log
import android.view.Surface
import io.reactivex.Observable
import io.reactivex.ObservableEmitter

class LiveFeedDecoder(context: Context, private val liveFeedObservable: Observable<LiveFeedData>) :
        Runnable {

    // Drone live feed is 720p
    private val nativeWidth = 1280
    private val nativeHeight = 720

    // Constants
    private val tag = "NumberDetector"
    private val mime = "video/avc"

    // Async observables
    private val inputBufferObservable : Observable<Pair<MediaCodec, Int>>
    private val outputBufferObservable : Observable<Pair<MediaCodec, Int>>

    // Live feed YUV to RGB vars
    private val rs = RenderScript.create(context)
    private val yuvToRGB = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

    init {
        val format = MediaFormat.createVideoFormat(mime, nativeWidth, nativeHeight)
        val decoderName = MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .findDecoderForFormat(format)

        var inBuffEmitter: ObservableEmitter<Pair<MediaCodec, Int>>? = null
        var outBuffEmitter: ObservableEmitter<Pair<MediaCodec, Int>>? = null

        inputBufferObservable = Observable.create<Pair<MediaCodec, Int>> {
            inBuffEmitter = it
        }.publish().autoConnect()

        outputBufferObservable = Observable.create<Pair<MediaCodec, Int>> {
            outBuffEmitter = it
        }.publish().autoConnect()

        if(decoderName != null) {
            val decoder = MediaCodec.createByCodecName(decoderName)

            decoder.setCallback(object : MediaCodec.Callback() {
                override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo?) {
                    outBuffEmitter?.onNext(Pair(codec, index))
                }

                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    inBuffEmitter?.onNext(Pair(codec, index))
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat?) {
                    Log.d(tag, "Format changed to $format")
                }

                override fun onError(codec: MediaCodec?, e: MediaCodec.CodecException?) {
                    outBuffEmitter?.onError(e ?: Exception("Unknown error in live feed decoding"))
                    inBuffEmitter?.onError(e ?: Exception("Unknown error in live feed decoding"))
                }

            })

            decoder.configure(format, null, null, 0)
            decoder.start()
        } else {
            throw MissingCodecException(mime)
        }
    }

    private fun decodeYUV(data: ByteArray, width: Int, height: Int) : Bitmap {
        val yuvType = Type.Builder(rs, Element.U8(rs)).setX(data.size)
        val input = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT)

        val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height)
        val output = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        input.copyFrom(data)

        yuvToRGB.setInput(input)
        yuvToRGB.forEach(output)

        output.copyTo(bitmap)

        return bitmap
    }

    override fun run() {
        val startTime = System.currentTimeMillis()
        var liveFeedData : ByteArray? = null
        var liveFeedLen : Int = 0
        var surface : Surface? = null

        liveFeedObservable.subscribe { (data, len) ->
            liveFeedData = data
            liveFeedLen = len
        }

        inputBufferObservable.subscribe { (decoder, index) ->
            val presentationTime = (startTime - System.currentTimeMillis()) * 1000
            val buffer = decoder.getInputBuffer(index)

            if(liveFeedData != null) {
                buffer.put(liveFeedData)
                decoder.queueInputBuffer(index, 0, liveFeedLen, presentationTime, 0)
            }
        }

        outputBufferObservable.map { (decoder, index) ->
            val frameData = decoder.getOutputBuffer(index).array()
            val frameBitmap = decodeYUV(frameData, nativeWidth, nativeHeight)

            decoder.releaseOutputBuffer(index, System.nanoTime())

            frameBitmap
        }

    }

    data class LiveFeedData(val buffer: ByteArray, val length: Int)
    class MissingCodecException(mime: String) : Exception("No decoder found for $mime")
}