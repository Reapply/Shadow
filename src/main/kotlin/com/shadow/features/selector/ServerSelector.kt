package com.shadow.features.selector

import com.shadow.Shadow
import com.shadow.config.ConfigManager
import com.shadow.domain.QueueResult
import com.shadow.domain.ServerInfo
import com.shadow.utils.SafeItemBuilder
import com.shadow.utils.ShadowUtils
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

/**
 * Manages server selection and queuing functionality for a multi-server network.
 * Provides a GUI-based server selector and handles queue management for server transfers.
 */
object ServerSelector : Listener {
    // Queue management
    private val queues: Map<String, LinkedBlockingQueue<UUID>> by lazy {
        ConfigManager.serverList.associate { it.id to LinkedBlockingQueue<UUID>() }
    }

    // Tracks how long players have been in queues
    private val queueTimes = ConcurrentHashMap<UUID, Long>()

    // Tracks which server queues are currently disabled
    private val disabledQueues = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val selectorItem by lazy {
        try {
            createSelectorItem()
        } catch (e: Exception) {
            Shadow.instance.logger.severe("Failed to create selector item: ${e.message}")
            e.printStackTrace()

            // Fallback to a simple item
            SafeItemBuilder(
                material = Material.COMPASS,
                name = ShadowUtils.parseMessage("<dark_purple>Server Selector")
            ).apply {
                customModelData = 1001
            }
        }
    }

    /**
     * Initializes the ServerSelector system.
     * Sets up queues, registers event handlers, and starts queue processing.
     */
    fun init(plugin: Shadow) {
        loadDisabledQueues()
        plugin.server.pluginManager.registerEvents(this, plugin)
        startQueueProcessor(plugin)

        plugin.getCommand("queue")?.setExecutor(QueueCommand())
        plugin.getCommand("queue")?.tabCompleter = QueueTabCompleter()
    }

    /**
     * Loads the initial state of disabled queues from configuration
     */
    private fun loadDisabledQueues() {
        ConfigManager.serverList.forEach { server ->
            if (!Shadow.instance.config.getBoolean("servers.${server.id}.enabled", true)) {
                disabledQueues.add(server.id)
            }
        }
    }

