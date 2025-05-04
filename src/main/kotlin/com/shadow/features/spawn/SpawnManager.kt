package com.shadow.features.spawn

import com.shadow.Shadow
import com.shadow.config.ConfigManager
import com.shadow.utils.ShadowUtils
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent

/**
 * Manages the server spawn location and related functionality
 */
object SpawnManager : Listener {
    /**
     * Initialize the spawn manager
     */
    fun init(plugin: Shadow) {
        // Register events
        plugin.server.pluginManager.registerEvents(this, plugin)

        // Register command
        plugin.getCommand("setspawn")?.setExecutor(SetSpawnCommand())
    }

    /**
     * Teleport to spawn on join is now handled by PlayerListener
     * to prevent multiple teleports causing "invalid move packet error"
     */
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        // Teleport is now handled by PlayerListener with a delay
    }

    /**
     * Teleport to spawn on respawn
     */
    @EventHandler
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        ConfigManager.getSpawnLocation()?.let { spawn ->
            event.player.teleport(spawn)
        }
    }

    /**
     * Teleport a player to spawn
     */
    fun teleportToSpawn(player: Player) {
        ConfigManager.getSpawnLocation()?.let { spawn ->
            player.teleport(spawn)
        }
    }

    /**
     * Set the spawn location
     */
    fun setSpawn(location: Location) {
        ConfigManager.saveSpawn(location)
    }
}

/**
 * Command to set the server spawn location
 */
class SetSpawnCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(ShadowUtils.parseMessage("<red>Only players can use this command!"))
            return true
        }

        if (!sender.hasPermission("shadow.setspawn")) {
            sender.sendMessage(ShadowUtils.parseMessage("<red>You don't have permission to use this command!"))
            return true
        }

        SpawnManager.setSpawn(sender.location)
        sender.sendMessage(ShadowUtils.parseMessage("<green>Spawn location has been set!"))
        ShadowUtils.playSound(sender, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.5f)

        return true
    }
}
