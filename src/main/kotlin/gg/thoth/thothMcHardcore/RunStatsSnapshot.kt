package gg.thoth.thothMcHardcore

import java.time.Instant

data class RunStatsSnapshot(
    val worldName: String,
    val deathMessage: String?,
    val gameDays: Double,
    val aggregatePlayerDays: Double,
    val participantCount: Int,
    val blocksBroken: Long,
    val blocksPlaced: Long,
    val oresBroken: Long,
    val craftingCount: Long,
    val netherReached: Boolean,
    val netherReachedAfterDays: Double?,
    val endReached: Boolean,
    val endReachedAfterDays: Double?,
    val totalDamageTaken: Double,
    val nearDeathCount: Long,
    val hostileKills: Long,
    val passiveNeutralKills: Long,
    val finishedAtIso: String,
) {
    companion object {
        private const val TICKS_PER_DAY = 24000.0

        fun from(
            stats: RunStats,
            finishedAtMillis: Long,
            currentFullTime: Long,
            deathMessage: String? = null,
        ): RunStatsSnapshot = RunStatsSnapshot(
            worldName = stats.worldName,
            deathMessage = deathMessage,
            gameDays = ((currentFullTime - stats.startFullTime) / TICKS_PER_DAY).coerceAtLeast(0.0),
            aggregatePlayerDays = stats.elapsedPlayerTicks / TICKS_PER_DAY,
            participantCount = stats.participants.size,
            blocksBroken = stats.blocksBroken,
            blocksPlaced = stats.blocksPlaced,
            oresBroken = stats.oresBroken,
            craftingCount = stats.craftingCount,
            netherReached = stats.reachedNether,
            netherReachedAfterDays = stats.reachedNetherAtFullTime?.let { (it - stats.startFullTime) / TICKS_PER_DAY },
            endReached = stats.reachedEnd,
            endReachedAfterDays = stats.reachedEndAtFullTime?.let { (it - stats.startFullTime) / TICKS_PER_DAY },
            totalDamageTaken = stats.totalDamageTaken,
            nearDeathCount = stats.nearDeathCount,
            hostileKills = stats.hostileKills,
            passiveNeutralKills = stats.passiveNeutralKills,
            finishedAtIso = Instant.ofEpochMilli(finishedAtMillis).toString(),
        )
    }
}
