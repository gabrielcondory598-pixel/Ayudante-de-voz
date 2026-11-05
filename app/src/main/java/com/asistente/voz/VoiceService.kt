package com.asistente.voz

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import java.util.Calendar
import java.util.Locale

/**
 * Escucha todo el tiempo (servicio en primer plano). Cuando oye "asistente" + una orden,
 * la ejecuta. Si hay una acción pendiente de confirmar, acepta "sí" / "no" sin palabra clave.
 */
class VoiceService : Service(), RecognitionListener {

    companion object {
        const val CANAL = "asistente"
        const val PALABRA = "asistente"
        @Volatile var corriendo = false

        private val MSJ = Regex("""^(?:mandale|mandar|manda|mandame|enviale|enviar|envia|escribile|escribir|escribe|avisale|avisar|dile|decile|decirle)\s+(?:un\s+)?(?:mensaje\s+|whatsapp\s+|wasap\s+)?(?:por\s+whatsapp\s+)?(?:a\s+)?(.+?)\s+(?:que\s+diga|diciendo|que)\s+(.+)$""")
        private val LLAMAR = Regex("""^(?:llama|llamar|llamame|llamale|llamalo|llamala|telefonea)\s+(?:por\s+whatsapp\s+)?(?:a\s+)?(.+?)(?:\s+por\s+whatsapp)?$""")
        private val MUSICA = Regex("""^(?:pon|pone|poneme|ponme|poner|reproduce|reproduci|toca|tocame|busca)\s+(?:la\s+|el\s+|algo\s+de\s+)?(?:musica\s+(?:de\s+)?|cancion\s+(?:de\s+)?|tema\s+(?:de\s+)?|playlist\s+(?:de\s+)?|disco\s+(?:de\s+)?|a\s+)?(.+)$""")
        private val PAUSA = Regex("""^(?:pausa|pausar|pausa la musica|para la musica|parar la musica|detene la musica|deten la musica|silencio)$""")
        private val SEGUIR = Regex("""^(?:continua|continuar|seguir|sigue|segui|reanuda|reanudar|play|dale play)$""")
        private val SIGUIENTE = Regex("""^(?:siguiente|siguiente cancion|proxima|proximo|salta|saltar|cambia|cambia la cancion|otra|otra cancion|pasa|pasa la cancion)$""")
        private val ANTERIOR = Regex("""^(?:anterior|cancion anterior|vuelve|volve|atras|retrocede)$""")
        private val SUBIR = Regex("""^(?:sube|subi|subir|sube el volumen|subi el volumen|subir volumen|mas volumen)$""")
        private val BAJAR = Regex("""^(?:baja|bajar|baja el volumen|bajar volumen|menos volumen)$""")
        private val HORA = Regex("""^(?:que hora es|la hora|hora)$""")
        private val APAGAR = Regex("""^(?:apagate|detente|detenete|desactivate|chau|adios|apagar)$""")

        private val SI = setOf("si", "dale", "enviar", "enviala", "enviale", "confirmo", "ok", "manda", "mandalo", "listo", "llama")
        private val NO = setOf("no", "cancela", "cancelar", "cancelalo")
    }

    private lateinit var acciones: Acciones
    private var reconocedor: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private val h = Handler(Looper.getMainLooper())

