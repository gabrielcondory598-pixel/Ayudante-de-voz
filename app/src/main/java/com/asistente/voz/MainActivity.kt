package com.asistente.voz

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

class MainActivity : Activity() {

    private lateinit var estado: TextView

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val columna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        columna.addView(TextView(this).apply {
            text = "Asistente de Voz"
            textSize = 26f
            setPadding(0, 0, 0, dp(8))
        })

        estado = TextView(this).apply {
            textSize = 15f
            setPadding(0, dp(4), 0, dp(12))
        }
        columna.addView(estado)

        columna.addView(boton("1. Dar permisos (micrófono, contactos, avisos)") { pedirPermisos() })
        columna.addView(boton("2. Permitir mostrar sobre otras apps") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        columna.addView(boton("3. Activar accesibilidad (para enviar solo)") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        columna.addView(boton("4. Sin ahorro de batería (para que no se corte)") {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })

        columna.addView(CheckBox(this).apply {
            text = "Preguntarme «¿Lo envío?» antes de mandar o llamar"
            isChecked = Config.confirmar(this@MainActivity)
            setOnCheckedChangeListener { _, marcado -> Config.setConfirmar(this@MainActivity, marcado) }
        })

        columna.addView(boton("▶ Iniciar asistente") { iniciar() })
        columna.addView(boton("■ Detener asistente") {
            stopService(Intent(this, VoiceService::class.java))
            estado.postDelayed({ actualizar() }, 500)
        })

        columna.addView(TextView(this).apply {
            text = "Ejemplos (decí primero «asistente»):\n" +
                "• asistente, poné música de Duki\n" +
                "• asistente, pausa / siguiente / subí el volumen\n" +
                "• asistente, llamá a Mamá\n" +
                "• asistente, mandale a Juan que ya voy\n" +
                "• asistente, qué hora es\n" +
                "• asistente, apagate"
            textSize = 14f
            gravity = Gravity.START
            setPadding(0, dp(16), 0, 0)
        })

        setContentView(ScrollView(this).apply { addView(columna) })
    }

    private fun boton(texto: String, accion: () -> Unit) = Button(this).apply {
        text = texto
        isAllCaps = false
        setOnClickListener { accion() }
    }

    override fun onResume() {
        super.onResume()
        actualizar()
    }

    private fun permisosNecesarios(): List<String> {
        val l = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) l.add(Manifest.permission.POST_NOTIFICATIONS)
        return l
    }

    private fun tienePermisos() = permisosNecesarios().all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun pedirPermisos() {
        requestPermissions(permisosNecesarios().toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        actualizar()
    }

    private fun accesibilidadActiva(): Boolean {
        val activos = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        return activos.contains(packageName)
    }

    private fun iniciar() {
        if (!tienePermisos()) {
            Toast.makeText(this, "Primero tocá el botón 1 y aceptá los permisos", Toast.LENGTH_LONG).show()
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, VoiceService::class.java))
        estado.postDelayed({ actualizar() }, 700)
    }

    private fun actualizar() {
        fun marca(ok: Boolean) = if (ok) "✅" else "❌"
        estado.text =
            "${marca(tienePermisos())} Permisos (micrófono, contactos, avisos)\n" +
            "${marca(Settings.canDrawOverlays(this))} Mostrar sobre otras apps\n" +
            "${marca(accesibilidadActiva())} Accesibilidad (enviar mensajes solo)\n" +
            "${if (VoiceService.corriendo) "🟢 Asistente escuchando" else "⚪ Asistente apagado"}"
    }
}
