<div align="center">

# WA-Listener-Summarizer

### Resumen diario de notificaciones (WhatsApp)

![Android](https://img.shields.io/badge/Android-8%2B-brightgreen?style=for-the-badge) ![Kotlin](https://img.shields.io/badge/Kotlin-Android-blue?style=for-the-badge) ![Room](https://img.shields.io/badge/Room-Database-orange?style=for-the-badge)

</div>

---

## 📋 Descripción

WA-Listener-Summarizer es una aplicación Android que captura notificaciones (principalmente de WhatsApp), las guarda localmente y genera resúmenes diarios usando un servicio de chat/IA. Está diseñada para uso personal: los datos permanecen en tu dispositivo.

La intención principal es permitir revisar rápidamente el día en forma de resúmenes periódicos sin necesidad de abrir cada chat.

---

## ✨ Características principales

- Captura de notificaciones con `NotificationListenerService`.
- Agrupación por chat (`package|chatTitle`) y persistencia en Room (entidades `Chat`, `Message`, `DailySummary`).
- Heurísticas para evitar persistir mensajes vacíos, placeholders y duplicados.
- Generación de resúmenes diarios por chat mediante `SummaryGenerator` (llamadas a API de chat/completions).
- Botón en pantalla principal para generar un resumen agregado del día (todos los chats) y mostrarlo en un diálogo.
- Limpieza automática (una vez) de mensajes vacíos y duplicados históricos.

---

## 🛠️ Cómo funciona (visión técnica)

- `WhatsAppNotificationListener` escucha notificaciones y extrae título/texto.
- Se normalizan y filtran textos (eliminación de contadores como "(3 mensajes)").
- `Message` se guarda en Room si pasa las comprobaciones (no vacío, no placeholder, no duplicado reciente, etc.).
- `SummaryGenerator` construye un prompt con los mensajes del día y llama a la API para generar un resumen; existen funciones para resumen por chat y para resumen agregado que toma mensajes de todos los chats del día.
- UI: `MainActivity` muestra la lista de chats y permite generar el resumen agregado del día.

---

## ⚙️ Configuración: API Key y Modelo

En la versión actual el `SummaryGenerator` incluye variables locales `key` y `model` dentro de `generateDailySummary(...)` y `generateSummaryForChats(...)`.

Editar esas variables:

- Archivo: `app/src/main/java/com/example/whatsappsummary/util/SummaryGenerator.kt`
- Líneas de interés: dentro de `generateDailySummary` y `generateSummaryForChats` busca `val key =` y `val model =`.

Ejemplo:

- `val key = "sk-or-..."`
- `val model = "arcee-ai/trinity-large-preview:free"`

Reemplaza `key` por tu API key y `model` por el identificador del modelo que prefieras. Mantén estas claves privadas y evita subirlas a repositorios públicos.

Consejo: para mayor seguridad y flexibilidad, considera mover estas configuraciones a `SharedPreferences` o a un archivo de ajustes en la app (puedo implementarlo si lo deseas).

---

## 🚀 Instalación y ejecución

1. Clona el repositorio:

```bash
git clone https://github.com/tuusuario/WA-Listener-Summarizer.git
```

2. Abre el proyecto en Android Studio.
3. Conecta un dispositivo Android 8.0+ o usa un emulador con API >= 26.
4. Ejecuta la app y concede permiso de acceso a notificaciones cuando se solicite.

Pruebas rápidas:

- Envía notificaciones de WhatsApp al dispositivo para comprobar captura.
- Pulsa el botón central en la pantalla principal para generar el resumen agregado del día.

---

## 📁 Estructura del proyecto (resumen)

```
app/src/main/java/com/example/whatsappsummary/
├── service/                    # NotificationListenerService
│   └── WhatsAppNotificationListener.kt
├── data/                       # Room entities, DAOs, AppDatabase
├── repository/                 # WhatsAppRepository (capa de datos)
├── util/                       # SummaryGenerator.kt (IA prompts)
└── ui/                         # Activities, Adapters y layouts
```

---

## 🔒 Privacidad

Los mensajes y metadatos se almacenan localmente en la base de datos del dispositivo. La app no envía datos a servidores externos por defecto; la única comunicación externa posible es la llamada a la API de chat para generar resúmenes (si configuras una API key).

---

## 🧰 Tecnologías

- Kotlin (Android)
- Room Database
- ViewModel, LiveData (MVVM)
- Material Design, RecyclerView, ConstraintLayout

---

## ♻️ Mantenimiento y limpieza

- La base `AppDatabase` ejecuta una limpieza inicial (una vez) para eliminar placeholders y duplicados históricos.
- Se aplican varias defensas contra duplicados en `WhatsAppNotificationListener` y `WhatsAppRepository` (filtros por ventana temporal y comparación de últimos mensajes).

---

## 🤝 Contribuciones

Si deseas contribuir:

1. Haz fork del proyecto.
2. Crea una rama para tu feature (`git checkout -b feature/mi-feature`).
3. Envía un PR con descripción y pruebas si aplica.

Por favor documenta cualquier cambio que afecte la privacidad o persistencia de datos.

---

## 📄 Licencia

MIT — añade un archivo `LICENSE` si quieres dejarlo explícito.

---

## Autor

- MacWilliXD

---

¿Quieres que mueva la configuración de `key`/`model` a ajustes (`SharedPreferences`) y un panel simple en la UI para administrarlas? Puedo implementarlo.
