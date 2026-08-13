package com.audioninja.app.service

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Detects and trims leading silence from a recorded file — specifically the dead
 * air captured while Android's permission dialogs were still on screen. Only
 * standard Android media APIs are used (no external libraries), so this should
 * be reliable across devices.
 *
 * Safe by design: if anything goes wrong, the original file is left untouched
 * and the function simply returns false.
 */
object AudioPostProcessor {

    private const val SILENCE_AMPLITUDE_THRESHOLD = 600 // 16-bit PCM sample magnitude
    private const val MAX_SCAN_US = 6_000_000L // never scan more than the first 6 seconds

    fun trimStartupSilence(file: File): Boolean {
        val trimmedFile = File(file.parentFile, "${file.nameWithoutExtension}_trimmed.${file.extension}")
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null

        try {
            extractor = MediaExtractor()
            extractor.setDataSource(file.absolutePath)

            var audioTrackIndex = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = f
                    break
                }
            }
            if (audioTrackIndex < 0 || format == null) return false
            extractor.selectTrack(audioTrackIndex)

            val mime = format.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var silenceEndUs = -1L
            var sawInputEOS = false

            while (silenceEndUs < 0) {
                if (!sawInputEOS) {
                    val inIndex = decoder.dequeueInputBuffer(10000)
                    if (inIndex >= 0) {
                        val inBuffer = decoder.getInputBuffer(inIndex)!!
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                if (outIndex >= 0) {
                    val outBuffer = decoder.getOutputBuffer(outIndex)!!
                    val chunk = ByteArray(bufferInfo.size)
                    if (bufferInfo.size > 0) {
                        outBuffer.get(chunk)
                    }
                    outBuffer.clear()

                    var maxAmp = 0
                    var i = 0
                    while (i + 1 < chunk.size) {
                        val sample = (chunk[i].toInt() and 0xFF) or (chunk[i + 1].toInt() shl 8)
                        val amp = kotlin.math.abs(sample.toShort().toInt())
                        if (amp > maxAmp) maxAmp = amp
                        i += 2
                    }

                    if (maxAmp > SILENCE_AMPLITUDE_THRESHOLD) {
                        silenceEndUs = bufferInfo.presentationTimeUs
                    }

                    val isEOS = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    val pastMaxScan = bufferInfo.presentationTimeUs > MAX_SCAN_US
                    decoder.releaseOutputBuffer(outIndex, false)

                    if (isEOS || pastMaxScan) break
                }
            }

            try { decoder.stop() } catch (_: Exception) { }
            try { decoder.release() } catch (_: Exception) { }
            decoder = null
            extractor.release()
            extractor = null

            // No clear silence boundary found (e.g. audio starts immediately) — nothing to trim.
            if (silenceEndUs <= 0) return false

            val readExtractor = MediaExtractor()
            readExtractor.setDataSource(file.absolutePath)
            readExtractor.selectTrack(audioTrackIndex)
            readExtractor.seekTo(silenceEndUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val muxer = MediaMuxer(trimmedFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val muxInfo = MediaCodec.BufferInfo()
            var baseTimeUs = -1L

            while (true) {
                buffer.clear()
                val sampleSize = readExtractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val sampleTime = readExtractor.sampleTime
                if (baseTimeUs < 0) baseTimeUs = sampleTime

                muxInfo.offset = 0
                muxInfo.size = sampleSize
                muxInfo.presentationTimeUs = (sampleTime - baseTimeUs).coerceAtLeast(0)
                muxInfo.flags = if (readExtractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0

                muxer.writeSampleData(muxerTrackIndex, buffer, muxInfo)
                readExtractor.advance()
            }

            try { muxer.stop() } catch (_: Exception) { }
            muxer.release()
            readExtractor.release()

            return if (trimmedFile.exists() && trimmedFile.length() > 0) {
                file.delete()
                trimmedFile.renameTo(file)
            } else {
                try { trimmedFile.delete() } catch (_: Exception) { }
                false
            }
        } catch (e: Exception) {
            try { trimmedFile.delete() } catch (_: Exception) { }
            return false
        } finally {
            try { decoder?.stop() } catch (_: Exception) { }
            try { decoder?.release() } catch (_: Exception) { }
            try { extractor?.release() } catch (_: Exception) { }
        }
    }
}
