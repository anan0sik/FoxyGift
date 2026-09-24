package com.foxygift.pos.core.security

import com.foxygift.pos.data.repository.ProvisionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends background Telegram Bot API alerts on security events and shift reports.
 *
 * Configured from provisioning or settings (botToken + chatId / channel).
 */
@Singleton
class TelegramAlarmClient @Inject constructor(
    private val provisionRepo: ProvisionRepository,
) {

    private var botToken: String = ""
    private var chatId:   String = ""

    companion object {
        fun sanitizeToken(raw: String): String =
            raw.filterNot { it.isWhitespace() }.removePrefix("bot").trim()

        fun sanitizeChatId(raw: String): String {
            var chat = raw.trim().removeSurrounding("\"").removeSurrounding("'").trim()
            if (chat.startsWith("https://t.me/")) {
                chat = "@" + chat.removePrefix("https://t.me/").trim('/')
            } else if (chat.startsWith("t.me/")) {
                chat = "@" + chat.removePrefix("t.me/").trim('/')
            }
            if (!chat.startsWith("@") && !chat.startsWith("-") && chat.toLongOrNull() == null) {
                chat = "@$chat"
            }
            if (chat.startsWith("100") && chat.length >= 13 && chat.toLongOrNull() != null) {
                chat = "-$chat"
            }
            return chat
        }
    }

    fun configure(token: String, chat: String) {
        val cleanToken = sanitizeToken(token)
        val cleanChat  = sanitizeChatId(chat)
        botToken = cleanToken
        chatId   = cleanChat
        provisionRepo.saveTelegramConfig(cleanToken, cleanChat)
    }

    private fun resolveToken(): String =
        sanitizeToken(botToken.ifBlank { provisionRepo.getTelegramToken() })

    private fun resolveChatId(): String =
        sanitizeChatId(chatId.ifBlank { provisionRepo.getTelegramChatId() })

    fun isConfigured(): Boolean = resolveToken().isNotBlank() && resolveChatId().isNotBlank()

    suspend fun sendLockoutAlert(terminalId: String, locationName: String, attemptCount: Int): Result<Unit> {
        val text = "🚨 FOXYGIFT ALARM: $attemptCount consecutive failed PIN attempts on " +
                   "[$locationName / $terminalId]! Terminal temporarily locked."
        return sendMessage(text, silent = true)
    }

    suspend fun sendPanicAlert(terminalId: String, locationName: String, attemptCount: Int): Result<Unit> {
        val text = "🔴 FOXYGIFT PANIC: Terminal PERMANENTLY LOCKED after $attemptCount failed PIN attempts! " +
                   "Location: [$locationName] Terminal: [$terminalId]. " +
                   "Re-provisioning QR required to unlock."
        return sendMessage(text, silent = false)
    }

    suspend fun sendZReportSummary(
        terminalId:     String,
        locationName:   String,
        shiftDate:      String,
        issued:         String,
        redeemed:       String,
        txCount:        Int,
        isClosingShift: Boolean = false,
    ): Result<Unit> {
        val title = if (isClosingShift) "🔔 FOXYGIFT — СМЕНА ЗАКРЫТА (Z-ОТЧЕТ)" else "📊 FOXYGIFT Z-REPORT"
        val text = "$title\n" +
                   "📍 $locationName · Терминал: $terminalId\n" +
                   "📅 Дата смены: $shiftDate\n" +
                   "💳 Выдано карт: $issued\n" +
                   "💰 Оплата / списано: $redeemed\n" +
                   "🔢 Всего операций: $txCount\n" +
                   (if (isClosingShift) "🔒 Статус: Смена завершена" else "ℹ Статус: Промежуточный отчет")
        return sendMessage(text, silent = false)
    }

    suspend fun testConnection(token: String, chat: String): Result<Unit> {
        val cleanToken = sanitizeToken(token)
        val cleanChat  = sanitizeChatId(chat)
        val originalToken = botToken
        val originalChat  = chatId
        botToken = cleanToken
        chatId   = cleanChat

        val res = sendMessage(
            "✅ Тестовое сообщение от терминала FoxyGift POS. Связь с Telegram установлена успешно!",
            silent = false
        )
        if (res.isFailure) {
            botToken = originalToken
            chatId   = originalChat
        } else {
            provisionRepo.saveTelegramConfig(cleanToken, cleanChat)
        }
        return res
    }

    private suspend fun sendMessage(text: String, silent: Boolean = true): Result<Unit> = withContext(Dispatchers.IO) {
        val token = resolveToken()
        val chat  = resolveChatId()
        if (token.isBlank() || chat.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Telegram Bot Token or Chat ID is not configured"))
        }

        runCatching {
            val url = "https://api.telegram.org/bot$token/sendMessage"
            val json = JSONObject().apply {
                val numericId = chat.toLongOrNull()
                if (numericId != null) {
                    put("chat_id", numericId)
                } else {
                    put("chat_id", chat)
                }
                put("text", text)
                put("disable_notification", silent)
            }
            val body = json.toString().toByteArray(Charsets.UTF_8)

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.setFixedLengthStreamingMode(body.size)
            conn.doOutput       = true
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            conn.outputStream.use { it.write(body) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val rawErr = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "HTTP $code"
                conn.disconnect()

                val parsedDesc = runCatching {
                    JSONObject(rawErr).optString("description")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: rawErr

                val friendlyExplanation = when {
                    parsedDesc.contains("chat not found", ignoreCase = true) ->
                        "Канал или чат не найден ($chat). Проверьте ID канала (начинается с -100...) или username (@канал). Бот должен быть добавлен в канал!"
                    parsedDesc.contains("have no rights", ignoreCase = true) ||
                    parsedDesc.contains("not enough rights", ignoreCase = true) ||
                    parsedDesc.contains("bot is not a member", ignoreCase = true) ||
                    parsedDesc.contains("bot was kicked", ignoreCase = true) ->
                        "У бота нет прав на публикацию в канале (403 Forbidden)! Зайдите в настройки канала Telegram -> Администраторы -> Добавить администратора -> выберите вашего бота и включите право 'Публикация сообщений' (Post Messages)."
                    parsedDesc.contains("bot was blocked", ignoreCase = true) ->
                        "Бот заблокирован пользователем или не запущен (403 Forbidden)! Откройте чат с ботом в Telegram и нажмите кнопку 'Запустить' (/start)."
                    code == 403 ->
                        "Доступ запрещен (403 Forbidden: $parsedDesc)! В канале: назначьте бота Администратором с правом публикации. В личном чате: напишите боту /start."
                    parsedDesc.contains("Unauthorized", ignoreCase = true) ->
                        "Неверный токен Telegram бота. Проверьте токен от @BotFather."
                    else -> parsedDesc
                }
                throw IOException("Telegram ($code): $friendlyExplanation")
            }
            conn.disconnect()
        }
    }

    /**
     * Sends a document (e.g. Transactions CSV) to the Telegram channel or chat via multipart/form-data.
     */
    suspend fun sendDocument(
        file: java.io.File,
        caption: String = "",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val token = resolveToken()
        val chat  = resolveChatId()
        if (token.isBlank() || chat.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Telegram Bot Token or Chat ID is not configured"))
        }

        if (!file.exists() || file.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("File does not exist or is empty: ${file.absolutePath}"))
        }

        runCatching {
            val boundary = "===FoxyGiftBoundary" + System.currentTimeMillis() + "==="
            val lineFeed = "\r\n"
            val url = "https://api.telegram.org/bot$token/sendDocument"

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput       = true
            conn.connectTimeout = 15_000
            conn.readTimeout    = 30_000

            conn.outputStream.use { os ->
                val writer = os.bufferedWriter(Charsets.UTF_8)

                // 1. chat_id field
                writer.write("--$boundary$lineFeed")
                writer.write("Content-Disposition: form-data; name=\"chat_id\"$lineFeed$lineFeed")
                writer.write(chat)
                writer.write(lineFeed)

                // 2. caption field
                if (caption.isNotBlank()) {
                    writer.write("--$boundary$lineFeed")
                    writer.write("Content-Disposition: form-data; name=\"caption\"$lineFeed$lineFeed")
                    writer.write(caption)
                    writer.write(lineFeed)
                }

                // 3. document file field
                writer.write("--$boundary$lineFeed")
                writer.write("Content-Disposition: form-data; name=\"document\"; filename=\"${file.name}\"$lineFeed")
                writer.write("Content-Type: text/csv; charset=UTF-8$lineFeed$lineFeed")
                writer.flush()

                // Stream file contents directly to connection output stream
                file.inputStream().use { fis ->
                    fis.copyTo(os)
                }
                os.flush()

                writer.write(lineFeed)
                writer.write("--$boundary--$lineFeed")
                writer.flush()
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val rawErr = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "HTTP $code"
                conn.disconnect()

                val parsedDesc = runCatching {
                    JSONObject(rawErr).optString("description")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: rawErr

                val friendlyExplanation = when {
                    parsedDesc.contains("chat not found", ignoreCase = true) ->
                        "Канал или чат не найден ($chat). Бот должен быть добавлен в канал!"
                    parsedDesc.contains("have no rights", ignoreCase = true) ||
                    parsedDesc.contains("not enough rights", ignoreCase = true) ||
                    parsedDesc.contains("bot is not a member", ignoreCase = true) ||
                    parsedDesc.contains("bot was kicked", ignoreCase = true) ->
                        "У бота нет прав на отправку файлов в канале (403 Forbidden)! Назначьте бота Администратором канала с правом публикации сообщений."
                    code == 403 ->
                        "Доступ запрещен (403 Forbidden)! Назначьте бота Администратором канала с правом публикации сообщений."
                    else -> parsedDesc
                }
                throw IOException("Telegram sendDocument ($code): $friendlyExplanation")
            }
            conn.disconnect()
        }
    }
}
