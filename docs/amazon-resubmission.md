# Amazon Appstore — reenvío tras los rechazos de la 0.1.8 y la 0.1.11

Textos preparados el 2026-10-01 para la **0.1.12 (vc16)**, la versión "solo reproductor" de Amazon
(`./gradlew :app:assembleFullRelease -Pstore=amazon`, sin el botón de lista de ejemplo). Estado y
cronología en `docs/PUBLISHING.md`, sección de Amazon. **No enviar sin el visto bueno del usuario.**

## 1. Mensaje para Amazon (Contact Us)
Ruta: developer.amazon.com → *Contact Us* → tipo **Appstore** → categoría **App Submission and
Certification** → tema **App Content Policy Review Results**.

```
Subject: Easy Xtream Football (com.footballxtream) – request for details on Content Policy result for 0.1.11

Hello,

My app Easy Xtream Football (package com.footballxtream, ASIN B0HKSFXX9Z) is live on the Amazon
Appstore as version 0.1.7. Two updates have since failed Content Policy Validation with the same
message ("offers pirated content within the app, promotes links to websites that stream pirated
content, or promotes downloading via torrents"): version 0.1.8 on September 29 and version 0.1.11
on October 1.

I take this seriously and want to fix it properly, but the report does not say which screen, link
or stream triggered the result, so I would be grateful for any detail you can share.

Some context:

- The app is a media player only. It hosts no content, bundles no channels and has no links to
  websites or torrents. Users connect their own Xtream account or their own M3U playlist.
- Version 0.1.8 added a "sample playlists" button, one of which pointed to a community list of
  free-to-air sports TV. After your first rejection I checked that list, found it was not what it
  claimed to be, and removed it completely in 0.1.11.
- Version 0.1.11 kept a single optional sample playlist of sports talk radio stations (the
  stations' own public streams). If this is what triggered the second result, I understand.
- I have prepared version 0.1.12 for the Amazon Appstore with no sample playlist at all: the app
  offers no content of any kind, exactly like the approved 0.1.7. The store screenshots have been
  redone so they show no third-party playlist names, and the testing instructions use only openly
  licensed test videos (Blender Foundation films and an HLS test pattern).

Could you please confirm whether removing the sample playlist resolves the issue, or tell me what
else in the app or its listing needs to change before I submit 0.1.12? I would rather ask than
submit a third time without knowing.

Thank you for your help.

Jorge Mtnez
```

> **Enviado el 2026-10-01 (11:48), caso `22369540491`**, con "will be redone" en la frase de las capturas
> y encajado en la plantilla del campo Descripción.

## 2. Párrafo IMPORTANTE de la ficha de Amazon (vuelve al de la 0.1.7)
```
IMPORTANT: Easy Xtream Football does NOT include or provide any channels, playlists or
content. You connect your own Xtream Codes account or your own M3U/M3U8 playlist from a
provider you already use. The app is only the player.
```
```
IMPORTANTE: Easy Xtream Football NO incluye ni proporciona ningún canal, lista ni
contenido. Eres tú quien conecta tu propia cuenta de Xtream Codes o tu propia lista
M3U/M3U8 de un proveedor que ya utilices. La app es únicamente el reproductor.
```
La ficha de Play **no cambia**: allí la 0.1.11 sigue ofreciendo la lista de radios.

## 3. Notas de versión (0.1.12, Amazon)
```
• Radio: stations in your playlist show their logo, name and what's on air.
• Pause: press OK twice (or double-tap the screen). While paused, a single OK resumes.
• Player menu redesigned for touch screens.
```
```
• Radio: las emisoras de tu lista se ven con su logo, nombre y lo que está sonando.
• Pausa: pulsa OK dos veces (o toca dos veces la pantalla). En pausa, un solo OK reanuda.
• Menú del reproductor rediseñado en pantallas táctiles.
```

## 4. Instrucciones de prueba para el revisor
La lista `docs/playlists/review-test.m3u` trae tres vídeos de prueba de licencia libre (Big Buck Bunny
y Tears of Steel, de la Blender Foundation, CC BY; y la carta de ajuste HLS de Apple). **La app no la
ofrece**: solo figura aquí.

```
Easy Xtream Football is a media player only. It includes no channels, playlists or content, and
no account is needed: users add their own Xtream account or M3U playlist.

To see the player working without a provider, please use this test playlist, which contains only
openly licensed test videos (Blender Foundation's Big Buck Bunny and Tears of Steel, CC BY, and
an HLS test pattern). It is not offered inside the app.

1. Open the app. On first launch it shows "Connect your list".
2. Select the "M3U list" tab.
3. In "M3U playlist URL" enter:
   https://raw.githubusercontent.com/nezor11/easy-xtream-football/main/docs/playlists/review-test.m3u
4. Select "Save and enter". Three test streams appear: Big Buck Bunny, Tears of Steel and BipBop test pattern.
5. Select any of them to play. Back returns to the list.

Alternative with a single stream: choose the "Direct link" tab and enter
https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8
```

## 5. Antes de enviar (lista de comprobación)
- [x] **Probada en el Chromecast el 2026-10-01** (build debug `0.1.12-debug` con `-Pstore=amazon`, datos
      vacíos): en el primer arranque y al final de "Connect your list" **no** sale el botón de la lista
      de ejemplo; la lista de prueba carga y **Big Buck Bunny reproduce** (848x480, con audio).
- [x] **Capturas rehechas el 2026-10-01** en el Chromecast, en inglés: `01-channels.png` ("Live sports of
      My provider", canales inventados de `docs/playlists/screenshot-demo.m3u`, 5 favoritos) y
      `02-profiles.png` (perfiles "Backup list", "Home", "My provider", "Sports bar", "Test streams").
      Copias RGB sin canal alfa para Fire TV en `~/Downloads/easy-xtream-0.1.12-vc16-amazon/screenshots/`.
      **Falta subirlas** a Amazon (tablet y Fire TV) y, si se quiere, a Play.
- [x] Rama mezclada en `main` (la URL de la lista de prueba apunta a `main`).
- [ ] Respuesta de Amazon al caso `22369540491`, o decisión del usuario de enviar sin esperarla.
- [ ] En la consola: *Add Upcoming Version*, subir el APK universal vc16, DRM No, borrar el APK
      anterior, rehacer dispositivos (Fire TV 91 / tablets 12 / Automotive 0), párrafo, notas,
      instrucciones de arriba y las dos capturas nuevas.

Cómo se hicieron las capturas (por si hay que repetirlas): `adb shell cmd locale set-app-locales
com.footballxtream.debug --locales en-US`; perfiles M3U creados con el mando por adb (`input keyevent` +
`input text` **en trozos cortos**: una URL larga de golpe se corta); los nombres de los canales de la lista
empiezan cada uno por una palabra distinta porque la app agrupa en una tarjeta los que comparten la
primera palabra; favoritos con `input keyevent --longpress 23`.
