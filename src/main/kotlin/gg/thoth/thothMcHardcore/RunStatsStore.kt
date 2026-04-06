package gg.thoth.thothMcHardcore

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.UUID

class RunStatsStore(private val file: File) {

    data class LoadResult(
        val stats: RunStats,
        val created: Boolean,
    )

    fun loadOrCreate(currentWorldName: String): LoadResult {
        if (!file.exists()) {
            return LoadResult(createNewStats(currentWorldName), true)
        }

        val config = YamlConfiguration.loadConfiguration(file)
        val worldUid = config.getString("world.uid")
        val worldName = config.getString("world.name")
        val world = worldUid?.let { uid ->
            runCatching { UUID.fromString(uid) }.getOrNull()?.let(Bukkit::getWorld)
        } ?: Bukkit.getWorld(worldName ?: currentWorldName)

        if (world == null) {
            return LoadResult(createNewStats(currentWorldName), true)
        }

        if (world.uid.toString() != worldUid) {
            return LoadResult(RunStats.create(world), true)
        }

        return LoadResult(
            stats = RunStats(
                worldUid = world.uid.toString(),
                worldName = world.name,
                startUnixMillis = config.getLong("start.unix-millis"),
                startFullTime = config.getLong("start.full-time"),
                elapsedPlayerTicks = config.getLong("elapsed-player-ticks"),
                participants = config.getStringList("participants")
                    .mapNotNull { value -> runCatching { UUID.fromString(value) }.getOrNull() }
                    .toMutableSet(),
                blocksBroken = config.getLong("counts.blocks-broken"),
                blocksPlaced = config.getLong("counts.blocks-placed"),
                oresBroken = config.getLong("counts.ores-broken"),
                craftingCount = config.getLong("counts.crafting"),
                reachedNether = config.getBoolean("milestones.nether.reached"),
                reachedNetherAtFullTime = config.getLong("milestones.nether.at-full-time").takeIf { it > 0L },
                reachedEnd = config.getBoolean("milestones.end.reached"),
                reachedEndAtFullTime = config.getLong("milestones.end.at-full-time").takeIf { it > 0L },
                totalDamageTaken = config.getDouble("combat.total-damage-taken"),
                nearDeathCount = config.getLong("combat.near-death-count"),
                hostileKills = config.getLong("combat.hostile-kills"),
                passiveNeutralKills = config.getLong("combat.passive-neutral-kills"),
                challengeStartAnnounced = config.getBoolean("notifications.challenge-start-announced"),
            ),
            created = false,
        )
    }

    fun save(stats: RunStats) {
        val config = YamlConfiguration()
        config.set("world.uid", stats.worldUid)
        config.set("world.name", stats.worldName)
        config.set("start.unix-millis", stats.startUnixMillis)
        config.set("start.full-time", stats.startFullTime)
        config.set("elapsed-player-ticks", stats.elapsedPlayerTicks)
        config.set("participants", stats.participants.map(UUID::toString))
        config.set("counts.blocks-broken", stats.blocksBroken)
        config.set("counts.blocks-placed", stats.blocksPlaced)
        config.set("counts.ores-broken", stats.oresBroken)
        config.set("counts.crafting", stats.craftingCount)
        config.set("milestones.nether.reached", stats.reachedNether)
        config.set("milestones.nether.at-full-time", stats.reachedNetherAtFullTime ?: 0L)
        config.set("milestones.end.reached", stats.reachedEnd)
        config.set("milestones.end.at-full-time", stats.reachedEndAtFullTime ?: 0L)
        config.set("combat.total-damage-taken", stats.totalDamageTaken)
        config.set("combat.near-death-count", stats.nearDeathCount)
        config.set("combat.hostile-kills", stats.hostileKills)
        config.set("combat.passive-neutral-kills", stats.passiveNeutralKills)
        config.set("notifications.challenge-start-announced", stats.challengeStartAnnounced)
        file.parentFile?.mkdirs()
        config.save(file)
    }

    private fun createNewStats(worldName: String): RunStats {
        val world = Bukkit.getWorld(worldName) ?: Bukkit.getWorlds().first()
        return RunStats.create(world)
    }
}
