# DigiTicket para Android

Beta para comerciantes que quieren llevar un registro de sus ventas y gastos en pesos mexicanos. Toma una foto del comprobante o captura el importe a mano. Revisa los datos antes de confirmar el movimiento y exporta el mes a Excel.

## Probar la beta

**[Descargar DigiTicket 0.1.0-beta.1 para Android](https://github.com/atzin-adrian/DigiTicket-Descargas/releases/download/v0.1.0-beta.1/DigiTicket-0.1.0-beta.1.apk)** · Android 8 o posterior · 9.6 MB.

Abre el enlace desde el teléfono, toca el archivo descargado y selecciona **Instalar**. Si Android te pide permiso para instalar desde tu navegador, puedes concederlo para esta instalación y retirarlo después. El paquete de la beta es `mx.digiticket.app.beta`. Encontrarás la suma SHA-256 y el historial de instaladores en [Versiones](https://github.com/atzin-adrian/DigiTicket-Descargas/releases).

## Primera prueba

1. Registra una venta por $125.50 y un gasto por $37.90 en el mismo mes.
2. Fotografía un ticket de ejemplo y guárdalo como pendiente. En Inicio el resultado estimado debe seguir en $87.60.
3. Revisa la foto, corrige el total y confirma el movimiento. Comprueba el cambio en Inicio y búscalo en Historial.
4. En Excel elige el mes y guarda o comparte el archivo. Compara la hoja Resumen con la pantalla de Inicio. Movimientos identifica el estado de cada registro y Productos muestra las partidas que capturaste.

Para comentar un problema, completa el [reporte de prueba](https://github.com/atzin-adrian/DigiTicket-Descargas/issues/new?template=prueba-beta.yml). Indica el modelo del teléfono, su versión de Android y el paso en que ocurrió. Usa datos inventados si necesitas ilustrarlo.

## Datos y lectura de tickets

Los movimientos, fotos y lecturas se guardan en el teléfono. El reconocimiento usa Google ML Kit en el dispositivo; el modelo puede necesitar una primera descarga. La cámara del sistema, la galería y la captura manual también están disponibles. Las sugerencias de lectura, especialmente de notas manuscritas, requieren revisión. Puedes corregir fechas, montos y partidas, guardar un pendiente o eliminar un movimiento y su comprobante.

El resultado mensual suma las ventas confirmadas y resta los gastos confirmados. Las aportaciones, retiros y operaciones de capital se muestran por separado en el historial. El resultado es una estimación para llevar el negocio.

Consulta cómo se tratan las fotos, los datos técnicos y las exportaciones en [Privacidad](PRIVACIDAD.md).

## Desarrollo

Abre `android/` en Android Studio. El proyecto usa Kotlin, Jetpack Compose, SQLite y el SDK de Android 37. El wrapper incluye la suma de verificación de Gradle. Desde ese directorio ejecuta:

```sh
./gradlew testDebugUnitTest assembleDebug assemblePilot
./gradlew connectedDebugAndroidTest
```

La última tarea necesita un emulador o dispositivo. Para el piloto se firma el APK de `app/build/outputs/apk/pilot/` con la clave de distribución mantenida fuera del repositorio. Una actualización de la beta debe conservar el identificador de paquete y el certificado de firma. Los datos se guardan por instalación. Antes de desinstalar, exporta los meses que quieras conservar.
