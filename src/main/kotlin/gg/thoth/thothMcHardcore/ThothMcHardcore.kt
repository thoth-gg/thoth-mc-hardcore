package gg.thoth.thothMcHardcore

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.damage.DamageType
import org.bukkit.entity.Firework
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Monster
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.util.UUID

class ThothMcHardcore : JavaPlugin(), Listener {

    private lateinit var languageMap: LanguageMap
    private lateinit var colorTeams: ColorTeams
    private lateinit var healthSidebar: HealthSidebar
    private var deathMessagePacketListener: DeathMessagePacketListener? = null
    private var shutdownTask: BukkitTask? = null
    private var statsTask: BukkitTask? = null
    private var shutdownSequenceStarted = false
    private val json = Json { ignoreUnknownKeys = true }
    private lateinit var statsStore: RunStatsStore
    private lateinit var runStats: RunStats
    private val nearDeathPlayers = mutableSetOf<UUID>()
    private var discordWebhookClient: DiscordWebhookClient? = null
    @Volatile
    private var latestDeathMessage: String? = null
    private val resetRequestFileName = "reset-world.lock"

    override fun onEnable() {
        saveDefaultConfig()
        languageMap = loadJapaneseLanguage()
        colorTeams = ColorTeams(this)
        colorTeams.ensureExists()
        statsStore = RunStatsStore(File(dataFolder, "run-stats.yml"))
        val primaryWorld = getPrimaryWorld()
        val loadResult = statsStore.loadOrCreate(primaryWorld.name)
        runStats = loadResult.stats
        if (runStats.worldUid != primaryWorld.uid.toString()) {
            runStats = RunStats.create(primaryWorld)
            statsStore.save(runStats)
        }
        discordWebhookClient = DiscordWebhookClient.fromConfig(config)
        server.onlinePlayers.forEach(colorTeams::assignIfMissing)
        healthSidebar = HealthSidebar(this)
        healthSidebar.start()
        server.onlinePlayers.forEach(::markParticipant)
        startStatsTask()
        registerProtocolLibListener()
        server.pluginManager.registerEvents(this, this)
    }

    override fun onDisable() {
        if (::healthSidebar.isInitialized) {
            healthSidebar.stop()
        }
        deathMessagePacketListener?.close()
        shutdownTask?.cancel()
        statsTask?.cancel()
        if (::statsStore.isInitialized && ::runStats.isInitialized) {
            statsStore.save(runStats)
        }
    }

