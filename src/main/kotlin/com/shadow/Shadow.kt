package com.shadow

import com.shadow.features.*
import com.shadow.features.selector.ServerSelector
import com.shadow.features.spawn.SpawnManager
import com.shadow.listeners.PlayerListener
import gg.flyte.twilight.event.event
import gg.flyte.twilight.twilight
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Sound
import org.bukkit.WeatherType
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.plugin.java.JavaPlugin

class Shadow : JavaPlugin() {
    companion object {
        lateinit var instance: Shadow
            private set

        private const val BUNGEECORD_CHANNEL = "BungeeCord"
        private const val DEFAULT_HEALTH = 20.0
        private const val DEFAULT_FOOD_LEVEL = 20
        private const val DEFAULT_TIME = 6000L // Midday
    }

    override fun onEnable() {
        instance = this
        initializePlugin()
        logger.info("Shadow Hub has been enabled!")
    }

    override fun onDisable() {
        logger.info("Shadow Hub has been disabled!")
    }

    private fun initializePlugin() {
        saveDefaultConfig()
        setupBungee()
        initializeTwilight()
        initializeFeatures()
        registerListeners()
        setupWorlds()
        resetOnlinePlayers()
    }

    private fun setupBungee() {
        server.messenger.registerOutgoingPluginChannel(this, BUNGEECORD_CHANNEL)
    }

    private fun initializeTwilight() {
        twilight(this) { }
    }

    private fun initializeFeatures() {
        with(this) {
            SpawnManager.init(this)
            ServerSelector.init(this)
            FireplaceEffect.init(this)
            LaunchPad.init()
            WorldProtection.init()
            SnowballFeature.init()
        }
    }

    private fun registerListeners() {
        server.pluginManager.registerEvents(PlayerListener(), this)
        registerSelectorClickEvent()
    }

    private fun registerSelectorClickEvent() {
        event<InventoryClickEvent> {
            if (whoClicked !is Player) return@event
            val player = whoClicked as Player

            if (currentItem?.itemMeta?.let { meta ->
                    meta.hasCustomModelData() && meta.customModelData == 1001
                } == true) {
                isCancelled = true
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                ServerSelector.openSelector(player)
            }
        }
    }

    private fun setupWorlds() {
        server.worlds.forEach { world ->
            with(world) {
                setGameRule(GameRule.DO_MOB_SPAWNING, false)
                setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
                setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
                setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
                time = DEFAULT_TIME
                setStorm(true)
            }
        }
    }

    private fun resetOnlinePlayers() {
        server.onlinePlayers.forEach(::setPlayerHubState)
    }

    fun setPlayerHubState(player: Player) {
        resetPlayerStats(player)
        teleportToSpawn(player)
        giveItems(player)
    }

    private fun resetPlayerStats(player: Player) {
        with(player) {
            gameMode = GameMode.ADVENTURE
            health = DEFAULT_HEALTH
            foodLevel = DEFAULT_FOOD_LEVEL
            allowFlight = true
            isFlying = false
            exp = 0f
            level = 0
            inventory.clear()
            setPlayerWeather(WeatherType.DOWNFALL)
            activePotionEffects.forEach { effect ->
                removePotionEffect(effect.type)
            }
        }
    }

    private fun teleportToSpawn(player: Player) {
        SpawnManager.getSpawn()?.let { spawn ->
            player.teleport(spawn)
        }
    }

    private fun giveItems(player: Player) {
        ServerSelector.giveSelectorItem(player)
        SnowballFeature.giveSnowballItem(player)
    }
}