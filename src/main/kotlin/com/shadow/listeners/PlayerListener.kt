package com.shadow.listeners

import com.shadow.Shadow
import com.shadow.features.spawn.SpawnManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent

/**
 * Handles player-related events for the hub server
 */
class PlayerListener : Listener {
    private companion object {
        const val SPAWN_TELEPORT_DELAY = 20L // 1 second delay
        const val SEPARATOR_LENGTH = 30

    }

    private val mm = MiniMessage.miniMessage()

    private val welcomeMessage by lazy {
        listOf(
            "",
            "<bold><gold>● WELCOME TO ABSIDIEN NETWORK ●</gold></bold>",
            "<bold><white>━━━━━━━━━━━━━━━━━━━━━━━</white></bold>",
            "<white>The home of PvP</white>",
            ""
        ).map { mm.deserialize(it) }
    }

    /**
     * Join event
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        event.joinMessage(null) // Remove default join message
        Shadow.instance.setPlayerHubState(event.player)
        scheduleSpawnTeleport(event.player)
        sendWelcomeMessage(event.player)
    }

    /**
     * Disable item dropping for non-creative players
     */
    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        if (event.player.gameMode != GameMode.CREATIVE) {
            event.isCancelled = true
        }
    }

    /**
     * Reset player state on respawn
     */
    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        Shadow.instance.setPlayerHubState(event.player)
    }

    /**
     * Disable all damage
     */
    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        event.isCancelled = true
    }

    /**
     * Disable hunger
     */
    @EventHandler
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        event.isCancelled = true
    }

    /**
     * Schedule teleport to spawn with a slight delay
     */
    private fun scheduleSpawnTeleport(player: Player) {
        Shadow.instance.server.scheduler.runTaskLater(
            Shadow.instance,
            Runnable { SpawnManager.teleportToSpawn(player) },
            SPAWN_TELEPORT_DELAY
        )
    }

    /**
     * Send welcome message to player
     */
    private fun sendWelcomeMessage(player: Player) {
        welcomeMessage.forEach(player::sendMessage)
    }
}