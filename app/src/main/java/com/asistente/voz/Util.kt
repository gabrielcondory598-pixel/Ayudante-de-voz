package com.asistente.voz

import android.content.Context
import java.text.Normalizer
import java.util.Locale

/** Pasa a minúsculas y le saca los acentos (conserva el largo del texto en español). */
fun normalizar(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(Locale.ROOT)

object Config {
    private const val PREFS = "config"
    private const val CONFIRMAR = "confirmar"

    fun confirmar(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(CONFIRMAR, true)

    fun setConfirmar(ctx: Context, valor: Boolean) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(CONFIRMAR, valor).apply()
    }
}
