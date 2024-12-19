package com.shadow.features.selector

import com.shadow.Shadow
import com.shadow.domain.*
import gg.flyte.twilight.builders.item.ItemBuilder
import gg.flyte.twilight.event.event
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
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

object ServerSelector {
    private val mm = MiniMessage.miniMessage()
    private val config: YamlConfiguration by lazy {
        YamlConfiguration.loadConfiguration(File(Shadow.instance.dataFolder, "config.yml"))
    }

    private val selectorConfig: SelectorConfig by lazy {
        SelectorConfig.fromConfig(config)
    }

    private val queueConfig: QueueConfig by lazy {
        QueueConfig.fromConfig(config)
    }

    val serverList: List<ServerInfo> by lazy {
        buildList {
            config.getConfigurationSection("servers")?.getKeys(false)?.forEach { key ->
                add(ServerInfo.fromConfig(key, config))
            }
        }
    }

    private val queues: Map<String, LinkedBlockingQueue<UUID>> by lazy {
        serverList.associate { it.id to LinkedBlockingQueue<UUID>() }
    }

    private val queueTimes = ConcurrentHashMap<UUID, Long>()
    private val disabledQueues = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    private val selectorItem by lazy {
        createSelectorItem()
    }

    fun init(plugin: Shadow) {
        loadDisabledQueues()
        registerEvents()
        startQueueProcessor(plugin)

        // Register commands
        plugin.getCommand("queue")?.setExecutor(QueueCommand())
        plugin.getCommand("queue")?.tabCompleter = QueueTabCompleter()
    }

    private fun loadDisabledQueues() {
        serverList.forEach { server ->
            if (!config.getBoolean("servers.${server.id}.enabled", true)) {
                disabledQueues.add(server.id)
            }
        }
    }

    private fun createSelectorItem(): ItemBuilder =
        ItemBuilder(
            Material.valueOf(selectorConfig.material),
            name = mm.deserialize(selectorConfig.name)
        ).apply {
            customModelData = 1001
        }

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

    private fun getQueueStatus(serverId: String): String =
        if (disabledQueues.contains(serverId)) "<red>Disabled" else "<green>Open"

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

    fun openSelector(player: Player) {
        val inventory = Bukkit.createInventory(
            null,
            selectorConfig.guiSize,
            mm.deserialize(selectorConfig.guiTitle)
        )

        if (selectorConfig.useBackground) {
            val bgItem = ItemBuilder(
                Material.valueOf(selectorConfig.backgroundMaterial),
                name = mm.deserialize(" ")
            ).build()

            for (i in 0 until selectorConfig.guiSize) {
                inventory.setItem(i, bgItem)
            }
        }

        serverList.forEach { server ->
            inventory.setItem(server.slot, createServerItem(server))
        }

        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 1f, 1.4f)
        player.openInventory(inventory)
    }

    fun addToQueue(player: Player, serverId: String): QueueResult {
        if (disabledQueues.contains(serverId)) {
            player.sendMessage(mm.deserialize(queueConfig.messages.disabled))
            return QueueResult.Disabled
        }

        val queue = queues[serverId] ?: return QueueResult.Error("Invalid server")

        if (queue.contains(player.uniqueId)) {
            return QueueResult.AlreadyInQueue
        }

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

    fun removeFromAllQueues(player: Player) {
        queues.values.forEach { it.remove(player.uniqueId) }
        queueTimes.remove(player.uniqueId)
    }

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

    private fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            else -> "${seconds / 60}m ${seconds % 60}s"
        }
    }

    fun giveSelectorItem(player: Player) {
        player.inventory.setItem(selectorConfig.slot, selectorItem.build())
    }

    private fun sendToServer(player: Player, server: String) {
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeUTF("Connect")
                out.writeUTF(server)
                player.sendPluginMessage(Shadow.instance, "BungeeCord", bytes.toByteArray())
            }
        }
    }

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

        // Update action bars
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

    private fun registerEvents() {
        event<PlayerJoinEvent> {
            giveSelectorItem(player)
        }

        event<PlayerDropItemEvent> {
            if (itemDrop.itemStack.itemMeta?.hasCustomModelData() == true &&
                itemDrop.itemStack.itemMeta?.customModelData == 1001) {
                isCancelled = true
            }
        }

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

        event<PlayerQuitEvent> {
            removeFromAllQueues(player)
        }
    }

    fun disableQueue(serverId: String) {
        disabledQueues.add(serverId)
        updateQueueStatusInConfig(serverId, false)
        updateAllSelectors()
    }

    fun enableQueue(serverId: String) {
        disabledQueues.remove(serverId)
        updateQueueStatusInConfig(serverId, true)
        updateAllSelectors()
    }
}