    @EventHandler
    fun onPlayerDeath(event: PlayerDeathEvent) {
        markParticipant(event.entity)
        reportRunToDiscord(event.deathMessage)
        server.onlinePlayers.forEach { player ->
            player.playSound(player, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f)
            player.playSound(player, Sound.MUSIC_DISC_13, 0.5f, 1.0f)
        }
        startShutdownSequence()
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        colorTeams.assignIfMissing(event.player)
        val isFirstParticipant = runStats.participants.isEmpty() && !runStats.challengeStartAnnounced
        markParticipant(event.player)
        if (isFirstParticipant) {
            runStats.challengeStartAnnounced = true
            notifyChallengeStart(getPrimaryWorld(), event.player)
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.entity is Player && event.damageSource.damageType == DamageType.FIREWORKS) {
            event.isCancelled = true
            return
        }

        val player = event.entity as? Player ?: return
        if (event.isCancelled || event.finalDamage <= 0.0) {
            return
        }

        markParticipant(player)
        val actualDamageTaken = minOf(event.finalDamage, player.health + player.absorptionAmount)
        runStats.totalDamageTaken += actualDamageTaken
        val remainingHealth = player.health - event.finalDamage
        if (remainingHealth <= 3.0) {
            if (nearDeathPlayers.add(player.uniqueId)) {
                runStats.nearDeathCount += 1
            }
        } else {
            nearDeathPlayers.remove(player.uniqueId)
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        if (event.isCancelled) {
            return
        }

        markParticipant(event.player)
        runStats.blocksBroken += 1
        if (event.block.type in RunStats.trackedOres) {
            runStats.oresBroken += 1
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        if (event.isCancelled) {
            return
        }

        markParticipant(event.player)
        runStats.blocksPlaced += 1
    }

    @EventHandler
    fun onCraftItem(event: CraftItemEvent) {
        if (event.isCancelled) {
            return
        }

        val player = event.whoClicked as? Player ?: return
        if (event.currentItem?.type == Material.AIR) {
            return
        }

        markParticipant(player)
        runStats.craftingCount += 1
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        markParticipant(event.player)
        updateDimensionMilestones(event.player.world)
    }

    @EventHandler
    fun onEntityDeath(event: EntityDeathEvent) {
        val killer = event.entity.killer ?: return
        markParticipant(killer)

        val entity: LivingEntity = event.entity
        if (entity is Monster) {
            runStats.hostileKills += 1
            return
        }

        if (entity.type in RunStats.passiveOrNeutralEntityTypes) {
            runStats.passiveNeutralKills += 1
        }
    }

    fun showGameOverTitle(subtitle: String) {
        cacheDeathMessage(subtitle)
        server.scheduler.runTask(this, Runnable {
            server.onlinePlayers.forEach { player ->
                player.sendTitle("GAME OVER", subtitle, 10, 70, 20)
                spawnCelebrationFireworks(player.location)
            }
        })
    }

    private fun spawnCelebrationFireworks(origin: Location) {
        val world = origin.world ?: return
        val effects = listOf(
            FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL_LARGE)
                .withColor(
                    Color.fromRGB(255, 110, 110),
                    Color.fromRGB(255, 175, 80),
                    Color.fromRGB(255, 225, 90),
                )
                .withFade(
                    Color.fromRGB(255, 150, 140),
                    Color.fromRGB(255, 210, 120),
                )
                .trail(true)
                .flicker(true)
                .build(),
            FireworkEffect.builder()
                .with(FireworkEffect.Type.STAR)
                .withColor(
                    Color.fromRGB(120, 235, 120),
                    Color.fromRGB(70, 200, 255),
                    Color.fromRGB(90, 120, 255),
                )
                .withFade(
                    Color.fromRGB(150, 245, 170),
                    Color.fromRGB(135, 180, 255),
                )
                .trail(true)
                .flicker(true)
                .build(),
            FireworkEffect.builder()
                .with(FireworkEffect.Type.BURST)
                .withColor(
                    Color.fromRGB(145, 110, 255),
                    Color.fromRGB(220, 95, 255),
                    Color.fromRGB(255, 135, 210),
                )
                .withFade(
                    Color.fromRGB(180, 145, 255),
                    Color.fromRGB(255, 175, 230),
                )
                .trail(true)
                .flicker(true)
                .build(),
        )
        val bursts = listOf(
            origin.clone().add(2.0, 1.2, 0.0),
            origin.clone().add(-2.0, 1.2, 0.0),
            origin.clone().add(1.4, 2.4, 1.4),
            origin.clone().add(-1.4, 2.4, -1.4),
        )

        bursts.forEachIndexed { index, location ->
            server.scheduler.runTaskLater(this, Runnable {
                val firework = world.spawn(location, Firework::class.java)
                firework.fireworkMeta = firework.fireworkMeta.apply {
                    clearEffects()
                    power = 0
                    addEffect(effects[index % effects.size])
                    if (index == 1) {
                        addEffect(effects[(index + 1) % effects.size])
                    }
                }
                firework.detonate()
            }, index.toLong())
        }
    }

    private fun startShutdownSequence() {
        if (shutdownSequenceStarted) {
            return
        }

        shutdownSequenceStarted = true
        shutdownTask = server.scheduler.runTaskLater(this, Runnable {
            startCountdown(10)
        }, 20L * 5)
    }

    private fun startCountdown(seconds: Int) {
        if (seconds <= 0) {
            server.onlinePlayers.toList().forEach { player ->
                player.kickPlayer("新しい世界を創るために、サーバーを再起動します...")
            }
            createResetRequestFile()
            server.shutdown()
            return
        }

        server.onlinePlayers.forEach { player ->
            player.playSound(player, Sound.UI_BUTTON_CLICK, 0.5f, 1.0f)
        }

        if (seconds == 10 || seconds == 5 || seconds <= 3) {
            server.broadcastMessage("[SYSTEM] 残り${seconds}秒")
        }

        shutdownTask = server.scheduler.runTaskLater(this, Runnable {
            startCountdown(seconds - 1)
        }, 20L)
    }

    private fun registerProtocolLibListener() {
        if (server.pluginManager.getPlugin("ProtocolLib") == null) {
            logger.warning("ProtocolLib が見つからないため、死亡メッセージのタイトル表示は無効です。")
            return
        }

        deathMessagePacketListener = DeathMessagePacketListener(
            plugin = this,
            formatter = DeathMessageFormatter(languageMap),
        ).also { it.register() }
    }

    private fun startStatsTask() {
        statsTask?.cancel()
        statsTask = server.scheduler.runTaskTimer(this, Runnable {
            val activePlayers = server.onlinePlayers.count { player ->
                markParticipant(player)
                player.gameMode != GameMode.SPECTATOR && !player.isDead && player.health > 0.0
            }
            runStats.elapsedPlayerTicks += activePlayers * 20L
            server.worlds.forEach(::updateDimensionMilestones)
            statsStore.save(runStats)
        }, 20L, 20L)
    }

    private fun updateDimensionMilestones(world: World) {
        val fullTime = getPrimaryWorld().fullTime
        when (world.environment) {
            World.Environment.NETHER -> {
                if (!runStats.reachedNether && world.players.isNotEmpty()) {
                    runStats.reachedNether = true
                    runStats.reachedNetherAtFullTime = fullTime
                }
            }

            World.Environment.THE_END -> {
                if (!runStats.reachedEnd && world.players.isNotEmpty()) {
                    runStats.reachedEnd = true
                    runStats.reachedEndAtFullTime = fullTime
                }
            }

            else -> Unit
        }
    }

    private fun markParticipant(player: Player) {
        runStats.participants += player.uniqueId
    }

    private fun reportRunToDiscord(fallbackDeathMessage: String?) {
        if (!shutdownSequenceStarted) {
            statsStore.save(runStats)
            server.scheduler.runTaskLater(this, Runnable {
                val snapshot = RunStatsSnapshot.from(
                    stats = runStats,
                    finishedAtMillis = System.currentTimeMillis(),
                    currentFullTime = getPrimaryWorld().fullTime,
                    deathMessage = latestDeathMessage ?: fallbackDeathMessage?.trim()?.takeIf { it.isNotEmpty() },
                )
                server.scheduler.runTaskAsynchronously(this, Runnable {
                try {
                    discordWebhookClient?.sendFailureEmbed(snapshot)
                } catch (exception: Exception) {
                    logger.warning("Discord webhook送信に失敗しました: ${exception.message}")
                }
                })
            }, 1L)
        }
    }

    private fun notifyChallengeStart(world: World, player: Player) {
        val startedAtMillis = System.currentTimeMillis()
        statsStore.save(runStats)
        server.scheduler.runTaskAsynchronously(this, Runnable {
            try {
                discordWebhookClient?.sendChallengeStartEmbed(
                    worldName = world.name,
                    seed = world.seed,
                    playerName = player.name,
                    startedAtMillis = startedAtMillis,
                )
            } catch (exception: Exception) {
                logger.warning("Discord webhookへの開始通知に失敗しました: ${exception.message}")
            }
        })
    }

    private fun cacheDeathMessage(message: String?) {
        latestDeathMessage = message?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun getPrimaryWorld(): World =
        server.worlds.firstOrNull { it.environment == World.Environment.NORMAL } ?: server.worlds.first()

    private fun createResetRequestFile() {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warning("プラグインデータフォルダを作成できなかったため、リセット要求ファイルを作成できませんでした。")
            return
        }

        val resetRequestFile = File(dataFolder, resetRequestFileName)
        try {
            resetRequestFile.writeText(
                "requestedAt=${System.currentTimeMillis()}\nplugin=${description.name}\n",
                Charsets.UTF_8,
            )
            logger.info("リセット要求ファイルを作成しました: ${resetRequestFile.absolutePath}")
        } catch (exception: Exception) {
            logger.warning("リセット要求ファイルの作成に失敗しました: ${resetRequestFile.absolutePath} (${exception.message})")
        }
    }

    private fun loadJapaneseLanguage(): LanguageMap {
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
        }

        val langFile = File(dataFolder, "ja_jp.json")
        if (!langFile.exists()) {
            logger.warning("公式の日本語言語ファイルが見つかりません: ${langFile.absolutePath}")
            logger.warning("Minecraft の ja_jp.json をこの場所に配置すると、死亡メッセージを日本語で表示できます。")
            return LanguageMap(emptyMap())
        }

        return try {
            val root = json.parseToJsonElement(langFile.readText(Charsets.UTF_8)).jsonObject
            LanguageMap(root.mapValues { (_, value) -> value.jsonPrimitive.content })
        } catch (exception: Exception) {
            logger.warning("ja_jp.json の読み込みに失敗しました: ${exception.message}")
            LanguageMap(emptyMap())
        }
    }
}
