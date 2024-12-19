package com.shadow.listeners

import com.shadow.Shadow
import com.shadow.features.spawn.SpawnManager
import gg.flyte.twilight.event.event
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.GameMode
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent

class PlayerListener : Listener {
    private companion object {
        val PINK = TextColor.color(0xFF69B4)
        val LIGHT_PINK = TextColor.color(0xFFB6C1)
        const val SPAWN_TELEPORT_DELAY = 20L // 1 second delay
        const val SEPARATOR_LENGTH = 30
    }

    private val welcomeMessage: List<Component> by lazy {
        buildList {
            add(Component.empty())
            add(
                Component.text()
                    .append(
                        Component.text("Welcome to Sakura PvP")
                            .color(PINK)
                            .decorate(TextDecoration.BOLD)
                    )
                    .build()
            )
            add(
                Component.text()
                    .append(
                        Component.text("━".repeat(SEPARATOR_LENGTH))
                            .color(LIGHT_PINK)
                    )
                    .decoration(TextDecoration.STRIKETHROUGH, true)
                    .build()
            )
            add(Component.empty())
        }
    }

    init {
        registerEvents()
    }

    private fun registerEvents() {
        registerJoinEvent()
        registerDropEvent()
        registerRespawnEvent()
        registerDamageEvent()
        registerHungerEvent()
    }

    private fun registerJoinEvent() {
        event<PlayerJoinEvent> {
            joinMessage(null) // Remove default join message
            Shadow.instance.setPlayerHubState(player)
            scheduleSpawnTeleport(player)
            sendWelcomeMessage(player)
        }
    }

    private fun registerDropEvent() {
        event<PlayerDropItemEvent> {
            if (player.gameMode != GameMode.CREATIVE) {
                isCancelled = true
            }
        }
    }

    private fun registerRespawnEvent() {
        event<PlayerRespawnEvent> {
            Shadow.instance.setPlayerHubState(player)
        }
    }

    private fun registerDamageEvent() {
        event<EntityDamageEvent> {
            isCancelled = true
        }
    }

    private fun registerHungerEvent() {
        event<FoodLevelChangeEvent> {
            isCancelled = true
        }
    }

    private fun scheduleSpawnTeleport(player: org.bukkit.entity.Player) {
        Shadow.instance.server.scheduler.runTaskLater(
            Shadow.instance,
            Runnable { SpawnManager.teleportToSpawn(player) },
            SPAWN_TELEPORT_DELAY
        )
    }

    private fun sendWelcomeMessage(player: org.bukkit.entity.Player) {
        welcomeMessage.forEach(player::sendMessage)
    }
}