package com.shadow.features

import com.shadow.Shadow
import com.shadow.config.ConfigManager
import com.shadow.utils.SafeItemBuilder
import com.shadow.utils.ShadowUtils
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.EnderPearl
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.ProjectileLaunchEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.metadata.FixedMetadataValue

/**
 * EnderButt feature that allows players to ride ender pearls
 * and returns the ender pearl to the player after teleportation
 */
object EnderButtFeature : Listener {
    private const val ENDER_BUTT_META_KEY = "shadow_ender_butt"
    private const val ENDER_BUTT_SLOT = 1
    private var enabled = true
    private lateinit var enderButtItem: ItemStack

    /**
     * Initialize the EnderButt feature
     */
    fun init() {
        loadConfig()

        if (enabled) {
            Shadow.instance.server.pluginManager.registerEvents(this, Shadow.instance)
            Shadow.instance.logger.info("EnderButt feature enabled")
        } else {
            Shadow.instance.logger.info("EnderButt feature is disabled in config")
        }
    }

    /**
     * Load EnderButt configuration from config.yml
     */
    private fun loadConfig() {
        val config = Shadow.instance.config

        // Check if EnderButt is enabled
        enabled = config.getBoolean("enderbutt.enabled", true)
        if (!enabled) return

        // Create the EnderButt item
        val name = config.getString("enderbutt.name") ?: "<#9AABBD>Ender Butt"
        val lore = config.getStringList("enderbutt.lore").ifEmpty {
            listOf(
                "<gray>Throw to ride the ender pearl!",
                "<gray>Wheeeeeee!"
            )
        }

        enderButtItem = SafeItemBuilder(Material.ENDER_PEARL)
            .setName(ShadowUtils.parseMessage(name))
            .setLore(lore.map { ShadowUtils.parseMessage(it) }.toMutableList())
            .build()
    }

    /**
     * Give the EnderButt item to a player
     */
    fun giveEnderButtItem(player: Player) {
        if (!enabled) return

        player.inventory.setItem(ENDER_BUTT_SLOT, enderButtItem.clone())
    }

    /**
     * Handle ender pearl throws
     */
    @EventHandler
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        val projectile = event.entity

        // Check if the projectile is an ender pearl thrown by a player
        if (projectile !is EnderPearl || projectile.shooter !is Player) return

        val player = projectile.shooter as Player

        // Mark this ender pearl as an EnderButt
        projectile.setMetadata(ENDER_BUTT_META_KEY, FixedMetadataValue(Shadow.instance, true))

        // Make the player ride the ender pearl
        projectile.addPassenger(player)

        // Play a sound effect
        ShadowUtils.playSound(player, Sound.ENTITY_ENDER_PEARL_THROW, 0.5f, 0.8f)
    }

    /**
     * Handle player teleportation by ender pearl
     */
    @EventHandler
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        // Check if this is an ender pearl teleport
        if (event.cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return

        val player = event.player

        // Give the player a new ender pearl
        player.inventory.setItem(ENDER_BUTT_SLOT, enderButtItem.clone())

        // Play a sound effect
        ShadowUtils.playSound(player, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1f)
    }
}
