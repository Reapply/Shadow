package com.shadow.features.selector

import com.shadow.config.ConfigManager
import com.shadow.domain.QueueResult
import com.shadow.utils.ShadowUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Command to manage the server queue system
 */
class QueueCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player && args.getOrNull(0) != "control") {
            sender.sendMessage(ShadowUtils.parseMessage("<red>This command can only be used by players!"))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "join" -> handleJoinCommand(sender, args)
            "leave" -> handleLeaveCommand(sender)
            "control" -> handleControlCommand(sender, args)
            else -> sendUsage(sender)
        }

        return true
    }

    /**
     * Handle the join queue command
     */
    private fun handleJoinCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return

        val serverId = args.getOrNull(1)
        if (serverId == null) {
            sender.sendMessage(ShadowUtils.parseMessage("<red>Usage: /queue join <server>"))
            return
        }

        when (val result = ServerSelector.addToQueue(sender, serverId)) {
            is QueueResult.Success -> {
                // Message is sent in addToQueue
            }
            is QueueResult.Error -> {
                sender.sendMessage(ShadowUtils.parseMessage("<red>${result.message}"))
            }
            QueueResult.Disabled -> {
                // Message is sent in addToQueue
            }
            QueueResult.AlreadyInQueue -> {
                sender.sendMessage(ShadowUtils.parseMessage("<red>You are already in this queue!"))
            }
        }
    }

    /**
     * Handle the leave queue command
     */
    private fun handleLeaveCommand(sender: CommandSender) {
        if (sender !is Player) return

        ServerSelector.removeFromAllQueues(sender)
        sender.sendMessage(ShadowUtils.parseMessage("<green>You left all queues!"))
    }

    /**
     * Handle the queue control commands (admin only)
     */
    private fun handleControlCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("shadow.queue.admin")) {
            sender.sendMessage(ShadowUtils.parseMessage("<red>You don't have permission to control queues!"))
            return
        }

        when (args.getOrNull(1)?.lowercase()) {
            "disable" -> handleQueueDisable(sender, args)
            "enable" -> handleQueueEnable(sender, args)
            else -> {
                sender.sendMessage(ShadowUtils.parseMessage("<red>Usage: /queue control <disable|enable> <server>"))
            }
        }
    }

    /**
     * Handle disabling a server queue
     */
    private fun handleQueueDisable(sender: CommandSender, args: Array<out String>) {
        val serverId = args.getOrNull(2)
        if (serverId == null) {
            sender.sendMessage(ShadowUtils.parseMessage("<red>Usage: /queue control disable <server>"))
            return
        }

        ServerSelector.disableQueue(serverId)
        sender.sendMessage(ShadowUtils.parseMessage("<green>Queue for $serverId has been disabled!"))
    }

    /**
     * Handle enabling a server queue
     */
    private fun handleQueueEnable(sender: CommandSender, args: Array<out String>) {
        val serverId = args.getOrNull(2)
        if (serverId == null) {
            sender.sendMessage(ShadowUtils.parseMessage("<red>Usage: /queue control enable <server>"))
            return
        }

        ServerSelector.enableQueue(serverId)
        sender.sendMessage(ShadowUtils.parseMessage("<green>Queue for $serverId has been enabled!"))
    }

    /**
     * Send command usage information
     */
    private fun sendUsage(sender: CommandSender) {
        val baseCommands = listOf(
            "<#9AABBD>Usage: /queue <join|leave>",
            "<gray>  join <server> - Join a server queue",
            "<gray>  leave - Leave current queue"
        )

        val adminCommands = if (sender.hasPermission("shadow.queue.admin")) {
            listOf(
                "<#9AABBD>Admin Commands:",
                "<gray>  control disable <server> - Disable a queue",
                "<gray>  control enable <server> - Enable a queue"
            )
        } else emptyList()

        (baseCommands + adminCommands).forEach { line ->
            sender.sendMessage(ShadowUtils.parseMessage(line))
        }
    }
}

/**
 * Tab completer for the queue command
 */
class QueueTabCompleter : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> = when (args.size) {
        1 -> getFirstArgumentCompletions(sender, args[0])
        2 -> getSecondArgumentCompletions(sender, args[0], args[1])
        3 -> getThirdArgumentCompletions(sender, args[0], args[1], args[2])
        else -> emptyList()
    }

    private fun getFirstArgumentCompletions(sender: CommandSender, current: String): List<String> {
        val commands = mutableListOf("join", "leave")
        if (sender.hasPermission("shadow.queue.admin")) {
            commands.add("control")
        }
        return commands.filter { it.startsWith(current.lowercase()) }
    }

    private fun getSecondArgumentCompletions(
        sender: CommandSender,
        firstArg: String,
        current: String
    ): List<String> = when (firstArg.lowercase()) {
        "join" -> ConfigManager.serverList.map { it.id }
        "control" -> if (sender.hasPermission("shadow.queue.admin")) {
            listOf("disable", "enable")
        } else emptyList()
        else -> emptyList()
    }.filter { it.startsWith(current.lowercase()) }

    private fun getThirdArgumentCompletions(
        sender: CommandSender,
        firstArg: String,
        secondArg: String,
        current: String
    ): List<String> = when {
        firstArg.equals("control", ignoreCase = true) &&
                sender.hasPermission("shadow.queue.admin") -> {
            ConfigManager.serverList.map { it.id }
                .filter { it.startsWith(current.lowercase()) }
        }
        else -> emptyList()
    }
}
