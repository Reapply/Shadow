package com.shadow.features.selector

import com.shadow.Shadow
import gg.flyte.twilight.builders.item.ItemBuilder
import gg.flyte.twilight.event.event
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemFlag
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

object ServerSelector {
    private lateinit var config: YamlConfiguration
    private lateinit var selectorItem: ItemBuilder
    private val serverList = mutableListOf<ServerInfo>()
    private val mm = MiniMessage.miniMessage()

    fun init(plugin: Shadow) {
        loadConfig(plugin)
        registerEvents()
        createSelectorItem()
    }

    private fun loadConfig(plugin: Shadow) {
        val configFile = File(plugin.dataFolder, "config.yml")
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(configFile)

        // Load servers from config
        config.getConfigurationSection("servers")?.getKeys(false)?.forEach { key ->
            val section = config.getConfigurationSection("servers.$key")!!
            serverList.add(
                ServerInfo(
                    key,
                    section.getString("display-name") ?: "<dark_purple>$key",
                    section.getString("material") ?: "STONE",
                    section.getInt("slot"),
                    section.getStringList("description"),
                    section.getString("command") ?: "server $key",
                    section.getString("server-id") ?: key  // Use server-id from config or fall back to key
                )
            )
        }
    }

    private fun registerEvents() {
        // Give selector on join
        event<PlayerJoinEvent> {
            giveSelectorItem(player)
        }

        // Prevent dropping
        event<PlayerDropItemEvent> {
            if (itemDrop.itemStack.itemMeta?.hasCustomModelData() == true &&
                itemDrop.itemStack.itemMeta?.customModelData == 1001) {
                isCancelled = true
            }
        }

        // Handle compass interaction
        event<PlayerInteractEvent> {
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK ||
                action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {

                if (item?.itemMeta?.hasCustomModelData() == true &&
                    item?.itemMeta?.customModelData == 1001) {
                    isCancelled = true
                    openSelector(player)
                }
            }
        }

        // Handle inventory clicks
        event<InventoryClickEvent> {
            val title = view.title()
            if (title == mm.deserialize(config.getString("gui.title") ?: "<dark_purple>Server Selector")) {
                isCancelled = true

                if (whoClicked !is Player) return@event
                val player = whoClicked as Player

                serverList.find { it.slot == slot }?.let { server ->
                    player.closeInventory()
                    player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
                    sendToServer(player, server.serverId)
                }
            }
        }
    }

    private fun sendToServer(player: Player, server: String) {
        val byteArray = ByteArrayOutputStream()
        val out = DataOutputStream(byteArray)

        try {
            out.writeUTF("Connect")
            out.writeUTF(server)
        } catch (e: IOException) {
            e.printStackTrace()
        }

        player.sendPluginMessage(Shadow.instance, "BungeeCord", byteArray.toByteArray())
    }

    private fun createSelectorItem() {
        val builder = ItemBuilder(
            Material.valueOf(config.getString("selector.material") ?: "COMPASS"),
            name = mm.deserialize(config.getString("selector.name") ?: "<dark_purple>Server Selector")
        ).apply {
            customModelData = 1001
        }

        val selectorItemStack = builder.build()
        selectorItemStack.itemMeta = selectorItemStack.itemMeta?.apply {
            addItemFlags(
                ItemFlag.HIDE_ATTRIBUTES,
                ItemFlag.HIDE_DYE,
                ItemFlag.HIDE_UNBREAKABLE,
                ItemFlag.HIDE_ENCHANTS,
                ItemFlag.HIDE_PLACED_ON,
                ItemFlag.HIDE_DESTROYS
            )
        }

        // Create new ItemBuilder with the modified itemStack's properties
        selectorItem = ItemBuilder(
            type = selectorItemStack.type,
            name = selectorItemStack.itemMeta?.displayName(),
            customModelData = selectorItemStack.itemMeta?.customModelData
        )
    }

    fun giveSelectorItem(player: Player) {
        val slot = config.getInt("selector.slot", 4)
        player.inventory.setItem(slot, selectorItem.build())
    }

    fun openSelector(player: Player) {
        val guiSize = config.getInt("gui.size", 27)
        val inventory = Bukkit.createInventory(
            null,
            guiSize,
            mm.deserialize(config.getString("gui.title") ?: "<dark_purple>Server Selector")
        )

        // Set background glass if configured
        if (config.getBoolean("gui.use-background", true)) {
            val bgMaterial = Material.valueOf(config.getString("gui.background-material") ?: "BLACK_STAINED_GLASS_PANE")
            val bgItem = ItemBuilder(bgMaterial, name = mm.deserialize(" ")).build()
            for (i in 0 until guiSize) {
                inventory.setItem(i, bgItem)
            }
        }

// Add server items in openSelector()
        serverList.forEach { server ->
            val item = ItemBuilder(
                Material.valueOf(server.material),
                name = mm.deserialize(server.displayName)
            ).apply {
                if (server.description.isNotEmpty()) {
                    lore = server.description.map { line ->
                        mm.deserialize(line)
                    }.toMutableList()
                }
            }

            val itemStack = item.build()
            // Apply the item flags
            itemStack.itemMeta = itemStack.itemMeta?.apply {
                addItemFlags(
                    ItemFlag.HIDE_ATTRIBUTES,
                    ItemFlag.HIDE_DYE,
                    ItemFlag.HIDE_UNBREAKABLE,
                    ItemFlag.HIDE_ENCHANTS,
                    ItemFlag.HIDE_PLACED_ON,
                    ItemFlag.HIDE_DESTROYS
                )
            }

            // Now use the modified itemStack
            inventory.setItem(server.slot, itemStack)
        }

        // Open inventory with sound effect
        player.playSound(player.location, Sound.BLOCK_CHEST_OPEN, 1f, 1.4f)
        player.openInventory(inventory)
    }
}

data class ServerInfo(
    val id: String,
    val displayName: String,
    val material: String,
    val slot: Int,
    val description: List<String>,
    val command: String,
    val serverId: String  // Add this field
)