package gg.thoth.thothMcHardcore

import org.bukkit.GameMode
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.RenderType
import org.bukkit.scoreboard.Scoreboard
import kotlin.math.ceil

class HealthSidebar(private val plugin: JavaPlugin) {

    private val scoreboard: Scoreboard =
        requireNotNull(plugin.server.scoreboardManager) { "Scoreboard manager is not available." }.mainScoreboard
    private var objective: Objective? = null
    private var task: BukkitTask? = null
    private val renderedEntries = mutableSetOf<String>()

    fun start() {
        task?.cancel()
        ensureObjective()
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            render()
        }, 0L, 10L)
    }

    fun stop() {
        task?.cancel()
        task = null
        clearRenderedEntries()
        objective?.unregister()
        objective = null
    }

    private fun render() {
        val sidebar = ensureObjective()
        clearRenderedEntries()

        activePlayers()
            .sortedWith(compareByDescending<Player> { it.health }.thenBy { it.name.lowercase() })
            .take(MAX_LINES)
            .forEach { player ->
                val entry = formatLine(player)
                val score = sidebar.getScore(entry)
                score.score = ceil(player.health.coerceAtLeast(0.0)).toInt()
                renderedEntries += entry
            }
    }

    private fun clearRenderedEntries() {
        renderedEntries.forEach(scoreboard::resetScores)
        renderedEntries.clear()
    }

    private fun ensureObjective(): Objective {
        objective?.let { current ->
            return current
        }

        scoreboard.getObjective(OBJECTIVE_NAME)?.unregister()
        return scoreboard.registerNewObjective(
            OBJECTIVE_NAME,
            Criteria.DUMMY,
            TITLE,
            RenderType.INTEGER
        ).apply {
            displaySlot = DisplaySlot.SIDEBAR
            objective = this
        }
    }

    private fun activePlayers(): List<Player> =
        plugin.server.onlinePlayers.filter { player ->
            player.gameMode == GameMode.SURVIVAL || player.gameMode == GameMode.ADVENTURE
        }

    private fun formatLine(player: Player): String {
        val teamColor = scoreboard.getEntryTeam(player.name)?.color ?: ChatColor.WHITE
        return "$teamColor${player.name}"
    }

    private companion object {
        const val OBJECTIVE_NAME = "thoth_health"
        const val TITLE = "Health"
        const val MAX_LINES = 15
    }
}
