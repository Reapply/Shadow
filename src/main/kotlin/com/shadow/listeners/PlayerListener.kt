package com.shadow.listeners

import com.shadow.Shadow
import gg.flyte.twilight.event.event
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent

class PlayerListener : Listener {

    private fun createWelcomeMessage(): List<Component> {
        val pink = TextColor.color(0xFF69B4)
        val lightPink = TextColor.color(0xFFB6C1)

        return listOf(
            Component.empty(),
            Component.text()
                .append(Component.text("Welcome to Sakura PvP")
                    .color(pink)
                    .decorate(TextDecoration.BOLD))
                .build(),
            Component.text()
                .append(Component.text("━".repeat(30))
                    .color(lightPink))
                .build(),
            Component.empty()
        )
    }

    init {
        // Handle player join
        event<PlayerJoinEvent> {
            // Set join message to null to remove default message
            joinMessage(null)

            // Set player state
            Shadow.instance.setPlayerHubState(player)

            // Send welcome message
            createWelcomeMessage().forEach { message ->
                player.sendMessage(message)
            }
        }

        // Handle respawn
        event<PlayerRespawnEvent> {
            Shadow.instance.setPlayerHubState(player)
        }

        // Prevent all damage
        event<EntityDamageEvent> {
            isCancelled = true
        }

        // Prevent hunger loss
        event<FoodLevelChangeEvent> {
            isCancelled = true
        }
    }
}