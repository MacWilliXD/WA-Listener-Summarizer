package com.example.whatsappsummary.config

/**
 * Configuración centralizada del cliente de IA (OpenRouter).
 *
 * Toda la conexión a la API vive aquí: si cambias de proveedor, de modelo o
 * necesitas rotar la API key, solo editas este archivo.
 *
 * NOTA DE SEGURIDAD: la API key viaja en el APK. Para un build de producción
 * real conviene moverla a BuildConfig (gradle) con variantes distintas para
 * debug/release, o leerla desde un backend propio. Para uso personal y debug
 * esta forma es aceptable.
 */
object AiConfig {

    /** Endpoint de chat completions. */
    const val URL_BASE: String = "https://openrouter.ai/api/v1/chat/completions"

    /** API key del proveedor (OpenRouter). */
    const val API_KEY: String = "API KEY"

    /** Modelo por defecto para los resúmenes. */
    const val MODEL: String = "openai/gpt-oss-120b:free"

    /** Nombre de la app que se envía a OpenRouter como X-Title (dashboard). */
    const val CLIENT_TITLE: String = "Notirizer"

    /** Timeouts HTTP en milisegundos. */
    const val CONNECT_TIMEOUT_MS: Int = 15_000
    const val READ_TIMEOUT_MS: Int = 45_000
}
