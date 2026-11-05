package com.asistente.voz

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telephony.PhoneNumberUtils
import android.view.KeyEvent

class Acciones(private val ctx: Context) {

    data class Contacto(val nombre: String, val numero: String)

    private val paquetesWhatsapp = listOf("com.whatsapp", "com.whatsapp.w4b")

    // ---------- Contactos ----------

    fun buscarContacto(consulta: String): Contacto? {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val q = normalizar(consulta).trim()
        if (q.isEmpty()) return null
        var parecido: Contacto? = null
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val nombre = c.getString(0) ?: continue
                val numero = c.getString(1) ?: continue
                val n = normalizar(nombre)
                if (n == q) return Contacto(nombre, numero)
                if (parecido == null && n.contains(q)) parecido = Contacto(nombre, numero)
            }
        }
        return parecido
    }

    private fun aInternacional(numero: String): String? {
        val e164 = PhoneNumberUtils.formatNumberToE164(numero, "AR")
            ?: numero.filter { it.isDigit() || it == '+' }.takeIf { it.startsWith("+") }
        return e164?.filter { it.isDigit() }
    }

    // ---------- WhatsApp ----------

    /** Abre el chat con el mensaje escrito; el servicio de accesibilidad aprieta "Enviar". */
    fun whatsappMensaje(contacto: Contacto, texto: String): Boolean {
        val tel = aInternacional(contacto.numero) ?: return false
        val uri = Uri.parse("https://wa.me/$tel?text=" + Uri.encode(texto))
        AsistenteAccesibilidad.pedirEnvio()
        for (paquete in paquetesWhatsapp) {
            val i = Intent(Intent.ACTION_VIEW, uri)
                .setPackage(paquete)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (lanzar(i)) return true
        }
        return false
    }

    /** Llamada de voz por WhatsApp usando la fila de contacto que WhatsApp mismo crea. */
    fun whatsappLlamada(consulta: String): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return false
        val mime = "vnd.android.cursor.item/vnd.com.whatsapp.voip.call"
        val q = normalizar(consulta).trim()
        var idElegido: Long? = null
        ctx.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DISPLAY_NAME),
            "${ContactsContract.Data.MIMETYPE}=?",
            arrayOf(mime),
            null
        )?.use { c ->
            while (c.moveToNext()) {
                val n = normalizar(c.getString(1) ?: continue)
                if (n == q) { idElegido = c.getLong(0); break }
                if (idElegido == null && n.contains(q)) idElegido = c.getLong(0)
            }
        }
        val id = idElegido ?: return false
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://com.android.contacts/data/$id"), mime)
            .setPackage("com.whatsapp")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return lanzar(i)
    }

    // ---------- Spotify y volumen ----------

    fun spotify(consulta: String): Boolean {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage("com.spotify.music")
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, consulta)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = lanzar(i)
        if (ok) AsistenteAccesibilidad.volverAtras(3500)
        return ok
    }

    fun tecla(codigo: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, codigo))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, codigo))
    }

    fun volumen(subir: Boolean) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val dir = if (subir) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        repeat(2) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI) }
    }

    private fun lanzar(i: Intent): Boolean =
        try {
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
}
