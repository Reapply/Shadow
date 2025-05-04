package com.shadow.features.autograph

import com.shadow.Shadow
import com.shadow.config.ConfigManager
import com.shadow.utils.PlayerHeadUtils
import com.shadow.utils.SafeItemBuilder
import com.shadow.utils.ShadowUtils
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.server.PluginDisableEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.inventory.meta.SkullMeta
import java.util.*
import java.text.SimpleDateFormat

/**
 * Autograph Book feature that allows players to collect autographs from other players
 */
object AutographBook : Listener {
    private const val AUTOGRAPH_BOOK_SLOT = 2
    private var enabled = true
    private lateinit var autographBookItem: ItemStack

    // GUI settings
    private var guiTitle = "<gold>Autograph Collection"
    private var guiSize = 54
    private var backgroundMaterial = Material.BLACK_STAINED_GLASS_PANE
    private var headerMaterial = Material.WRITABLE_BOOK

    // Cache for autograph requests to avoid frequent database queries
    private val autographRequests = HashMap<UUID, UUID>() // requester UUID -> target UUID

    /**
     * Initialize the Autograph Book feature
     */
    fun init() {
        loadConfig()

        if (enabled) {
            // Initialize the database
            AutographDatabase.init()

            // Load autograph requests from the database
            autographRequests.clear()
            autographRequests.putAll(AutographDatabase.getAutographRequests())

            Shadow.instance.server.pluginManager.registerEvents(this, Shadow.instance)
            Shadow.instance.logger.info("Autograph Book feature enabled")
        } else {
            Shadow.instance.logger.info("Autograph Book feature is disabled in config")
        }
    }

    /**
     * Handle plugin disable event to close the database connection
     */
    @EventHandler
    fun onPluginDisable(event: PluginDisableEvent) {
        if (event.plugin == Shadow.instance) {
            AutographDatabase.close()
            Shadow.instance.logger.info("Autograph Book database closed")
        }
    }

    /**
     * Load Autograph Book configuration from config.yml
     */
    private fun loadConfig() {
        val config = Shadow.instance.config

        // Check if Autograph Book is enabled
        enabled = config.getBoolean("autographbook.enabled", true)
        if (!enabled) return

        // Create the Autograph Book item
        val name = config.getString("autographbook.name") ?: "<#9AABBD>Autograph Book"
        val lore = config.getStringList("autographbook.lore").ifEmpty {
            listOf(
                "<gray>Collect autographs from other players!",
                "<gray>Right-click to view your collection.",
                "<gray>Left-click a player to request their autograph."
            )
        }

        autographBookItem = SafeItemBuilder(Material.WRITABLE_BOOK)
            .setName(ShadowUtils.parseMessage(name))
            .setLore(lore.map { ShadowUtils.parseMessage(it) }.toMutableList())
            .build()

        // Load GUI settings
        guiTitle = config.getString("autographbook.gui.title") ?: "<gold>Autograph Collection"
        guiSize = config.getInt("autographbook.gui.size", 54)

        // Ensure GUI size is valid (multiple of 9 and <= 54)
        if (guiSize % 9 != 0 || guiSize > 54) {
            Shadow.instance.logger.warning("Invalid GUI size for autograph book: $guiSize. Using default size of 54.")
            guiSize = 54
        }

        // Load materials
        val bgMaterialStr = config.getString("autographbook.gui.background-material") ?: "BLACK_STAINED_GLASS_PANE"
        backgroundMaterial = runCatching {
            Material.valueOf(bgMaterialStr.uppercase())
        }.getOrElse {
            Shadow.instance.logger.warning("Invalid background material for autograph book: $bgMaterialStr. Using default.")
            Material.BLACK_STAINED_GLASS_PANE
        }

        val headerMaterialStr = config.getString("autographbook.gui.header-material") ?: "WRITABLE_BOOK"
        headerMaterial = runCatching {
            Material.valueOf(headerMaterialStr.uppercase())
        }.getOrElse {
            Shadow.instance.logger.warning("Invalid header material for autograph book: $headerMaterialStr. Using default.")
            Material.WRITABLE_BOOK
        }
    }

    /**
     * Give the Autograph Book item to a player
     */
    fun giveAutographBookItem(player: Player) {
        if (!enabled) return

        player.inventory.setItem(AUTOGRAPH_BOOK_SLOT, autographBookItem.clone())
    }

    /**
     * Handle player interactions with the autograph book
     */
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        if (item.type != Material.WRITABLE_BOOK && item.type != Material.WRITTEN_BOOK) return

        val meta = item.itemMeta ?: return
        if (meta.displayName() != autographBookItem.itemMeta?.displayName()) return

