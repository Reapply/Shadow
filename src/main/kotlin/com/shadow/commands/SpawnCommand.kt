package com.shadow.commands

import com.shadow.features.spawn.SpawnManager
import com.shadow.utils.ShadowUtils
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command handler for the /spawn command
 * Teleports players to the server spawn location
 */
class SpawnCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(ShadowUtils.parseMessage("<red>Only players can use this command!"))
            return true
        }

        SpawnManager.teleportToSpawn(sender)
        sender.sendMessage(ShadowUtils.parseMessage("<green>Teleported to spawn!"))
        ShadowUtils.playSound(sender, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1f)

        return true
    }
}
