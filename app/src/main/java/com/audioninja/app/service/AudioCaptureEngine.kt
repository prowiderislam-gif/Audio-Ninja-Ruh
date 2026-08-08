package com.audioninja.app.service

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RequiresApi(Build.VERSION_CODES.Q)
class AudioCaptureEngine {

    private var audioRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private var captureThread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start(projection: MediaProjection, outputFile: File, sampleRate: Int, bitrate: Int, stereo: Boolean) {
        try {
            setupAndStart(projection, outputFile, sampleRate, bitrate, stereo)
        } catch (e: Exception) {
            releaseAll()
            throw e
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupAndStart(
        projection: MediaProjection,
        outputFile: File,
        sampleRate: Int,
        bitrate: Int,
        stereo: Boolean
    ) {
        val channelCount = if (stereo) 2 else 1
        val channelMask = if (stereo) AudioFormat.CHANNEL_IN_STEREO else AudioFormat.CHANNEL_IN_MONO

        val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(projection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(sampleRate)
            .setChannelMask(channelMask)
            .build()

        val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize <= 0) {
            throw IllegalStateException("Device doesn't support this audio configuration.")
        }

        audioRecord = AudioRecord.Builder()
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(minBufferSize * 2)
            .setAudioPlaybackCaptureConfig(playbackConfig)
            .build()

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize — permission may be missing.")
        }

        val outFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBufferSize)
        }

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(outFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        audioRecord?.startRecording()
        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            throw IllegalStateException("AudioRecord couldn't start — permission may be missing or denied.")
        }

        encoder?.start()
        running.set(true)
        paused.set(false)

        captureThread = Thread {
            val pcmBuffer = ByteArray(minBufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var totalPresentationTimeUs = 0L

            while (running.get()) {
                if (paused.get()) {
                    Thread.sleep(50)
                    continue
                }
                val read = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0
                if (read > 0) {
                    val inputIndex = encoder?.dequeueInputBuffer(10000) ?: -1
                    if (inputIndex >= 0) {
                        val inputBuffer = encoder?.getInputBuffer(inputIndex)
                        inputBuffer?.clear()
                        inputBuffer?.put(pcmBuffer, 0, read)
                        val presentationTimeUs = totalPresentationTimeUs
                        totalPresentationTimeUs += (read * 1_000_000L) / (sampleRate * channelCount * 2)
                        encoder?.queueInputBuffer(inputIndex, 0, read, presentationTimeUs, 0)
                    }
                }
                drainEncoder(bufferInfo)
            }

            val inputIndex = encoder?.dequeueInputBuffer(10000) ?: -1
            if (inputIndex >= 0) {
                encoder?.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
            drainEncoder(bufferInfo, endOfStream = true)
        }
        captureThread?.start()
    }

    private fun drainEncoder(bufferInfo: MediaCodec.BufferInfo, endOfStream: Boolean = false) {
        val enc = encoder ?: return
        while (true) {
            val outputIndex = enc.dequeueOutputBuffer(bufferInfo, 10000)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    trackIndex = muxer?.addTrack(enc.outputFormat) ?: -1
                    muxer?.start()
                    muxerStarted = true
                }
                outputIndex >= 0 -> {
                    val outputBuffer = enc.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0 && muxerStarted) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer?.writeSampleData(trackIndex, outputBuffer, bufferInfo)
                    }
                    enc.releaseOutputBuffer(outputIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    fun pause() {
        paused.set(true)
    }

    fun resume() {
        paused.set(false)
    }

    fun stop() {
        running.set(false)
        try {
            captureThread?.join(500)
        } catch (_: Exception) { }
        captureThread = null
        releaseAll()
    }

    private fun releaseAll() {
        try {
            audioRecord?.stop()
        } catch (_: Exception) { }
        try {
            audioRecord?.release()
        } catch (_: Exception) { }
        audioRecord = null
        try {
            encoder?.stop()
        } catch (_: Exception) { }
        try {
            encoder?.release()
        } catch (_: Exception) { }
        encoder = null
        try {
            if (muxerStarted) muxer?.stop()
        } catch (_: Exception) { }
        try {
            muxer?.release()
        } catch (_: Exception) { }
        muxer = null
        muxerStarted = false
    }
}
