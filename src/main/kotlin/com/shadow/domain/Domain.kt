package com.shadow.domain

import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration

data class ServerInfo(
    val id: String,
    val displayName: String,
    val material: String,
    val slot: Int,
    val description: List<String>,
    val serverId: String
) {
    init {
        require(id.isNotBlank()) { "Server ID cannot be blank" }
        require(slot >= 0) { "Slot must be non-negative" }
        require(material.isNotBlank()) { "Material cannot be blank" }
        require(serverId.isNotBlank()) { "Server ID cannot be blank" }
    }

    companion object {
        fun fromConfig(id: String, config: YamlConfiguration): ServerInfo =
            ServerInfo(
                id = id,
                displayName = config.getString("servers.$id.display-name") ?: id,
                material = config.getString("servers.$id.material") ?: "STONE",
                slot = config.getInt("servers.$id.slot"),
                description = config.getStringList("servers.$id.description"),
                serverId = config.getString("servers.$id.server-id") ?: id
            )
    }
}

data class SelectorConfig(
    val material: String = "COMPASS",
    val name: String = "<dark_purple>Server Selector",
    val slot: Int = 4,
    val guiTitle: String = "<dark_purple>Sakura Network",
    val guiSize: Int = 27,
    val useBackground: Boolean = true,
    val backgroundMaterial: String = "BLACK_STAINED_GLASS_PANE"
) {
    // Added validation to ensure valid GUI size
    init {
        require(guiSize % 9 == 0 && guiSize <= 54) { "GUI size must be a multiple of 9 and less than or equal to 54" }
        require(slot in 0..8) { "Selector slot must be between 0 and 8" }
    }

    // Added helper function to get Material enum from string
    fun getSelectorMaterial(): Material = try {
        Material.valueOf(material)
    } catch (e: IllegalArgumentException) {
        Material.COMPASS
    }

    // Added helper function for background material
    fun getBackgroundMaterial(): Material = try {
        Material.valueOf(backgroundMaterial)
    } catch (e: IllegalArgumentException) {
        Material.BLACK_STAINED_GLASS_PANE
    }

    companion object {
        fun fromConfig(config: YamlConfiguration): SelectorConfig =
            SelectorConfig(
                material = config.getString("selector.material") ?: "COMPASS",
                name = config.getString("selector.name") ?: "<dark_purple>Server Selector",
                slot = config.getInt("selector.slot", 4),
                guiTitle = config.getString("gui.title") ?: "<dark_purple>Sakura Network",
                guiSize = config.getInt("gui.size", 27),
                useBackground = config.getBoolean("gui.use-background", true),
                backgroundMaterial = config.getString("gui.background-material")
                    ?: "BLACK_STAINED_GLASS_PANE"
            )
    }
}

data class QueueConfig(
    val processInterval: Long = 20L,
    val messages: QueueMessages
) {
    data class QueueMessages(
        val joined: String = "<dark_purple>You joined the queue for %server%!",
        val left: String = "<dark_purple>You left the queue!",
        val disabled: String = "<dark_purple>This queue is currently disabled!",
        val locked: String = "<dark_purple>This queue is currently locked!"
    )

    companion object {
        fun fromConfig(config: YamlConfiguration): QueueConfig =
            QueueConfig(
                processInterval = config.getLong("queue.process-interval", 20L),
                messages = QueueMessages(
                    joined = config.getString("queue.messages.joined")
                        ?: "<dark_purple>You joined the queue for %server%!",
                    left = config.getString("queue.messages.left")
                        ?: "<dark_purple>You left the queue!",
                    disabled = config.getString("queue.messages.disabled")
                        ?: "<dark_purple>This queue is currently disabled!",
                    locked = config.getString("queue.messages.locked")
                        ?: "<dark_purple>This queue is currently locked!"
                )
            )
    }
}

sealed class QueueResult {
    data class Success(val position: Int) : QueueResult()
    data class Error(val message: String) : QueueResult()
    object Disabled : QueueResult()
    object AlreadyInQueue : QueueResult()

    // Helper function to get appropriate message for each result type
    fun getMessage(config: QueueConfig, serverName: String = ""): String = when (this) {
        is Success -> config.messages.joined.replace("%server%", serverName)
        is Error -> message
        is Disabled -> config.messages.disabled
        is AlreadyInQueue -> "You are already in the queue for $serverName"
    }
}