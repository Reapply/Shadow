package com.shadow.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Sound
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Utility functions that are shared across the plugin
 */
object ShadowUtils {
    private val mm = MiniMessage.miniMessage()

    /**
     * Parse a string using MiniMessage format
     */
    fun parseMessage(message: String): Component = mm.deserialize(message)

    /**
     * Send a MiniMessage formatted message to a player
     */
    fun sendMessage(player: Player, message: String) {
        player.sendMessage(parseMessage(message))
    }

    /**
     * Send a MiniMessage formatted message to a command sender
     */
    fun sendMessage(sender: CommandSender, message: String) {
        sender.sendMessage(parseMessage(message))
    }

    /**
     * Play a sound to a player
     */
    fun playSound(player: Player, sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
        player.playSound(player.location, sound, volume, pitch)
    }

    /**
     * Format a time in milliseconds to a readable string
     */
    fun formatTime(ms: Long): String {
        val seconds = ms / 1000
        return when {
            seconds < 60 -> "${seconds}s"
            else -> "${seconds / 60}m ${seconds % 60}s"
        }
    }

    /**
     * Check if an item is a selector item by its custom model data
     */
    fun isSelectorItem(customModelData: Int?): Boolean {
        return customModelData == 1001
    }
}