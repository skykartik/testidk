package com.bubbly.calc

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.*

/**
 * SoundPool is Android's own API for exactly this job - many short, overlapping game/UI sounds -
 * so unlike the long saga on the Windows build, genuine overlapping playback during a spam of key
 * presses should just work here by design, with no pooling or GC-timing tricks required.
 */
class Sfx(context: Context) {
    var enabled = true
    private val pool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val ids = HashMap<String, Int>()
    private val loaded = HashSet<Int>()
    private val sampleRate = 44100

    init {
        // load() is asynchronous - a sound isn't actually playable the instant load() returns.
        // Learned this the hard way on the Windows build with a near-identical bug in MediaPlayer;
        // tracking readiness explicitly here instead of assuming it'll finish in time.
        pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded.add(sampleId) }
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        val freqs = doubleArrayOf(392.0, 440.0, 523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.5)
        for ((k, fr) in freqs.withIndex()) {
            make(dir, "d$k", { t -> bubble(t, fr * 0.6, fr, 20.0) * 0.55 }, 0.4)
        }
        make(dir, "op", { t -> bubble(t, 250.0, 470.0, 18.0) * 0.7 }, 0.26)
        make(dir, "fn", { t -> bubble(t, 400.0, 620.0, 20.0) * 0.6 }, 0.22)
        make(dir, "tick", { t -> bubble(t, 660.0, 920.0, 55.0, 0.01) * 0.4 }, 0.12)
        make(dir, "clr", { t -> bubble(t, 660.0, 240.0, 15.0) * 0.55 }, 0.32)
        make(dir, "err", { t -> bubble(t, 190.0, 95.0, 14.0, 0.0) * 0.6 }, 0.32)
        val n4 = doubleArrayOf(523.25, 659.25, 783.99, 1046.5)
        make(dir, "eq", { t ->
            echo({ x -> n4.withIndex().sumOf { (j, f) -> bubble(x - j * 0.085, f * 0.6, f, 8.0, 0.02) } * 0.3 }, t)
        }, 1.3)
    }

    private fun bubble(t: Double, f0: Double, f1: Double, decay: Double, wob: Double = 0.015): Double {
        if (t < 0) return 0.0
        val sweep = 0.03
        val freq = if (t < sweep) f0 + (f1 - f0) * (t / sweep) else f1
        val vib = 1 + wob * sin(2 * PI * 11 * t)
        val env = min(1.0, t / 0.003) * exp(-t * decay)
        val ph = 2 * PI * freq * vib * t
        return env * (sin(ph) + 0.18 * sin(2 * ph))
    }

    private fun echo(f: (Double) -> Double, t: Double) = f(t) + 0.22 * f(t - 0.08) + 0.09 * f(t - 0.16)

    private fun make(dir: File, name: String, gen: (Double) -> Double, secs: Double) {
        val file = File(dir, "$name.wav")
        if (!file.exists()) writeWav(file, gen, secs)
        ids[name] = pool.load(file.absolutePath, 1)
    }

    private fun writeWav(file: File, gen: (Double) -> Double, secs: Double) {
        val n = (sampleRate * secs).toInt()
        val dataSize = n * 2
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            fun le16(v: Int) { f.write(v and 0xFF); f.write((v shr 8) and 0xFF) }
            fun le32(v: Int) {
                f.write(v and 0xFF); f.write((v shr 8) and 0xFF)
                f.write((v shr 16) and 0xFF); f.write((v shr 24) and 0xFF)
            }
            f.writeBytes("RIFF"); le32(36 + dataSize); f.writeBytes("WAVE")
            f.writeBytes("fmt "); le32(16); le16(1); le16(1); le32(sampleRate)
            le32(sampleRate * 2); le16(2); le16(16)
            f.writeBytes("data"); le32(dataSize)
            val buf = ByteArray(dataSize)
            for (i in 0 until n) {
                var v = gen(i.toDouble() / sampleRate)
                if (v > 1.0) v = 1.0; if (v < -1.0) v = -1.0
                val s = (v * 14000).toInt()
                buf[i * 2] = (s and 0xFF).toByte()
                buf[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
            }
            f.write(buf)
        }
    }

    fun play(name: String) {
        if (!enabled) return
        val id = ids[name] ?: return
        if (id !in loaded) return // still decoding (only possible in the first instant after launch) - skip rather than risk a stuck/garbled stream
        pool.play(id, 0.6f, 0.6f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
