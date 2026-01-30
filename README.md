# WA-Listener-Summarizer

![Logo](https://img.shields.io/badge/Android-8%2B-brightgreen?style=flat-square) ![Kotlin](https://img.shields.io/badge/Kotlin-Android-blue?style=flat-square) ![Room](https://img.shields.io/badge/Room-Database-orange?style=flat-square)

**WA-Listener-Summarizer** es una app Android que escucha notificaciones (principalmente WhatsApp), las persiste localmente y genera resúmenes diarios usando un modelo de IA. Está pensada para uso personal y privado: todos los datos permanecen en el dispositivo.

---

## ✨ Características principales

- Escucha y almacena notificaciones (WhatsApp y otras apps opcionales).
- Lista de chats con contador de mensajes no leídos y vista de mensajes por chat.
- Generación de resúmenes diarios por chat (usando una API de chat/completions) y opción para resumen agregado del día.
- Filtrado por fechas y búsqueda de texto en mensajes y resúmenes.
- Limpieza automática de mensajes vacíos o repetidos y deduplicación en la captura.
- Todo se guarda localmente en Room Database (sin subir datos a servidores externos).

---

## Cómo funciona (resumen técnico)

- Un servicio `NotificationListenerService` captura notificaciones y extrae título/texto.
- Las notificaciones se normalizan y se agrupan por `chatId` (ej. `package|chatTitle`).
- `Message` y `Chat` se guardan en una base Room; hay heurísticas para evitar duplicados y mensajes vacíos.
- `SummaryGenerator` arma un prompt con los mensajes del día y llama a una API de chat para generar el resumen.
- La UI muestra chats y permite generar manualmente resúmenes; también se puede generar un resumen agregado para todos los chats del día.

---

## Configuración: API Key y modelo para `SummaryGenerator`

Por simplicidad en la versión actual, `SummaryGenerator` contiene variables locales donde se establece la API key y el modelo a usar. Para cambiar la clave o el modelo edita el archivo:

- `app/src/main/java/com/example/whatsappsummary/util/SummaryGenerator.kt`

Busca las líneas dentro de los métodos `generateDailySummary(...)` y `generateSummaryForChats(...)` donde se definen `key` y `model`. Por ejemplo:

- `val key = "sk-or-..."`
- `val model = "arcee-ai/trinity-large-preview:free"`

Reemplaza `key` por tu API key y `model` por el identificador del modelo que quieras usar. Opcionalmente puedes implementar la lectura desde `SharedPreferences` usando el método `loadApiKeysFromPrefs()` ya incluido.

IMPORTANTE: Mantén tus claves en privado y evita incluirlas en repositorios públicos.

---

## Instalación y ejecución

1. Clona el repositorio:

```bash
git clone https://github.com/tuusuario/WA-Listener-Summarizer.git
```

2. Abre el proyecto en Android Studio (Recomendado: Android Studio Arctic Fox o superior).
3. Conecta un dispositivo Android 8.0+ o usa un emulador con API >= 26.
4. Ejecuta la app; concede permiso de acceso a notificaciones cuando se solicite.

Pruebas rápidas:

- Envía notificaciones de WhatsApp al dispositivo y verifica que aparecen en la lista.
- Pulsa el botón central en la pantalla principal para generar el resumen general del día.

---

## Desarrollo y estructura del código

- `service/WhatsAppNotificationListener.kt`: captura y normaliza notificaciones.
- `data/`: entidades Room (`Chat`, `Message`, `DailySummary`), DAOs y `AppDatabase`.
- `repository/WhatsAppRepository.kt`: capa de acceso a datos.
- `util/SummaryGenerator.kt`: construye prompt y llama a la API para generar resúmenes.
- `ui/`: Activities, Adapters y layouts para la interfaz.

Si contribuyes, procura seguir el estilo Kotlin/Android del proyecto y añade tests si haces cambios relevantes.

---

## Privacidad

La app guarda todo localmente y no envía mensajes ni metadatos a servidores externos (a menos que configures una API key para el generador de resúmenes, que sí se usa para llamadas desde el dispositivo a la API que elijas).

---

## Contribuciones

Pull requests y issues son bienvenidos. Si vas a contribuir con cambios que afecten la privacidad o el manejo de datos, por favor documenta el comportamiento en la descripción del PR.

---

## Licencia

MIT License — consulta el archivo `LICENSE` si lo agregas al repo.

---

## Autor

- [MacWilliXD](https://github.com/MacWilliXD)

Si quieres, puedo también actualizar `SummaryGenerator` para leer la clave y el modelo desde ajustes de la app en vez de variables hardcodeadas. ¿Deseas que lo haga?
