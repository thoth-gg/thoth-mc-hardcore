package gg.thoth.thothMcHardcore

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import kotlin.random.Random

class ColorTeams(private val plugin: JavaPlugin) {

    private val scoreboard: Scoreboard =
        requireNotNull(plugin.server.scoreboardManager) { "Scoreboard manager is not available." }.mainScoreboard

    fun ensureExists() {
        COLOR_TEAMS.forEach { (teamName, color) ->
            val team = scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)
            team.color = color
        }
    }

    fun assignIfMissing(player: Player) {
        if (scoreboard.getEntryTeam(player.name) != null) {
            return
        }

        val team = leastPopulatedTeams().random(Random.Default)
        team.addEntry(player.name)
    }

    private fun leastPopulatedTeams(): List<Team> {
        val teams = COLOR_TEAMS.mapNotNull { (teamName, _) -> scoreboard.getTeam(teamName) }
        val minimumSize = teams.minOfOrNull { it.entries.size } ?: 0
        return teams.filter { it.entries.size == minimumSize }
    }

    private companion object {
        val COLOR_TEAMS = listOf(
            "dark_blue" to ChatColor.DARK_BLUE,
            "dark_green" to ChatColor.DARK_GREEN,
            "dark_aqua" to ChatColor.DARK_AQUA,
            "dark_red" to ChatColor.DARK_RED,
            "dark_purple" to ChatColor.DARK_PURPLE,
            "gold" to ChatColor.GOLD,
            "gray" to ChatColor.GRAY,
            "dark_gray" to ChatColor.DARK_GRAY,
            "blue" to ChatColor.BLUE,
            "green" to ChatColor.GREEN,
            "aqua" to ChatColor.AQUA,
            "red" to ChatColor.RED,
            "light_purple" to ChatColor.LIGHT_PURPLE,
            "yellow" to ChatColor.YELLOW,
            "white" to ChatColor.WHITE,
        )
    }
}
