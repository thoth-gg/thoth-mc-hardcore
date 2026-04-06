package gg.thoth.thothMcHardcore

import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.EntityType
import java.util.UUID

data class RunStats(
    val worldUid: String,
    val worldName: String,
    val startUnixMillis: Long,
    val startFullTime: Long,
    var elapsedPlayerTicks: Long = 0L,
    val participants: MutableSet<UUID> = mutableSetOf(),
    var blocksBroken: Long = 0L,
    var blocksPlaced: Long = 0L,
    var oresBroken: Long = 0L,
    var craftingCount: Long = 0L,
    var reachedNether: Boolean = false,
    var reachedNetherAtFullTime: Long? = null,
    var reachedEnd: Boolean = false,
    var reachedEndAtFullTime: Long? = null,
    var totalDamageTaken: Double = 0.0,
    var nearDeathCount: Long = 0L,
    var hostileKills: Long = 0L,
    var passiveNeutralKills: Long = 0L,
    var challengeStartAnnounced: Boolean = false,
) {
    companion object {
        fun create(world: World, nowMillis: Long = System.currentTimeMillis()): RunStats = RunStats(
            worldUid = world.uid.toString(),
            worldName = world.name,
            startUnixMillis = nowMillis,
            startFullTime = world.fullTime,
        )

        val trackedOres: Set<Material> = setOf(
            Material.COAL_ORE,
            Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE,
            Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE,
            Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE,
            Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE,
            Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE,
            Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE,
            Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE,
            Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE,
            Material.NETHER_GOLD_ORE,
            Material.ANCIENT_DEBRIS,
        )

        val passiveOrNeutralEntityTypes: Set<EntityType> = setOf(
            EntityType.ALLAY,
            EntityType.ARMADILLO,
            EntityType.AXOLOTL,
            EntityType.BAT,
            EntityType.BEE,
            EntityType.CAMEL,
            EntityType.CAT,
            EntityType.CHICKEN,
            EntityType.COD,
            EntityType.COW,
            EntityType.DOLPHIN,
            EntityType.DONKEY,
            EntityType.FOX,
            EntityType.FROG,
            EntityType.GLOW_SQUID,
            EntityType.GOAT,
            EntityType.HORSE,
            EntityType.IRON_GOLEM,
            EntityType.LLAMA,
            EntityType.MOOSHROOM,
            EntityType.MULE,
            EntityType.OCELOT,
            EntityType.PANDA,
            EntityType.PARROT,
            EntityType.PIG,
            EntityType.POLAR_BEAR,
            EntityType.PUFFERFISH,
            EntityType.RABBIT,
            EntityType.SALMON,
            EntityType.SHEEP,
            EntityType.SKELETON_HORSE,
            EntityType.SNIFFER,
            EntityType.SNOW_GOLEM,
            EntityType.SQUID,
            EntityType.STRIDER,
            EntityType.TRADER_LLAMA,
            EntityType.TROPICAL_FISH,
            EntityType.TURTLE,
            EntityType.VILLAGER,
            EntityType.WANDERING_TRADER,
            EntityType.WOLF,
            EntityType.ZOMBIE_HORSE,
        )
    }
}
