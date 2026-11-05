# Asistente de Voz (Android)

App para Android que escucha mientras jugás. Decís **"asistente"** + una orden y la ejecuta:

- 🎵 **Spotify**: "asistente, poné música de Duki", "pausa", "siguiente", "subí el volumen".
- 💬 **Mensajes por WhatsApp**: "asistente, mandale a Juan que ya voy".
- 📞 **Llamadas por WhatsApp**: "asistente, llamá a Mamá".
- 🕒 "asistente, qué hora es" · "asistente, apagate".

Por seguridad, antes de mandar un mensaje o llamar te pregunta **"¿Lo envío?"** y esperás tu "sí".
Se puede apagar esa pregunta en la pantalla principal.

## Cómo conseguir el APK (instalador)

Este proyecto **no está compilado todavía**: hay que generar el APK. Dos formas:

### Opción A: con GitHub (gratis, sin instalar nada)
1. Creá una cuenta en https://github.com y un repositorio nuevo.
2. Subí **todo el contenido** de esta carpeta (incluida la carpeta oculta `.github`).
3. Entrá a la pestaña **Actions** → "Compilar APK" → **Run workflow**.
4. Cuando termine (unos minutos), abrí la ejecución y descargá **AsistenteVoz-apk** (adentro está `app-debug.apk`).

### Opción B: con Android Studio (en una computadora)
Abrí esta carpeta con Android Studio y tocá **Build → Build APK(s)**.

## Instalar en el celular
1. Pasá `app-debug.apk` al celular y abrilo (si pide permiso para "instalar apps desconocidas", aceptalo).
2. Abrí **Asistente de Voz** y tocá los botones **1, 2, 3 y 4** en orden. Cada permiso lo aceptás vos en el celular:
   1. Permisos: micrófono, contactos y avisos.
   2. Mostrar sobre otras apps (para poder abrir WhatsApp/Spotify mientras jugás).
   3. Accesibilidad → "Asistente de Voz" → activar (sirve para que toque "Enviar" solo, solo dentro de WhatsApp).
   4. Sin ahorro de batería (para que Android no lo corte).
3. Tocá **▶ Iniciar asistente**. Aparece una notificación fija: es normal, mantiene el micrófono activo.

## Límites (para que no te sorprendan)
- No fue probado en un celular real; puede hacer falta ajustar detalles según tu marca/modelo.
- Escuchar siempre gasta batería y en algunos celulares el reconocimiento de voz hace un "bip" cada pocos segundos.
- Si hay mucho ruido del juego, puede no oírte bien. Con auriculares anda mejor.
- Spotify gratis: pone música pero a veces en modo aleatorio y con anuncios.
- Los contactos deben tener el número con código de país (+54...) para que los mensajes funcionen.
- Mandar mensajes tocando botones por accesibilidad es un truco: si WhatsApp cambia su pantalla, puede dejar de funcionar. Úsalo con confianza solo con tus contactos.
- Todo el audio lo procesa el reconocimiento de voz de Google que ya tiene tu celular; esta app no guarda nada.
