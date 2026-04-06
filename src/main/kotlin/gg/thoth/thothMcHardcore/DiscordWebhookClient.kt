package gg.thoth.thothMcHardcore

import org.bukkit.configuration.file.FileConfiguration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.floor

class DiscordWebhookClient(
    private val webhookUrl: String,
    private val serverName: String,
    private val serverUrl: String?,
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun sendChallengeStartEmbed(
        worldName: String,
        seed: Long,
        playerName: String,
        startedAtMillis: Long,
    ) {
        val fields = buildList {
            add(field("ワールド", worldName))
            add(field("シード値", "`$seed`"))
            add(field("開始時刻", formatStartedAt(startedAtMillis)))
            add(field("最初の参加者", playerName))
            if (!serverUrl.isNullOrBlank()) {
                add(field("サーバーURL", serverUrl))
            }
        }

        val payload = """
            {
              "username": "${escapeJson(serverName)} Hardcore Challenge",
              "embeds": [
                {
                  "title": "${escapeJson(serverName)} Hardcore Challenge 開始",
                  "description": "新しいハードコアチャレンジが始まりました。",
                  "color": 5763719,
                  "fields": [
                    ${fields.joinToString(",\n                    ")}
                  ],
                  "timestamp": "${Instant.ofEpochMilli(startedAtMillis)}"
                }
              ]
            }
        """.trimIndent()

        sendPayload(payload)
    }

    fun sendFailureEmbed(snapshot: RunStatsSnapshot) {
        val title = snapshot.deathMessage?.takeIf { it.isNotBlank() } ?: "失敗！"
        val payload = """
            {
              "username": "${escapeJson(serverName)} Hardcore Challenge",
              "embeds": [
                {
                  "title": "${escapeJson(serverName)} Hardcore Challenge 失敗",
                  "description": "${escapeJson(title)}",
                  "color": 15158332,
                  "fields": [
                    ${buildFields(snapshot).joinToString(",\n                    ")}
                  ],
                  "footer": {
                    "text": "ワールド: ${escapeJson(snapshot.worldName)}"
                  },
                  "timestamp": "${snapshot.finishedAtIso}"
                }
              ]
            }
        """.trimIndent()

        sendPayload(payload)
    }

    private fun sendPayload(payload: String) {
        val request = HttpRequest.newBuilder(URI.create(webhookUrl))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(10))
            .POST(HttpRequest.BodyPublishers.ofString(payload))
            .build()

        httpClient.send(request, HttpResponse.BodyHandlers.discarding())
    }

    private fun buildFields(snapshot: RunStatsSnapshot): List<String> = listOf(
        field("経過日数", formatDays(snapshot.gameDays)),
        field("延べプレイヤー日数", formatDays(snapshot.aggregatePlayerDays)),
        field("参加人数", "${snapshot.participantCount}人"),
        field("破壊したブロック", formatLong(snapshot.blocksBroken)),
        field("設置したブロック", formatLong(snapshot.blocksPlaced)),
        field("採掘した鉱石", formatLong(snapshot.oresBroken)),
        field("クラフト回数", formatLong(snapshot.craftingCount)),
        field("ネザー到達", formatMilestone(snapshot.netherReached, snapshot.netherReachedAfterDays)),
        field("エンド到達", formatMilestone(snapshot.endReached, snapshot.endReachedAfterDays)),
        field("受けたダメージ", formatHp(snapshot.totalDamageTaken)),
        field("瀕死回数", formatLong(snapshot.nearDeathCount)),
        field("敵対Mobキル", formatLong(snapshot.hostileKills)),
        field("友好/中立Mobキル", formatLong(snapshot.passiveNeutralKills)),
    )

    private fun field(name: String, value: String): String =
        """{"name":"${escapeJson(name)}","value":"${escapeJson(value)}","inline":true}"""

    private fun formatMilestone(reached: Boolean, days: Double?): String {
        if (!reached || days == null) {
            return "未到達"
        }
        return "到達 (${formatDays(days)})"
    }

    private fun formatHp(value: Double): String = "${formatDecimal(value)} HP"

    private fun formatDays(days: Double): String = "${formatDecimal(days)}日"

    private fun formatStartedAt(startedAtMillis: Long): String =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(startedAtMillis))

    private fun formatDecimal(value: Double): String = ((floor(value * 10.0 + 0.5)) / 10.0).toString()

    private fun formatLong(value: Long): String = "%,d".format(value)

    private fun escapeJson(value: String): String = buildString(value.length + 8) {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

    companion object {
        fun fromConfig(config: FileConfiguration): DiscordWebhookClient? {
            val webhookUrl = config.getString("discord.webhook-url").orEmpty().trim()
            if (webhookUrl.isEmpty()) {
                return null
            }

            val serverName = config.getString("discord.server-name").orEmpty().ifBlank { "Minecraft" }
            val serverUrl = config.getString("discord.server-url").orEmpty().trim().ifBlank { null }
            return DiscordWebhookClient(webhookUrl = webhookUrl, serverName = serverName, serverUrl = serverUrl)
        }
    }
}
