package com.shadow.features.selector

import com.shadow.Shadow
import com.shadow.domain.*
import gg.flyte.twilight.builders.item.ItemBuilder
import gg.flyte.twilight.event.event
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.*
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * Manages server selection and queuing functionality for a multi-server network.
 * Provides a GUI-based server selector and handles queue management for server transfers.
 */
object ServerSelector {
    private val mm = MiniMessage.miniMessage()

    // Configuration objects loaded from config.yml
    private val config: YamlConfiguration by lazy {
        YamlConfiguration.loadConfiguration(File(Shadow.instance.dataFolder, "config.yml"))
    }

    private val selectorConfig: SelectorConfig by lazy {
        SelectorConfig.fromConfig(config)
    }

    private val queueConfig: QueueConfig by lazy {
        QueueConfig.fromConfig(config)
    }

    // List of available servers and their configurations
    val serverList: List<ServerInfo> by lazy {
        buildList {
            config.getConfigurationSection("servers")?.getKeys(false)?.forEach { key ->
                add(ServerInfo.fromConfig(key, config))
            }
        }
    }

    // Queue management
    private val queues: Map<String, LinkedBlockingQueue<UUID>> by lazy {
        serverList.associate { it.id to LinkedBlockingQueue<UUID>() }
    }

    // Tracks how long players have been in queues
    private val queueTimes = ConcurrentHashMap<UUID, Long>()

    // Tracks which server queues are currently disabled
    private val disabledQueues = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val selectorItem by lazy {
        createSelectorItem()
    }

    /**
     * Initializes the ServerSelector system.
     * Sets up queues, registers event handlers, and starts queue processing.
     */
    fun init(plugin: Shadow) {
        loadDisabledQueues()
        registerEvents()
        startQueueProcessor(plugin)

        plugin.getCommand("queue")?.setExecutor(QueueCommand())
        plugin.getCommand("queue")?.tabCompleter = QueueTabCompleter()
    }

    /**
     * Loads the initial state of disabled queues from configuration
     */
    private fun loadDisabledQueues() {
        serverList.forEach { server ->
            if (!config.getBoolean("servers.${server.id}.enabled", true)) {
                disabledQueues.add(server.id)
            }
        }
    }

    /**
     * Creates the item used to open the server selector GUI
     */
    private fun createSelectorItem(): ItemBuilder =
        ItemBuilder(
            Material.valueOf(selectorConfig.material),
            name = mm.deserialize(selectorConfig.name)
        ).apply {
            customModelData = 1001
        }

    /**
     * Creates an ItemStack representing a server in the selector GUI
     * Includes server name, description, and current queue status
     */
    private fun createServerItem(server: ServerInfo): ItemStack {
        val description = config.getStringList("servers.${server.id}.description").toMutableList()
        val statusIndex = description.indexOfFirst { it.contains("Queue Status") }

        if (statusIndex != -1) {
            description[statusIndex] = "<gray>Queue Status: ${getQueueStatus(server.id)}"
        }

        return ItemBuilder(
            Material.valueOf(server.material),
            name = mm.deserialize(server.displayName)
        ).apply {
            lore = description.map { mm.deserialize(it) }.toMutableList()
        }.build()
    }

    /**
     * Returns the current status of a server's queue (Open/Disabled)
     */
    private fun getQueueStatus(serverId: String): String =
        if (disabledQueues.contains(serverId)) "<red>Disabled" else "<green>Open"

    /**
     * Updates the queue status in the configuration file and saves changes
     */
    private fun updateQueueStatusInConfig(serverId: String, enabled: Boolean) {
        config.set("servers.$serverId.enabled", enabled)

        config.getStringList("servers.$serverId.description").toMutableList().let { description ->
            val statusIndex = description.indexOfFirst { it.contains("Queue Status") }
            if (statusIndex != -1) {
                description[statusIndex] = "<gray>Queue Status: ${if (enabled) "<green>Open" else "<red>Disabled"}"
                config.set("servers.$serverId.description", description)
            }
        }

        try {
            config.save(File(Shadow.instance.dataFolder, "config.yml"))
        } catch (e: IOException) {
            Shadow.instance.logger.warning("Failed to save queue status: ${e.message}")
        }
    }

    /**
     * Updates all open server selector GUIs to reflect current queue statuses
     */
    fun updateAllSelectors() {
        Bukkit.getOnlinePlayers()
            .filter { it.openInventory.title() == mm.deserialize(selectorConfig.guiTitle) }
            .forEach { player ->
                val inventory = player.openInventory.topInventory
                serverList.forEach { server ->
                    inventory.setItem(server.slot, createServerItem(server))
                }
            }
    }