        // Right-click to open the book
        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            event.isCancelled = true
            openAutographBook(player)
        }
    }

    /**
     * Handle player interactions with other players while holding the autograph book
     */
    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand

        // Check if the player is holding the autograph book
        if (item.type != Material.WRITABLE_BOOK && item.type != Material.WRITTEN_BOOK) return

        val meta = item.itemMeta ?: return
        if (meta.displayName() != autographBookItem.itemMeta?.displayName()) return

        // Check if the entity is a player
        val target = event.rightClicked
        if (target !is Player) return

        // Prevent the player from interacting with themselves
        if (target == player) return

        // Request an autograph from the target player
        requestAutograph(player, target)

        // Cancel the event to prevent any other interactions
        event.isCancelled = true
    }

    /**
     * Open the autograph book GUI for a player
     */
    private fun openAutographBook(player: Player) {
        // Create inventory for the GUI
        val inventory = Bukkit.createInventory(
            null,
            guiSize,
            ShadowUtils.parseMessage(guiTitle)
        )

        // Create background item
        val backgroundItem = SafeItemBuilder(
            material = backgroundMaterial,
            name = ShadowUtils.parseMessage(" ")
        ).build()

        // Fill background
        for (i in 0 until guiSize) {
            inventory.setItem(i, backgroundItem)
        }

        // Add header item (book)
        val headerItem = SafeItemBuilder(
            material = headerMaterial,
            name = ShadowUtils.parseMessage(guiTitle)
        ).apply {
            setLore(mutableListOf(
                ShadowUtils.parseMessage("<gray>Owner: <white>${player.name}</white>"),
                ShadowUtils.parseMessage("<gray>"),
                ShadowUtils.parseMessage("<gray>Collect autographs from players across the Absidien Network!"),
                ShadowUtils.parseMessage("<gray>Left-click on a player while holding this book to request their autograph.")
            ))
        }.build()

        inventory.setItem(4, headerItem)

        // Add autograph items
        val autographs = getPlayerAutographs(player)
        var slot = 19 // Start in the third row

        for (autograph in autographs) {
            if (slot >= 54) break // Don't exceed inventory size

            // Parse the autograph to extract player name
            val nameMatch = "Signed by: <white>(.*?)</white>".toRegex().find(autograph)
            val playerName = nameMatch?.groupValues?.get(1) ?: "Unknown Player"

            // Parse the date
            val dateMatch = "Date: <white>(.*?)</white>".toRegex().find(autograph)
            val date = dateMatch?.groupValues?.get(1) ?: "Unknown Date"

            // Create player head item with proper skin support for offline mode servers
            val headItem = PlayerHeadUtils.createPlayerHead(playerName)
            val meta = headItem.itemMeta as? SkullMeta

            if (meta != null) {
                meta.displayName(ShadowUtils.parseMessage("<gold>$playerName's Autograph"))

                val lore = mutableListOf(
                    ShadowUtils.parseMessage("<gray>\"Thanks for being a fan!\""),
                    ShadowUtils.parseMessage("<gray>"),
                    ShadowUtils.parseMessage("<gray>Date: <white>$date</white>")
                )

                meta.lore(lore)
                headItem.itemMeta = meta
            }

            inventory.setItem(slot, headItem)
            slot++

            // Skip to next row if we're at the end of the current row
            if (slot % 9 == 8) {
                slot += 2
            }
        }

        player.openInventory(inventory)
        ShadowUtils.playSound(player, Sound.BLOCK_CHEST_OPEN, 0.5f, 1.4f)
    }

    /**
     * Get the autographs for a player
     */
    private fun getPlayerAutographs(player: Player): List<String> {
        // Return the player's autographs from the database
        return AutographDatabase.getPlayerAutographs(player.uniqueId)
    }

    /**
     * Request an autograph from a player
     */
    fun requestAutograph(requester: Player, target: Player) {
        if (!enabled) return

        // Check if the requester is holding the autograph book
        val item = requester.inventory.itemInMainHand
        if (item.type != Material.WRITABLE_BOOK && item.type != Material.WRITTEN_BOOK) return

        val meta = item.itemMeta ?: return
        if (meta.displayName() != autographBookItem.itemMeta?.displayName()) return

        // Check if the requester already has a pending request
        if (autographRequests.containsKey(requester.uniqueId)) {
            ShadowUtils.sendMessage(requester, "<red>You already have a pending autograph request!")
            return
        }

        // Check if the requester already has an autograph from this player
        if (AutographDatabase.hasAutographFromSigner(requester.uniqueId, target.name)) {
            ShadowUtils.sendMessage(requester, "<red>You already have an autograph from ${target.name}!")
            return
        }

        // Store the request in both the database and local cache
        val success = AutographDatabase.storeAutographRequest(requester.uniqueId, target.uniqueId)
        if (!success) {
            ShadowUtils.sendMessage(requester, "<red>Failed to store autograph request. Please try again later.")
            return
        }

        // Update local cache
        autographRequests[requester.uniqueId] = target.uniqueId

        // Notify the players
        ShadowUtils.sendMessage(requester, "<green>You requested an autograph from ${target.name}!")
        ShadowUtils.sendMessage(target, "<green>${requester.name} would like your autograph! Type /autograph accept to sign their book.")

        // Play sounds
        ShadowUtils.playSound(requester, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1f)
        ShadowUtils.playSound(target, Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 1f)
    }

    /**
     * Accept an autograph request
     */
    fun acceptAutograph(player: Player) {
        if (!enabled) return

        // Find the request where this player is the target
        val requesterEntry = autographRequests.entries.find { it.value == player.uniqueId }

        if (requesterEntry == null) {
            ShadowUtils.sendMessage(player, "<red>You don't have any pending autograph requests!")
            return
        }

        val requesterUuid = requesterEntry.key
        val requester = Bukkit.getPlayer(requesterUuid)

        if (requester == null || !requester.isOnline) {
            ShadowUtils.sendMessage(player, "<red>The player who requested your autograph is no longer online!")

            // Remove the request from both database and local cache
            AutographDatabase.removeAutographRequest(requesterUuid)
            autographRequests.remove(requesterUuid)
            return
        }

        // Add the autograph to the requester's book
        addAutograph(requester, player)

        // Remove the request from both database and local cache
        val success = AutographDatabase.removeAutographRequest(requesterUuid)
        if (!success) {
            Shadow.instance.logger.warning("Failed to remove autograph request from database for ${requesterUuid}")
        }

        autographRequests.remove(requesterUuid)

        // Notify the players
        ShadowUtils.sendMessage(player, "<green>You gave your autograph to ${requester.name}!")
        ShadowUtils.sendMessage(requester, "<green>${player.name} signed your autograph book!")

        // Play sounds
        ShadowUtils.playSound(player, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1f)
        ShadowUtils.playSound(requester, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1f)
    }

    /**
     * Decline an autograph request
     */
    fun declineAutograph(player: Player) {
        if (!enabled) return

        // Find the request where this player is the target
        val requesterEntry = autographRequests.entries.find { it.value == player.uniqueId }

        if (requesterEntry == null) {
            ShadowUtils.sendMessage(player, "<red>You don't have any pending autograph requests!")
            return
        }

        val requesterUuid = requesterEntry.key
        val requester = Bukkit.getPlayer(requesterUuid)

        // Remove the request from both database and local cache
        val success = AutographDatabase.removeAutographRequest(requesterUuid)
        if (!success) {
            Shadow.instance.logger.warning("Failed to remove autograph request from database for ${requesterUuid}")
        }

        autographRequests.remove(requesterUuid)

        // Notify the players
        ShadowUtils.sendMessage(player, "<yellow>You declined the autograph request.")

        if (requester != null && requester.isOnline) {
            ShadowUtils.sendMessage(requester, "<yellow>${player.name} declined to sign your autograph book.")
            ShadowUtils.playSound(requester, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1f)
        }

        ShadowUtils.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 1f)
    }

    /**
     * Add an autograph to a player's collection
     */
    private fun addAutograph(player: Player, signer: Player) {
        // Get the current date
        val dateFormat = SimpleDateFormat("MM/dd/yyyy")
        val currentDate = dateFormat.format(Date())

        // Create the autograph page (for backward compatibility with book format)
        val autograph = "<dark_purple><bold>AUTOGRAPH</bold></dark_purple>\n\n" +
                "<gold>Signed by: <white>${signer.name}</white></gold>\n\n" +
                "<gray>\"Thanks for being a fan!\"</gray>\n\n" +
                "<gray>Date: <white>${currentDate}</white></gray>"

        // Store the autograph in the database
        val success = AutographDatabase.storeAutograph(
            playerUuid = player.uniqueId,
            autographText = autograph,
            signerName = signer.name,
            date = currentDate
        )

        if (!success) {
            Shadow.instance.logger.warning("Failed to store autograph from ${signer.name} for ${player.name}")
        }
    }

    /**
     * Handle clicks in the autograph GUI
     */
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() == ShadowUtils.parseMessage(guiTitle)) {
            event.isCancelled = true
            // Currently just preventing clicks, no additional functionality needed
        }
    }
}