    /**
     * Creates the item used to open the server selector GUI
     */
    private fun createSelectorItem(): SafeItemBuilder {
        try {
            // Get the material safely
            val materialName = ConfigManager.selectorConfig.material.uppercase()
            val material = try {
                Material.valueOf(materialName)
            } catch (e: Exception) {
                Shadow.instance.logger.warning("Invalid material name: $materialName, using COMPASS instead")
                Material.COMPASS
            }

            return SafeItemBuilder(
                material = material,
                name = ShadowUtils.parseMessage(ConfigManager.selectorConfig.name)
            ).apply {
                customModelData = 1001
            }
        } catch (e: Exception) {
            Shadow.instance.logger.severe("Error in createSelectorItem: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Creates an ItemStack representing a server in the selector GUI
     * Includes server name, description, and current queue status
     */
    private fun createServerItem(server: ServerInfo): ItemStack {
        val description = Shadow.instance.config.getStringList("servers.${server.id}.description").toMutableList()
        val statusIndex = description.indexOfFirst { it.contains("Queue Status") }

        if (statusIndex != -1) {
            description[statusIndex] = "<gray>Queue Status: ${getQueueStatus(server.id)}"
        }

        val material = try {
            Material.valueOf(server.material.uppercase())
        } catch (e: Exception) {
            Shadow.instance.logger.warning("Invalid material for server ${server.id}: ${server.material}. Using STONE instead.")
            Material.STONE
        }

        return SafeItemBuilder(
            material = material,
            name = ShadowUtils.parseMessage(server.displayName)
        ).apply {
            setLore(description.map { ShadowUtils.parseMessage(it) }.toMutableList())
        }.build()
    }

    /**
     * Returns the current status of a server's queue (Open/Disabled)
     */
    private fun getQueueStatus(serverId: String): String =
        if (disabledQueues.contains(serverId)) "<red>Disabled" else "<green>Open"

    /**
     * Updates all open server selector GUIs to reflect current queue statuses
     */
    fun updateAllSelectors() {
        Bukkit.getOnlinePlayers()
            .filter { it.openInventory.title() == ShadowUtils.parseMessage(ConfigManager.selectorConfig.guiTitle) }
            .forEach { player ->
                val inventory = player.openInventory.topInventory
                ConfigManager.serverList.forEach { server ->
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
            ConfigManager.selectorConfig.guiSize,
            ShadowUtils.parseMessage(ConfigManager.selectorConfig.guiTitle)
        )

        // Fill background if enabled
        if (ConfigManager.selectorConfig.useBackground) {
            val bgMaterial = try {
                Material.valueOf(ConfigManager.selectorConfig.backgroundMaterial)
            } catch (e: Exception) {
                Material.BLACK_STAINED_GLASS_PANE
            }

            val bgItem = SafeItemBuilder(
                material = bgMaterial,
                name = ShadowUtils.parseMessage(" ")
            ).build()

            for (i in 0 until ConfigManager.selectorConfig.guiSize) {
                inventory.setItem(i, bgItem)
            }
        }

        // Add server items
        ConfigManager.serverList.forEach { server ->
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
            ShadowUtils.sendMessage(player, ConfigManager.queueConfig.messages.disabled)
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

        val server = ConfigManager.serverList.find { it.id == serverId }
        ShadowUtils.sendMessage(
            player,
            ConfigManager.queueConfig.messages.joined.replace("%server%", server?.displayName ?: serverId)
        )
        ShadowUtils.playSound(player, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f)

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
     * Gives the selector item to a player
     */
    fun giveSelectorItem(player: Player) {
        try {
            player.inventory.setItem(ConfigManager.selectorConfig.slot, selectorItem.build())
        } catch (e: Exception) {
            Shadow.instance.logger.severe("Failed to give selector item: ${e.message}")
            // Fallback to a simple item if the builder fails
            val item = ItemStack(Material.COMPASS)
            val meta = item.itemMeta
            if (meta != null) {
                meta.displayName(ShadowUtils.parseMessage("<dark_purple>Server Selector"))
                meta.setCustomModelData(1001)
                item.itemMeta = meta
            }
            player.inventory.setItem(ConfigManager.selectorConfig.slot, item)
        }
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
                    val serverName = ConfigManager.serverList.find { it.id == serverId }?.displayName ?: serverId

                    player.sendActionBar(
                        ShadowUtils.parseMessage(
                            "<pink>In Queue: $serverName | Position: $position/$queueSize | Time: ${ShadowUtils.formatTime(timeInQueue)}</pink>"
                        )
                    )
                }
            }
        }, 20L, 20L)
    }

    /**
     * Give selector item on join
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        giveSelectorItem(event.player)
    }

    /**
     * Prevent dropping selector item
     */
    @EventHandler
    fun onItemDrop(event: PlayerDropItemEvent) {
        val meta = event.itemDrop.itemStack.itemMeta
        if (meta?.hasCustomModelData() == true && meta.customModelData == 1001) {
            event.isCancelled = true
        }
    }

    /**
     * Handle right-click to open selector
     */
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action == Action.RIGHT_CLICK_AIR ||
            event.action == Action.RIGHT_CLICK_BLOCK) {
            val meta = event.item?.itemMeta
            if (meta?.hasCustomModelData() == true && meta.customModelData == 1001) {
                event.isCancelled = true
                openSelector(event.player)
            }
        }
    }

    /**
     * Handle clicks in selector GUI
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() == ShadowUtils.parseMessage(ConfigManager.selectorConfig.guiTitle)) {
            event.isCancelled = true

            if (event.whoClicked !is Player) return
            val player = event.whoClicked as Player

            ConfigManager.serverList.find { it.slot == event.slot }?.let { server ->
                player.closeInventory()
                addToQueue(player, server.id)
            }
        }
    }

    /**
     * Remove from queues on disconnect
     */
    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        removeFromAllQueues(event.player)
    }

    /**
     * Disables a server's queue and updates the selector GUI
     */
    fun disableQueue(serverId: String) {
        disabledQueues.add(serverId)
        ConfigManager.updateQueueStatus(serverId, false)
        updateAllSelectors()
    }

    /**
     * Enables a server's queue and updates the selector GUI
     */
    fun enableQueue(serverId: String) {
        disabledQueues.remove(serverId)
        ConfigManager.updateQueueStatus(serverId, true)
        updateAllSelectors()
    }
}