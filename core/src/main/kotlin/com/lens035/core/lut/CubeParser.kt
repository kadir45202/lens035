package com.lens035.core.lut

import com.lens035.core.color.ColorMath
import com.lens035.core.film.Lut3D

class CubeParseException(message: String) : Exception(message)

/**
 * Parser for Adobe/Resolve `.cube` 3D LUT files (the format most "film LUT" packs ship in).
 * Supports LUT_3D_SIZE 2..65, DOMAIN_MIN/DOMAIN_MAX and comments. 1D LUTs are rejected with a
 * readable message.
 */
object CubeParser {
    private const val MAX_SIZE = 65

    fun parse(text: String, key: String): Lut3D {
        var size = -1
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        var values: FloatArray? = null
        var count = 0

        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.substringBefore('#').trim()
            if (line.isEmpty()) return@forEachIndexed
            val upper = line.uppercase()
            when {
                upper.startsWith("TITLE") -> Unit
                upper.startsWith("LUT_1D_SIZE") ->
                    throw CubeParseException("1D LUT desteklenmiyor; lütfen 3D .cube dosyası seçin.")
                upper.startsWith("LUT_3D_SIZE") -> {
                    size = line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull()
                        ?: throw CubeParseException("Geçersiz LUT_3D_SIZE (satır ${index + 1}).")
                    if (size < 2 || size > MAX_SIZE) throw CubeParseException("LUT boyutu $size desteklenmiyor (2–$MAX_SIZE).")
                    values = FloatArray(size * size * size * 3)
                }
                upper.startsWith("DOMAIN_MIN") -> domainMin = parseTriple(line, index)
                upper.startsWith("DOMAIN_MAX") -> domainMax = parseTriple(line, index)
                upper.startsWith("LUT_3D_INPUT_RANGE") -> {
                    val parts = line.split(Regex("\\s+"))
                    val lo = parts.getOrNull(1)?.toFloatOrNull() ?: 0f
                    val hi = parts.getOrNull(2)?.toFloatOrNull() ?: 1f
                    domainMin = floatArrayOf(lo, lo, lo)
                    domainMax = floatArrayOf(hi, hi, hi)
                }
                upper[0].isLetter() -> Unit // unknown keyword, ignore
                else -> {
                    val arr = values ?: throw CubeParseException("Veri, LUT_3D_SIZE satırından önce geldi.")
                    val t = parseTriple(line, index)
                    if (count >= arr.size / 3) throw CubeParseException("Dosyada beklenenden fazla değer var.")
                    arr[count * 3] = t[0]
                    arr[count * 3 + 1] = t[1]
                    arr[count * 3 + 2] = t[2]
                    count++
                }
            }
        }
        val arr = values ?: throw CubeParseException("LUT_3D_SIZE bulunamadı.")
        if (count != size * size * size) {
            throw CubeParseException("Eksik veri: ${size * size * size} değer bekleniyordu, $count bulundu.")
        }
        // Normalise output values from the declared domain into 0..1.
        for (i in 0 until count) {
            for (c in 0 until 3) {
                val span = domainMax[c] - domainMin[c]
                val v = if (span > 1e-6f) (arr[i * 3 + c] - domainMin[c]) / span else arr[i * 3 + c]
                arr[i * 3 + c] = ColorMath.clamp01(v)
            }
        }
        // .cube data order is red fastest, then green, then blue: identical to Lut3D.
        return Lut3D(size, arr, key)
    }

    private fun parseTriple(line: String, index: Int): FloatArray {
        val parts = line.trim().split(Regex("\\s+"))
        val start = if (parts.isNotEmpty() && parts[0].firstOrNull()?.isLetter() == true) 1 else 0
        if (parts.size < start + 3) throw CubeParseException("Satır ${index + 1}: üç sayı bekleniyordu.")
        return FloatArray(3) {
            parts[start + it].toFloatOrNull() ?: throw CubeParseException("Satır ${index + 1}: sayı okunamadı.")
        }
    }
}
