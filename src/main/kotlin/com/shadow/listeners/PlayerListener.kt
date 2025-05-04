package com.shadow.listeners

import com.shadow.Shadow
import com.shadow.config.ConfigManager
import com.shadow.features.spawn.SpawnManager
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.LeavesDecayEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.*
import org.bukkit.event.weather.LightningStrikeEvent

class PlayerListener : Listener {

    private val mm = MiniMessage.miniMessage()
    private val config = Shadow.instance.config
    private val welcomeMessages = config.getStringList("player.welcome-message").map(mm::deserialize)
    private val spawnTeleportDelay = config.getLong("player.spawn-teleport-delay", 20L)

    @EventHandler fun onJoin(e: PlayerJoinEvent) {
        e.joinMessage(null)
        val player = e.player
        Shadow.instance.setPlayerHubState(player)
        Shadow.instance.server.scheduler.runTaskLater(Shadow.instance, Runnable {
            SpawnManager.teleportToSpawn(player)
            welcomeMessages.forEach(player::sendMessage)
        }, spawnTeleportDelay)
    }

    @EventHandler fun onQuit(e: PlayerQuitEvent) {
        e.quitMessage(null)
    }

    @EventHandler fun onLeafDecay(e: LeavesDecayEvent) {
        e.isCancelled = true
    }

    @EventHandler fun onLightning(e: LightningStrikeEvent) {
        if (e.world.name == ConfigManager.getHubWorldName()) e.isCancelled = true
    }

    @EventHandler fun onDrop(e: PlayerDropItemEvent) {
        if (e.player.gameMode != GameMode.CREATIVE) e.isCancelled = true
    }

    @EventHandler fun onRespawn(e: PlayerRespawnEvent) {
        Shadow.instance.setPlayerHubState(e.player)
    }

    @EventHandler fun onDamage(e: EntityDamageEvent) {
        e.isCancelled = true
    }

    @EventHandler fun onHunger(e: FoodLevelChangeEvent) {
        e.isCancelled = true
    }
}
