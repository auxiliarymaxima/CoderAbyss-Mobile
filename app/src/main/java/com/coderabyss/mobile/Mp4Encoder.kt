package com.coderabyss.mobile

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.Image
import java.io.File
import java.io.DataInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Encode RGB frames to a silent H.264 MP4 using Android's media encoder. */
object Mp4Encoder {
    suspend fun encode(raw: File, output: File, width: Int, height: Int, frames: Int, fps: Int) {
        require(width % 2 == 0 && height % 2 == 0 && frames > 0)
        val codec = MediaCodec.createEncoderByType("video/avc")
        var muxer: MediaMuxer? = null
        var started = false
        var muxStarted = false
        try {
            val format = MediaFormat.createVideoFormat("video/avc", width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                setInteger(MediaFormat.KEY_BIT_RATE, 2_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start(); started = true
            val writer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = writer
            var track = -1
            var next = 0
            var inputEnded = false
            var ended = false
            var lastActivity = System.nanoTime()
            val info = MediaCodec.BufferInfo()
            val rgb = ByteArray(width * height * 3)
            DataInputStream(raw.inputStream().buffered()).use { input ->
                while (!ended) {
                    currentCoroutineContext().ensureActive()
                    check(System.nanoTime() - lastActivity < 60_000_000_000L) { "MP4 encoder timed out on this device." }
                    if (!inputEnded) {
                        val index = codec.dequeueInputBuffer(10_000)
                        if (index >= 0) {
                            if (next < frames) {
                                input.readFully(rgb)
                                val image = codec.getInputImage(index) ?: error("This device's MP4 encoder does not expose YUV input frames.")
                                fillYuv(image, rgb, width, height)
                                image.close()
                                codec.queueInputBuffer(index, 0, width * height * 3 / 2, next * 1_000_000L / fps, 0)
                                next++
                            } else {
                                codec.queueInputBuffer(index, 0, 0, next * 1_000_000L / fps, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputEnded = true
                            }
                            lastActivity = System.nanoTime()
                        }
                    }
                    val index = codec.dequeueOutputBuffer(info, 10_000)
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        track = writer.addTrack(codec.outputFormat); writer.start(); muxStarted = true
                        lastActivity = System.nanoTime()
                    } else if (index >= 0) {
                        val buffer = codec.getOutputBuffer(index) ?: error("Missing encoded video buffer")
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                            check(muxStarted) { "MP4 encoder produced frames without a format" }
                            buffer.position(info.offset); buffer.limit(info.offset + info.size)
                            writer.writeSampleData(track, buffer, info)
                        }
                        ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        codec.releaseOutputBuffer(index, false)
                        lastActivity = System.nanoTime()
                    }
                }
            }
            check(muxStarted) { "No video frames were encoded" }
            writer.stop(); muxStarted = false
        } finally {
            if (started) runCatching { codec.stop() }
            codec.release()
            if (muxStarted) runCatching { muxer?.stop() }
            muxer?.release()
        }
    }

    private fun fillYuv(image: Image, rgb: ByteArray, width: Int, height: Int) {
        fun set(plane: Image.Plane, x: Int, y: Int, value: Int) {
            plane.buffer.put(y * plane.rowStride + x * plane.pixelStride, value.coerceIn(0,255).toByte())
        }
        for (y in 0 until height) for (x in 0 until width) {
            val i = (y * width + x) * 3
            val r = rgb[i].toInt() and 255; val g = rgb[i+1].toInt() and 255; val b = rgb[i+2].toInt() and 255
            set(image.planes[0], x, y, ((66*r + 129*g + 25*b + 128) shr 8) + 16)
            if (x % 2 == 0 && y % 2 == 0) {
                set(image.planes[1], x/2, y/2, ((-38*r - 74*g + 112*b + 128) shr 8) + 128)
                set(image.planes[2], x/2, y/2, ((112*r - 94*g - 18*b + 128) shr 8) + 128)
            }
        }
    }
}
