<div align="center">

# Notirizer

### Dashboard inteligente de notificaciones con resúmenes IA

![Android](https://img.shields.io/badge/Android-8%2B-brightgreen?style=for-the-badge) ![Kotlin](https://img.shields.io/badge/Kotlin-Android-blue?style=for-the-badge) ![Room](https://img.shields.io/badge/Room-Database-orange?style=for-the-badge) ![Charts](https://img.shields.io/badge/MPAndroidChart-3.0.3-blue?style=for-the-badge)

</div>

---

## 📋 Descripción

Notirizer es una aplicación Android completa que captura **TODAS las notificaciones del dispositivo** (WhatsApp, Telegram, Gmail, redes sociales, etc.), las guarda localmente y proporciona un **dashboard interactivo** con gráficas y estadísticas detalladas. Incluye generación de resúmenes diarios usando IA para mantenerte informado sin abrir cada aplicación.

La app es completamente privada: los datos permanecen en tu dispositivo y solo se comunican con la API de IA que configures para generar resúmenes.

**Ideal para:** revisar rápidamente tu día, obtener insights visuales de tu actividad digital, filtrar notificaciones por aplicación y período, mantener un registro organizado de notificaciones importantes.

---

## ✨ Características principales

### 📊 Dashboard Interactivo
- **Gráfica de Pastel (Donut)**: Visualización de notificaciones por aplicación con filtros de temporalidad (Hoy, Última semana, Último mes, Últimos 3 meses, Último año)
- **Gráfica de Líneas**: Tendencia temporal de notificaciones con filtros por aplicación específica
- **Estadísticas en tiempo real**: Total de notificaciones, aplicaciones emisoras, notificaciones del día
- **Lista de aplicaciones**: Navegación directa a chats filtrados por aplicación

### 🎯 Captura y Procesamiento
- Captura de notificaciones con `NotificationListenerService` para todas las apps del dispositivo
- Agrupación inteligente por chat (`package|chatTitle`) y persistencia en Room
- Heurísticas avanzadas para evitar persistir mensajes vacíos, placeholders y duplicados
- Filtros temporales y por aplicación emisora

### 🤖 Resúmenes con IA
- Generación de resúmenes diarios por chat mediante `SummaryGenerator` (API de chat/completions)
- Resumen general del día con opción de filtrar por chats visibles
- Configuración personalizable de longitud, detalle y prompts adicionales
- Soporte para múltiples modelos de IA

### 🔍 Navegación y Filtros
- Pantalla principal con lista de chats filtrable por fecha, texto y aplicación
- Navegación desde dashboard a chats específicos
- Filtros persistentes entre sesiones
- Búsqueda en tiempo real

---

## 🛠️ Cómo funciona (visión técnica)

### Arquitectura
- **MVVM Pattern**: ViewModels, LiveData y DataBinding para UI reactiva
- **Room Database**: Persistencia local con entidades `Notification`, `Chat`, `Message`, `DailySummary`
- **NotificationCaptureService**: Servicio en background que escucha todas las notificaciones
- **Dashboard**: Visualización de estadísticas con MPAndroidChart

### Flujo de Datos
1. `NotificationCaptureService` captura notificaciones del sistema
2. Normalización y filtrado de textos (eliminación de contadores, vacíos, duplicados)
3. Persistencia en Room usando entidad `Notification` como fuente principal
4. `DashboardViewModel` procesa datos para gráficas y estadísticas
5. `SummaryGenerator` construye prompts con notificaciones filtradas y llama a API de IA
6. UI muestra datos en tiempo real con navegación interactiva

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

**Próximamente**: Configuración desde la UI con SharedPreferences para mayor seguridad.

---

## 🚀 Instalación y ejecución

1. Clona el repositorio:

```bash
git clone https://github.com/tuusuario/Notirizer.git
```

2. Abre el proyecto en Android Studio.
3. Conecta un dispositivo Android 8.0+ o usa un emulador con API >= 26.
4. Ejecuta la app y concede permiso de acceso a notificaciones cuando se solicite.

### Pruebas rápidas:

- **Dashboard**: Abre la app para ver estadísticas y gráficas automáticamente
- **Gráficas**: Toca secciones del pastel para ver nombres de aplicaciones
- **Filtros**: Usa los spinners para filtrar por tiempo y aplicación
- **Navegación**: Toca aplicaciones en la lista para ver chats filtrados
- **Resúmenes**: Pulsa el botón de resumen para generar análisis con IA

---

## � Prueba rápida con APK

Si quieres probar la app sin configurar Android Studio, puedes descargar el APK de debug desde el directorio del proyecto:

1. Ve al directorio `app/build/outputs/apk/debug/`
2. Descarga el archivo [app-debug.apk](app/build/outputs/apk/debug/app-debug.apk)
3. Transfiere el APK a tu dispositivo Android
4. En tu dispositivo, ve a Ajustes > Seguridad > Instalar apps de fuentes desconocidas (habilita para tu navegador o administrador de archivos)
5. Abre el APK descargado e instala

**Advertencias importantes:**
- Este es un APK de debug sin firmar, ideal solo para pruebas personales
- Los antivirus pueden marcarlo como sospechoso (falso positivo común para APKs sin firma)
- Si ves avisos de virus, ignóralos ya que es código fuente abierto y construido localmente
- Recomendamos usar un dispositivo de prueba o emulador para evitar riesgos
- Para producción, genera un APK firmado desde Android Studio

---

## �📁 Estructura del proyecto (actualizada)

```
app/src/main/java/com/example/whatsappsummary/
├── service/                    # NotificationCaptureService (captura todas las notificaciones)
│   └── NotificationCaptureService.kt
├── data/                       # Room entities, DAOs, AppDatabase
│   ├── entity/                 # Notification, Chat, Message, DailySummary
│   └── dao/                    # NotificationDao, ChatDao, MessageDao, DailySummaryDao
├── repository/                 # NotificationRepository (capa de datos unificada)
├── util/                       # SummaryGenerator.kt (resúmenes con IA)
├── viewmodel/                  # DashboardViewModel, ChatListViewModel (MVVM)
├── ui/                         # Activities, Adapters
│   ├── MainActivity.kt         # Lista de chats con filtros
│   ├── DashboardActivity.kt    # Dashboard con gráficas y estadísticas
│   ├── ChatDetailActivity.kt   # Detalle de chat individual
│   └── adapter/                # AppStatsAdapter, ChatListAdapter
└── res/layout/                 # activity_dashboard.xml, activity_main.xml, etc.
```

---

## 🔒 Privacidad

Los mensajes y metadatos se almacenan localmente en la base de datos del dispositivo. La app no envía datos a servidores externos por defecto; la única comunicación externa posible es la llamada a la API de chat para generar resúmenes (si configuras una API key).

**Características de privacidad:**
- ✅ Datos locales únicamente
- ✅ Sin trackers ni analytics
- ✅ Comunicación externa solo para resúmenes IA (opcional)
- ✅ Filtros aplicados localmente

---

## 🧰 Tecnologías

- **Kotlin** (Android moderno)
- **Room Database** (Persistencia local)
- **MPAndroidChart** (Gráficas interactivas)
- **ViewModel + LiveData** (MVVM architecture)
- **Material Design 3** (UI moderna)
- **RecyclerView + ConstraintLayout** (Listas eficientes)
- **Coroutines** (Programación asíncrona)
- **NotificationListenerService** (Captura de notificaciones)

---

## ♻️ Mantenimiento y limpieza

- La base `AppDatabase` ejecuta una limpieza inicial (una vez) para eliminar placeholders y duplicados históricos
- Se aplican varias defensas contra duplicados en `NotificationCaptureService` y `NotificationRepository`
- Filtros por ventana temporal y comparación de contenido para evitar duplicados
- Limpieza automática de mensajes vacíos y placeholders

---

## 📈 Funcionalidades del Dashboard

### Gráfica de Pastel (Donut)
- Muestra distribución de notificaciones por aplicación
- Formato: "cantidad (porcentaje%)" ej: "150 (45%)"
- Filtros temporales independientes
- Interacción táctil: tocar muestra nombre completo
- Top 10 aplicaciones más activas

### Gráfica de Líneas
- Tendencia temporal de notificaciones
- Filtros por aplicación específica o "Todas"
- Rangos: semana, mes, 3 meses, año
- Eje X: fechas formateadas
- Eje Y: cantidad de notificaciones

### Estadísticas Principales
- Total de notificaciones capturadas
- Número de aplicaciones emisoras
- Notificaciones del día actual
- Actualización automática en tiempo real

### Lista de Aplicaciones
- Apps ordenadas por cantidad de notificaciones
- Navegación directa a chats filtrados
- Información de última notificación
- Scroll infinito con RecyclerView

---

## 🤝 Contribuciones

Si deseas contribuir:

1. Haz fork del proyecto
2. Crea una rama para tu feature (`git checkout -b feature/mi-feature`)
3. Envía un PR con descripción y pruebas si aplica

**Áreas de mejora sugeridas:**
- Configuración de API desde UI
- Más tipos de gráficas
- Exportación de datos
- Temas oscuros/claros
- Notificaciones push personalizadas

Por favor documenta cualquier cambio que afecte la privacidad o persistencia de datos.

---

## 📄 Licencia

MIT — añade un archivo `LICENSE` si quieres dejarlo explícito.

---

## Autor

- MacWilliXD

---

**¿Preguntas?** Abre un issue en GitHub. ¿Quieres nuevas funcionalidades? También por issues.
