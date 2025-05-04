package com.shadow.commands

import com.shadow.features.autograph.AutographBook
import com.shadow.utils.ShadowUtils
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Command handler for the /autograph command
 * Allows players to accept or decline autograph requests
 */
class AutographCommand : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(ShadowUtils.parseMessage("<red>Only players can use this command!"))
            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "accept" -> {
                AutographBook.acceptAutograph(sender)
            }
            "decline" -> {
                AutographBook.declineAutograph(sender)
            }
            "request" -> {
                if (args.size < 2) {
                    sender.sendMessage(ShadowUtils.parseMessage("<red>Please specify a player name!"))
                    return true
                }

                val targetName = args[1]
                val target = Bukkit.getPlayer(targetName)

                if (target == null || !target.isOnline) {
                    sender.sendMessage(ShadowUtils.parseMessage("<red>Player $targetName is not online!"))
                    return true
                }

                if (target == sender) {
                    sender.sendMessage(ShadowUtils.parseMessage("<red>You cannot request an autograph from yourself!"))
                    return true
                }

                AutographBook.requestAutograph(sender, target)
            }
            else -> {
                showHelp(sender)
            }
        }

        return true
    }

    private fun showHelp(player: Player) {
        player.sendMessage(ShadowUtils.parseMessage("<dark_purple>Autograph Book Commands:"))
        player.sendMessage(ShadowUtils.parseMessage("<gray>/autograph accept - Accept an autograph request"))
        player.sendMessage(ShadowUtils.parseMessage("<gray>/autograph decline - Decline an autograph request"))
        player.sendMessage(ShadowUtils.parseMessage("<gray>/autograph request <player> - Request an autograph from a player"))
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()

        if (args.size == 1) {
            val subCommands = listOf("accept", "decline", "request")
            return subCommands.filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2 && args[0].equals("request", ignoreCase = true)) {
            return Bukkit.getOnlinePlayers()
                .filter { it != sender && it.name.lowercase().startsWith(args[1].lowercase()) }
                .map { it.name }
        }

        return emptyList()
    }
}