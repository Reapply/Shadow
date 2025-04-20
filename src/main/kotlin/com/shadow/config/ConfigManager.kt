package com.shadow.config

import com.shadow.Shadow
import com.shadow.domain.QueueConfig
import com.shadow.domain.SelectorConfig
import com.shadow.domain.ServerInfo
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.IOException

/**
 * Centralized configuration manager to handle all config-related operations
 * across the plugin.
 */
object ConfigManager {
    // Config Files
    private lateinit var mainConfig: YamlConfiguration
    private lateinit var spawnConfig: YamlConfiguration

    // Config Files
    private lateinit var mainConfigFile: File
    private lateinit var spawnConfigFile: File

    // Domain Objects
    lateinit var selectorConfig: SelectorConfig
        private set

    lateinit var queueConfig: QueueConfig
        private set

    lateinit var serverList: List<ServerInfo>
        private set

    private var spawnLocation: Location? = null

    /**
     * Initialize all configuration files and load settings
     */
    fun init(plugin: Shadow) {
        // Set up main config
        mainConfigFile = File(plugin.dataFolder, "config.yml")
        if (!mainConfigFile.exists()) {
            plugin.saveDefaultConfig()
        }
        mainConfig = YamlConfiguration.loadConfiguration(mainConfigFile)

        // Set up spawn config
        spawnConfigFile = File(plugin.dataFolder, "spawn.yml")
        if (!spawnConfigFile.exists()) {
            spawnConfigFile.createNewFile()
        }
        spawnConfig = YamlConfiguration.loadConfiguration(spawnConfigFile)

        // Load all configurations
        loadConfigurations()
    }

    fun getHubWorldName(): String? {
        return mainConfig.getString("world", "world")
    }

    /**
     * Load all configurations from files
     */
    private fun loadConfigurations() {
        // Load domain objects from config
        selectorConfig = SelectorConfig.fromConfig(mainConfig)
        queueConfig = QueueConfig.fromConfig(mainConfig)

        // Load server list
        serverList = buildList {
            mainConfig.getConfigurationSection("servers")?.getKeys(false)?.forEach { key ->
                add(ServerInfo.fromConfig(key, mainConfig))
            }
        }

        // Load spawn location
        loadSpawn()
    }

    /**
     * Load the spawn location from config
     */
    private fun loadSpawn() {
        if (spawnConfig.contains("spawn")) {
            val world = Bukkit.getWorld(spawnConfig.getString("spawn.world")!!)
            val x = spawnConfig.getDouble("spawn.x")
            val y = spawnConfig.getDouble("spawn.y")
            val z = spawnConfig.getDouble("spawn.z")
            val yaw = spawnConfig.getDouble("spawn.yaw").toFloat()
            val pitch = spawnConfig.getDouble("spawn.pitch").toFloat()

            spawnLocation = Location(world, x, y, z, yaw, pitch)
        }
    }

    /**
     * Save the spawn location to config
     */
    fun saveSpawn(location: Location) {
        spawnLocation = location

        spawnConfig.set("spawn.world", location.world?.name)
        spawnConfig.set("spawn.x", location.x)
        spawnConfig.set("spawn.y", location.y)
        spawnConfig.set("spawn.z", location.z)
        spawnConfig.set("spawn.yaw", location.yaw)
        spawnConfig.set("spawn.pitch", location.pitch)

        try {
            spawnConfig.save(spawnConfigFile)
        } catch (e: IOException) {
            Shadow.instance.logger.warning("Failed to save spawn config: ${e.message}")
        }
    }

    /**
     * Get the spawn location
     */
    fun getSpawnLocation(): Location? = spawnLocation

    /**
     * Update queue status in config and save
     */
    fun updateQueueStatus(serverId: String, enabled: Boolean) {
        mainConfig.set("servers.$serverId.enabled", enabled)

        mainConfig.getStringList("servers.$serverId.description").toMutableList().let { description ->
            val statusIndex = description.indexOfFirst { it.contains("Queue Status") }
            if (statusIndex != -1) {
                description[statusIndex] = "<gray>Queue Status: ${if (enabled) "<green>Open" else "<red>Disabled"}"
                mainConfig.set("servers.$serverId.description", description)
            }
        }

        saveMainConfig()
    }

    /**
     * Save main config file
     */
    fun saveMainConfig() {
        try {
            mainConfig.save(mainConfigFile)
        } catch (e: IOException) {
            Shadow.instance.logger.warning("Failed to save config: ${e.message}")
        }
    }
}