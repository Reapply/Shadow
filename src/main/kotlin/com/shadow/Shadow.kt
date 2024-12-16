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
    }

    override fun onEnable() {
        instance = this

        // Save default configs
        saveDefaultConfig()

        // Make it contact velocity/bungee
        server.messenger.registerOutgoingPluginChannel(this, "BungeeCord")

        // Initialize Twilight
        twilight(this) {
        }

        // Initialize features
        SpawnManager.init(this)
        ServerSelector.init(this)
        LaunchPad.init()
        WorldProtection.init()

        // Register listeners
        server.pluginManager.registerEvents(PlayerListener(), this)

        // Add event for opening selector
        event<InventoryClickEvent> {
            if (whoClicked !is Player) return@event
            val player = whoClicked as Player

            if (currentItem?.itemMeta?.hasCustomModelData() == true &&
                currentItem?.itemMeta?.customModelData == 1001) {
                isCancelled = true
                player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                ServerSelector.openSelector(player)
            }
        }

        // Set up world settings
        server.worlds.forEach { world ->
            world.setGameRule(GameRule.DO_MOB_SPAWNING, false)
            world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
            world.setGameRule(GameRule.DO_WEATHER_CYCLE, false)
            world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false)
            world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true)
            world.time = 6000L // Set to midday
            world.setStorm(false)
            world.isThundering = false
        }

        // Set all online players to hub state (in case of reload)
        server.onlinePlayers.forEach { player ->
            setPlayerHubState(player)
        }

        // Log startup
        logger.info("Sakura PvP Hub has been enabled!")
    }

    override fun onDisable() {
        // Log shutdown
        logger.info("Sakura PvP Hub has been disabled!")
    }

    fun setPlayerHubState(player: Player) {
        // Reset player state
        player.gameMode = GameMode.ADVENTURE
        player.health = 20.0
        player.foodLevel = 20
        player.allowFlight = true
        player.isFlying = false
        player.exp = 0f
        player.level = 0
        player.inventory.clear()
        player.setPlayerWeather(WeatherType.DOWNFALL)
        player.activePotionEffects.clear()

        // Teleport to spawn if available
        SpawnManager.getSpawn()?.let { spawn ->
            player.teleport(spawn)
        }

        // Give selector item
        ServerSelector.giveSelectorItem(player)
    }
}