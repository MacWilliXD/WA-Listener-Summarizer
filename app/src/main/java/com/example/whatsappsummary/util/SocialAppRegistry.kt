package com.example.whatsappsummary.util

/**
 * Apps que se agrupan por chat/conversación individual. El resto se colapsa en una
 * única entrada por aplicación (chatId = "app:<packageName>"), ya que sus notificaciones
 * suelen ser avisos transaccionales donde no tiene sentido separar por hilo.
 */
object SocialAppRegistry {

    val SOCIAL_PACKAGES: Set<String> = setOf(
        // WhatsApp
        "com.whatsapp",
        "com.whatsapp.w4b",
        // Telegram
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thunderdog.challegram",
        // Messenger / Facebook
        "com.facebook.orca",
        "com.facebook.mlite",
        // Signal
        "org.thoughtcrime.securesms",
        "org.smssecure.smssecure",
        // Discord
        "com.discord",
        // Instagram (DMs)
        "com.instagram.android",
        "com.instagram.threadsapp",
        // Snapchat
        "com.snapchat.android",
        // Viber
        "com.viber.voip",
        // Line
        "jp.naver.line.android",
        // KakaoTalk
        "com.kakao.talk",
        // WeChat
        "com.tencent.mm",
        // SMS / Google Messages
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        // Teams / Slack (chat)
        "com.microsoft.teams",
        "com.Slack",
        // Skype
        "com.skype.raider",
        // Twitter/X DMs
        "com.twitter.android"
    )

    fun isSocial(packageName: String): Boolean = packageName in SOCIAL_PACKAGES

    /**
     * ChatId para apps no-sociales: se colapsan por paquete.
     */
    fun appBucketChatId(packageName: String): String = "app:$packageName"
}
