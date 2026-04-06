package gg.thoth.thothMcHardcore

import org.bukkit.ChatColor
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Scoreboard
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.`object`.ObjectContents
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.megavex.scoreboardlibrary.api.ScoreboardLibrary
import net.megavex.scoreboardlibrary.api.exception.NoPacketAdapterAvailableException
import net.megavex.scoreboardlibrary.api.sidebar.Sidebar
import kotlin.math.ceil

class HealthSidebar(private val plugin: JavaPlugin) {

    private val scoreboard: Scoreboard =
        requireNotNull(plugin.server.scoreboardManager) { "Scoreboard manager is not available." }.mainScoreboard
    private val legacySerializer = LegacyComponentSerializer.legacySection()
    private var scoreboardLibrary: ScoreboardLibrary? = null
    private var sidebar: Sidebar? = null
    private var task: BukkitTask? = null

    fun start() {
        task?.cancel()
        if (!ensureSidebar()) {
            return
        }
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            render()
        }, 0L, 10L)
    }

    fun stop() {
        task?.cancel()
        task = null
        sidebar?.close()
        sidebar = null
        scoreboardLibrary?.close()
        scoreboardLibrary = null
    }

    private fun render() {
        val currentSidebar = sidebar ?: return

        syncViewers(currentSidebar)
        currentSidebar.title(Component.text(TITLE, NamedTextColor.RED))

        val entries = activePlayers()
            .sortedWith(compareBy<Player> { it.health }.thenBy { it.name.lowercase() })
            .take(MAX_LINES)
            .map(::formatLine)

        for (line in 0 until MAX_LINES) {
            currentSidebar.line(line, entries.getOrNull(line))
        }
    }

    private fun ensureSidebar(): Boolean {
        sidebar?.let { return true }

        val library = scoreboardLibrary ?: run {
            try {
                ScoreboardLibrary.loadScoreboardLibrary(plugin)
            } catch (_: NoPacketAdapterAvailableException) {
                plugin.logger.warning("Health sidebar could not start because scoreboard-library did not find a compatible packet adapter.")
                return false
            }
        }.also {
            scoreboardLibrary = it
        }

        sidebar = library.createSidebar()
        return true
    }

    private fun activePlayers(): List<Player> =
        plugin.server.onlinePlayers.filter { player ->
            player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE
        }

    private fun syncViewers(sidebar: Sidebar) {
        val onlinePlayers = plugin.server.onlinePlayers.toSet()
        onlinePlayers.forEach(sidebar::addPlayer)
        sidebar.players()
            .filterNot(onlinePlayers::contains)
            .forEach(sidebar::removePlayer)
    }

    private fun formatLine(player: Player): Component {
        val health = ceil(player.health.coerceAtLeast(0.0)).toInt()
        val foodLevel = player.foodLevel
        val teamColor = scoreboard.getEntryTeam(player.name)?.color ?: ChatColor.WHITE
        val nameComponent = legacySerializer.deserialize("$teamColor${player.name}")
        val headComponent = Component.`object`(ObjectContents.playerHead(player.uniqueId))
        val heartComponent = Component.`object`(ObjectContents.sprite(HEART_ATLAS, HEART_SPRITE))
        val healthComponent = Component.text("$health", NamedTextColor.RED)
        val foodComponent = Component.`object`(ObjectContents.sprite(FOOD_ATLAS, FOOD_SPRITE))
        val foodLevelComponent = Component.text("$foodLevel", NamedTextColor.GOLD)

        return Component.textOfChildren(
            headComponent,
            Component.space(),
            nameComponent,
            Component.space(),
            heartComponent,
            Component.space(),
            healthComponent,
            Component.space(),
            foodComponent,
            Component.space(),
            foodLevelComponent,
        )
    }

    private companion object {
        const val TITLE = "Health"
        const val MAX_LINES = 15
        val HEART_ATLAS: Key = Key.key("minecraft:gui")
        val HEART_SPRITE: Key = Key.key("hud/heart/full")
        val FOOD_ATLAS: Key = Key.key("minecraft:gui")
        val FOOD_SPRITE: Key = Key.key("hud/food_full")
    }
}
