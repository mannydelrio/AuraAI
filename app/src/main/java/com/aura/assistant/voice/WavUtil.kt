/*
 * Aura — wrap raw 16-bit PCM in a minimal WAV container so cloud STT can read it.
 */
package com.aura.assistant.voice

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavUtil {

  fun pcm16ToWav(pcm: ByteArray, sampleRate: Int, channels: Int = 1): ByteArray {
    val byteRate = sampleRate * channels * 2
    val out = ByteArrayOutputStream(44 + pcm.size)

    fun writeStr(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
    fun writeInt(v: Int) =
        out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array())
    fun writeShort(v: Int) =
        out.write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(v.toShort()).array())

    writeStr("RIFF")
    writeInt(36 + pcm.size)
    writeStr("WAVE")
    writeStr("fmt ")
    writeInt(16) // PCM chunk size
    writeShort(1) // audio format = PCM
    writeShort(channels)
    writeInt(sampleRate)
    writeInt(byteRate)
    writeShort(channels * 2) // block align
    writeShort(16) // bits per sample
    writeStr("data")
    writeInt(pcm.size)
    out.write(pcm)
    return out.toByteArray()
  }
}