    @Volatile private var hablando = false
    private var activo = false
    private var esperandoOrdenHasta = 0L
    private var pendiente: (() -> Unit)? = null
    private var pendienteHasta = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acciones = Acciones(this)
        val canal = NotificationChannel(CANAL, "Asistente de voz", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(canal)

        tts = TextToSpeech(this) { estado ->
            if (estado == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("es", "AR"))
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale("es"))
                }
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { hablando = true }
                    override fun onDone(utteranceId: String?) { hablando = false }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { hablando = false }
                })
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "parar") {
            stopSelf()
            return START_NOT_STICKY
        }
        val tipo = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, 1, notificacion(), tipo)
        if (!activo) {
            activo = true
            corriendo = true
            escuchar()
            decir("Asistente listo")
        }
        return START_STICKY
    }

    private fun notificacion(): Notification {
        val parar = PendingIntent.getService(
            this, 0,
            Intent(this, VoiceService::class.java).setAction("parar"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CANAL)
            .setContentTitle("Asistente escuchando")
            .setContentText("Decí «asistente» y tu orden")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(0, "Detener", parar)
            .build()
    }

    // ---------- Reconocimiento de voz ----------

    private fun escuchar() {
        if (!activo) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            decir("Este celular no tiene reconocimiento de voz")
            stopSelf()
            return
        }
        reconocedor?.destroy()
        reconocedor = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(this)
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-AR")
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    // Sesiones más largas: menos aperturas y menos "bip".
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 8000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 6000L)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                }
            )
        }
    }

    private fun reiniciar(ms: Long) {
        h.postDelayed({ escuchar() }, ms)
    }

    override fun onResults(results: Bundle?) {
        val lista = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!hablando && !lista.isNullOrEmpty()) procesar(lista)
        reiniciar(300)
    }

    override fun onError(error: Int) {
        when (error) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                decir("Me falta el permiso del micrófono")
                stopSelf()
            }
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT -> reiniciar(1500)
            else -> reiniciar(300)
        }
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {}
    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    // ---------- Interpretar lo que se dijo ----------

    private fun procesar(candidatos: List<String>) {
        val ahora = System.currentTimeMillis()

        // 1) ¿Estamos esperando un "sí" o un "no"?
        if (pendiente != null) {
            if (ahora > pendienteHasta) {
                pendiente = null
            } else {
                val palabras = normalizar(candidatos[0]).split(" ").filter { it.isNotBlank() }
                if (palabras.size <= 3) {
                    if (palabras.any { it in SI }) {
                        val accion = pendiente
                        pendiente = null
                        accion?.invoke()
                        return
                    }
                    if (palabras.any { it in NO }) {
                        pendiente = null
                        decir("Cancelado")
                        return
                    }
                }
            }
        }

        // 2) Buscar la palabra de activación
        val texto = candidatos.firstOrNull { normalizar(it).contains(PALABRA) } ?: candidatos[0]
        val raw = texto.trim()
        val n = normalizar(raw)
        val i = n.indexOf(PALABRA)

        val ordenRaw: String
        val ordenN: String
        if (i >= 0) {
            val desde = i + PALABRA.length
            val nSub = n.substring(desde)
            val rawSub = if (raw.length == n.length) raw.substring(desde) else nSub
            var a = 0
            var b = nSub.length
            while (a < b && !nSub[a].isLetterOrDigit()) a++
            while (b > a && !nSub[b - 1].isLetterOrDigit()) b--
            ordenN = nSub.substring(a, b)
            ordenRaw = rawSub.substring(a, b)
        } else if (ahora < esperandoOrdenHasta) {
            ordenRaw = raw
            ordenN = n
        } else {
            return
        }

        if (ordenN.isEmpty()) {
            esperandoOrdenHasta = ahora + 8000
            decir("Decime")
            return
        }
        esperandoOrdenHasta = 0
        ejecutar(ordenRaw, ordenN)
    }

    private fun ejecutar(raw: String, n: String) {
        MSJ.matchEntire(n)?.let { m ->
            val nombre = m.groupValues[1]
            val rango = m.groups[2]!!.range
            val mensaje = if (raw.length == n.length) raw.substring(rango) else m.groupValues[2]
            mensaje(nombre, mensaje)
            return
        }
        LLAMAR.matchEntire(n)?.let { m ->
            llamar(m.groupValues[1])
            return
        }
        when {
            PAUSA.matches(n) -> acciones.tecla(KeyEvent.KEYCODE_MEDIA_PAUSE)
            SEGUIR.matches(n) -> acciones.tecla(KeyEvent.KEYCODE_MEDIA_PLAY)
            SIGUIENTE.matches(n) -> acciones.tecla(KeyEvent.KEYCODE_MEDIA_NEXT)
            ANTERIOR.matches(n) -> acciones.tecla(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            SUBIR.matches(n) -> acciones.volumen(true)
            BAJAR.matches(n) -> acciones.volumen(false)
            HORA.matches(n) -> {
                val c = Calendar.getInstance()
                decir("Son las %d y %02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))
            }
            APAGAR.matches(n) -> {
                decir("Hasta luego")
                h.postDelayed({ stopSelf() }, 1500)
            }
            else -> {
                val m = MUSICA.matchEntire(n)
                if (m != null) {
                    val q = m.groupValues[1].trim()
                    if (q in setOf("musica", "algo", "algo de musica", "cancion")) {
                        acciones.tecla(KeyEvent.KEYCODE_MEDIA_PLAY)
                    } else if (acciones.spotify(q)) {
                        decir("Poniendo $q")
                    } else {
                        decir("No pude abrir Spotify")
                    }
                } else {
                    decir("No entendí. Probá con: poné música de, llamá a, o mandale a")
                }
            }
        }
    }

    private fun mensaje(nombreDicho: String, texto: String) {
        val contacto = acciones.buscarContacto(nombreDicho)
        if (contacto == null) {
            decir("No encontré a $nombreDicho en tus contactos")
            return
        }
        val hacer = {
            if (acciones.whatsappMensaje(contacto, texto)) decir("Enviando mensaje a ${contacto.nombre}")
            else decir("No pude abrir WhatsApp")
        }
        pedirConfirmacion("Le mando a ${contacto.nombre}: $texto. ¿Lo envío?", hacer)
    }

    private fun llamar(nombreDicho: String) {
        val contacto = acciones.buscarContacto(nombreDicho)
        if (contacto == null) {
            decir("No encontré a $nombreDicho en tus contactos")
            return
        }
        val hacer = {
            if (acciones.whatsappLlamada(contacto.nombre)) decir("Llamando a ${contacto.nombre}")
            else decir("No encontré a ${contacto.nombre} en WhatsApp")
        }
        pedirConfirmacion("¿Llamo a ${contacto.nombre} por WhatsApp?", hacer)
    }

    private fun pedirConfirmacion(pregunta: String, accion: () -> Unit) {
        if (Config.confirmar(this)) {
            pendiente = accion
            pendienteHasta = System.currentTimeMillis() + 12_000
            decir(pregunta)
        } else {
            accion()
        }
    }

    private fun decir(texto: String) {
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "u" + System.nanoTime())
    }

    override fun onDestroy() {
        activo = false
        corriendo = false
        h.removeCallbacksAndMessages(null)
        reconocedor?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
