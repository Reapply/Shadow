package com.shadow

import com.shadow.commands.ShadowCommand
import com.shadow.commands.SpawnCommand
import com.shadow.config.ConfigManager
import com.shadow.features.EnderButtFeature
import com.shadow.features.LaunchPad
import com.shadow.features.WorldProtection
import com.shadow.features.selector.QueueCommand
import com.shadow.features.selector.ServerSelector
import com.shadow.features.spawn.SetSpawnCommand
import com.shadow.features.spawn.SpawnManager
import com.shadow.listeners.PlayerListener
import org.bukkit.GameMode
import org.bukkit.GameRule
import org.bukkit.Sound
import org.bukkit.WeatherType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.plugin.java.JavaPlugin

class Shadow : JavaPlugin(), Listener {
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
        saveDefaultConfig()

        // Initialize plugin components
        setupBungee()
        ConfigManager.init(this)
        initializeFeatures()
        registerCommands()
        registerListeners()
        setupWorlds()
        resetOnlinePlayers()

        logger.info("Shadow Hub has been enabled!")
    }

    private fun registerCommands() {
        getCommand("queue")?.setExecutor(QueueCommand())
        getCommand("setspawn")?.setExecutor(SetSpawnCommand())
        getCommand("spawn")?.setExecutor(SpawnCommand())

        // Register shadow command with both executor and tab completer
        val shadowCommand = ShadowCommand()
        getCommand("shadow")?.apply {
            setExecutor(shadowCommand)
            tabCompleter = shadowCommand
        }
    }

    override fun onDisable() {
        logger.info("Shadow Hub has been disabled!")
    }

    private fun setupBungee() {
        server.messenger.registerOutgoingPluginChannel(this, BUNGEECORD_CHANNEL)
    }

    private fun initializeFeatures() {
        SpawnManager.init(this)
        ServerSelector.init(this)
        LaunchPad.init()
        WorldProtection.init()
        EnderButtFeature.init()
    }

    private fun registerListeners() {
        server.pluginManager.registerEvents(PlayerListener(), this)
        server.pluginManager.registerEvents(this, this)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        val currentItem = event.currentItem ?: return
        val meta = currentItem.itemMeta ?: return

        if (meta.hasCustomModelData() && meta.customModelData == ConfigManager.selectorConfig.customModelData) {
            event.isCancelled = true
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            ServerSelector.openSelector(player)
        }
    }

    private fun setupWorlds() {
        server.worlds.forEach { world ->
            world.apply {
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
        player.apply {
            // Reset stats
            gameMode = GameMode.ADVENTURE
            health = DEFAULT_HEALTH
            foodLevel = DEFAULT_FOOD_LEVEL
            allowFlight = true
            isFlying = false
            exp = 0f
            level = 0
            inventory.clear()
            setPlayerWeather(WeatherType.CLEAR)
            activePotionEffects.forEach { removePotionEffect(it.type) }

            // Give items
            ServerSelector.giveSelectorItem(this)
            EnderButtFeature.giveEnderButtItem(this)
        }
    }
}
