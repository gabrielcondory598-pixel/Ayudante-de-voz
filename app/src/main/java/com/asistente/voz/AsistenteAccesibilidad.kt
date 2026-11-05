package com.asistente.voz

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Solo actúa dentro de WhatsApp (ver accessibility_config.xml).
 * Cuando el asistente abre un chat con el mensaje escrito, toca el botón "Enviar"
 * y después vuelve atrás para que regreses a tu juego.
 */
class AsistenteAccesibilidad : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        @Volatile private var instancia: AsistenteAccesibilidad? = null
        @Volatile private var envioHasta = 0L

        /** Avisa que en los próximos 20 segundos hay que apretar "Enviar" en WhatsApp. */
        fun pedirEnvio() {
            envioHasta = System.currentTimeMillis() + 20_000
        }

        /** Aprieta "atrás" una vez, después de [retrasoMs]. */
        fun volverAtras(retrasoMs: Long) {
            val s = instancia ?: return
            s.handler.postDelayed({
                s.performGlobalAction(GLOBAL_ACTION_BACK)
            }, retrasoMs)
        }
    }

    override fun onServiceConnected() {
        instancia = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instancia = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (System.currentTimeMillis() > envioHasta) return
        val raiz = rootInActiveWindow ?: return

        var boton: AccessibilityNodeInfo? = null
        for (id in listOf("com.whatsapp:id/send", "com.whatsapp.w4b:id/send")) {
            boton = raiz.findAccessibilityNodeInfosByViewId(id).firstOrNull()
            if (boton != null) break
        }
        if (boton == null) {
            for (texto in listOf("Enviar", "Send")) {
                boton = raiz.findAccessibilityNodeInfosByText(texto).firstOrNull { it.isClickable }
                if (boton != null) break
            }
        }

        if (boton != null && boton.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            envioHasta = 0
            // Primer "atrás" cierra el teclado, el segundo sale del chat.
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 1200)
            handler.postDelayed({ performGlobalAction(GLOBAL_ACTION_BACK) }, 1900)
        }
    }

    override fun onInterrupt() {}
}