    /**
     * Opens the server selector GUI for a player
     */
    fun openSelector(player: Player) {
        val inventory = Bukkit.createInventory(
            null,
            selectorConfig.guiSize,
            mm.deserialize(selectorConfig.guiTitle)
        )

        // Fill background if enabled
        if (selectorConfig.useBackground) {
            val bgItem = ItemBuilder(
                Material.valueOf(selectorConfig.backgroundMaterial),
                name = mm.deserialize(" ")
            ).build()

            for (i in 0 until selectorConfig.guiSize) {
                inventory.setItem(i, bgItem)
            }
        }

        // Add server items
        serverList.forEach { server ->
            inventory.setItem(server.slot, createServerItem(server))
        }

        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 1f, 1.4f)
        player.openInventory(inventory)
    }

    /**
     * Adds a player to a server's queue
     * @return QueueResult indicating success/failure and queue position
     */
    fun addToQueue(player: Player, serverId: String): QueueResult {
        if (disabledQueues.contains(serverId)) {
            player.sendMessage(mm.deserialize(queueConfig.messages.disabled))
            return QueueResult.Disabled
        }

        val queue = queues[serverId] ?: return QueueResult.Error("Invalid server")

        if (queue.contains(player.uniqueId)) {
            return QueueResult.AlreadyInQueue
        }

        // Remove from any existing queues before adding to new one
        removeFromAllQueues(player)
        queue.offer(player.uniqueId)
        queueTimes[player.uniqueId] = System.currentTimeMillis()

        val server = serverList.find { it.id == serverId }
        player.sendMessage(mm.deserialize(
            queueConfig.messages.joined.replace("%server%", server?.displayName ?: serverId)
        ))
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)

        return QueueResult.Success(queue.size)
    }

    /**
     * Removes a player from all server queues
     */
    fun removeFromAllQueues(player: Player) {
        queues.values.forEach { it.remove(player.uniqueId) }
        queueTimes.remove(player.uniqueId)
    }

    /**
     * Gets the current queue and position for a player
     * @return Pair of (serverId, position) or null if not in any queue
     */
    private fun getPlayerQueue(player: Player): Pair<String, Int>? {
        queues.forEach { (serverId, queue) ->
            queue.toList().indexOf(player.uniqueId).let { position ->
                if (position != -1) {
                    return serverId to (position + 1)
                }
            }
        }
        return null
    }

    /**
     * Formats milliseconds into a human-readable duration string
     */
    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            else -> "${seconds / 60}m ${seconds % 60}s"
        }
    }

    /**
     * Gives the selector item to a player
     */
    fun giveSelectorItem(player: Player) {
        player.inventory.setItem(selectorConfig.slot, selectorItem.build())
    }

    /**
     * Sends a player to another server via BungeeCord
     */
    private fun sendToServer(player: Player, server: String) {
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeUTF("Connect")
                out.writeUTF(server)
                player.sendPluginMessage(Shadow.instance, "BungeeCord", bytes.toByteArray())
            }
        }
    }

    /**
     * Starts the queue processing and status update tasks
     */
    private fun startQueueProcessor(plugin: Shadow) {
        // Process queues every second
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable {
            queues.asSequence()
                .filter { (serverId, _) -> !disabledQueues.contains(serverId) }
                .forEach { (serverId, queue) ->
                    queue.poll()?.let { playerId ->
                        Bukkit.getPlayer(playerId)?.let { player ->
                            queueTimes.remove(playerId)
                            sendToServer(player, serverId)
                        }
                    }
                }
        }, 20L, 20L)

        // Update action bars for queued players
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable{
            Bukkit.getOnlinePlayers().forEach { player ->
                getPlayerQueue(player)?.let { (serverId, position) ->
                    val timeInQueue = System.currentTimeMillis() - (queueTimes[player.uniqueId] ?: System.currentTimeMillis())
                    val queueSize = queues[serverId]?.size ?: 0

                    player.sendActionBar(
                        Component.text()
                            .append(Component.text("In Queue: "))
                            .append(Component.text(serverList.find { it.id == serverId }?.displayName ?: serverId))
                            .append(Component.text(" | Position: $position/$queueSize"))
                            .append(Component.text(" | Time: ${formatTime(timeInQueue)}"))
                            .color(TextColor.color(0xFF69B4))
                            .build()
                    )
                }
            }
        }, 20L, 20L)
    }

    /**
     * Registers all event handlers for the server selector system
     */
    private fun registerEvents() {
        // Give selector item on join
        event<PlayerJoinEvent> {
            giveSelectorItem(player)
        }

        // Prevent dropping selector item
        event<PlayerDropItemEvent> {
            if (itemDrop.itemStack.itemMeta?.hasCustomModelData() == true &&
                itemDrop.itemStack.itemMeta?.customModelData == 1001) {
                isCancelled = true
            }
        }

        // Handle right-click to open selector
        event<PlayerInteractEvent> {
            if (action == org.bukkit.event.block.Action.RIGHT_CLICK_AIR ||
                action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                if (item?.itemMeta?.hasCustomModelData() == true &&
                    item?.itemMeta?.customModelData == 1001) {
                    isCancelled = true
                    openSelector(player)
                }
            }
        }

        // Handle clicks in selector GUI
        event<InventoryClickEvent> {
            if (view.title() == mm.deserialize(selectorConfig.guiTitle)) {
                isCancelled = true

                if (whoClicked !is Player) return@event
                val player = whoClicked as Player

                serverList.find { it.slot == slot }?.let { server ->
                    player.closeInventory()
                    addToQueue(player, server.id)
                }
            }
        }

        // Remove from queues on disconnect
        event<PlayerQuitEvent> {
            removeFromAllQueues(player)
        }
    }

    /**
     * Disables a server's queue and updates the selector GUI
     */
    fun disableQueue(serverId: String) {
        disabledQueues.add(serverId)
        updateQueueStatusInConfig(serverId, false)
        updateAllSelectors()
    }

    /**
     * Enables a server's queue and updates the selector GUI
     */
    fun enableQueue(serverId: String) {
        disabledQueues.remove(serverId)
        updateQueueStatusInConfig(serverId, true)
        updateAllSelectors()
    }
}