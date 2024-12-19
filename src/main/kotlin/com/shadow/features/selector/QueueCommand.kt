package com.shadow.features.selector

import com.shadow.domain.QueueResult
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class QueueCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player && args.getOrNull(0) != "control") {
            sender.sendMessage(Component.text("This command can only be used by players!")
                .color(TextColor.color(0xFF69B4)))
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

    private fun handleJoinCommand(sender: CommandSender, args: Array<out String>) {
        if (sender !is Player) return

        val serverId = args.getOrNull(1)
        if (serverId == null) {
            sender.sendMessage(Component.text("Usage: /queue join <server>")
                .color(TextColor.color(0xFF69B4)))
            return
        }

        when (val result = ServerSelector.addToQueue(sender, serverId)) {
            is QueueResult.Success -> {
                // Message is sent in addToQueue
            }
            is QueueResult.Error -> {
                sender.sendMessage(Component.text(result.message)
                    .color(TextColor.color(0xFF69B4)))
            }
            QueueResult.Disabled -> {
                // Message is sent in addToQueue
            }
            QueueResult.AlreadyInQueue -> {
                sender.sendMessage(Component.text("You are already in this queue!")
                    .color(TextColor.color(0xFF69B4)))
            }
        }
    }

    private fun handleLeaveCommand(sender: CommandSender) {
        if (sender !is Player) return

        ServerSelector.removeFromAllQueues(sender)
        sender.sendMessage(Component.text("You left all queues!")
            .color(TextColor.color(0xFF69B4)))
    }

    private fun handleControlCommand(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission("sakura.queue.admin")) {
            sender.sendMessage(Component.text("You don't have permission to control queues!")
                .color(TextColor.color(0xFF69B4)))
            return
        }

        when (args.getOrNull(1)?.lowercase()) {
            "disable" -> handleQueueDisable(sender, args)
            "enable" -> handleQueueEnable(sender, args)
            else -> {
                sender.sendMessage(Component.text("Usage: /queue control <disable|enable> <server>")
                    .color(TextColor.color(0xFF69B4)))
            }
        }
    }

    private fun handleQueueDisable(sender: CommandSender, args: Array<out String>) {
        val serverId = args.getOrNull(2)
        if (serverId == null) {
            sender.sendMessage(Component.text("Usage: /queue control disable <server>")
                .color(TextColor.color(0xFF69B4)))
            return
        }

        ServerSelector.disableQueue(serverId)
        sender.sendMessage(Component.text("Queue for $serverId has been disabled!")
            .color(TextColor.color(0xFF69B4)))
    }

    private fun handleQueueEnable(sender: CommandSender, args: Array<out String>) {
        val serverId = args.getOrNull(2)
        if (serverId == null) {
            sender.sendMessage(Component.text("Usage: /queue control enable <server>")
                .color(TextColor.color(0xFF69B4)))
            return
        }

        ServerSelector.enableQueue(serverId)
        sender.sendMessage(Component.text("Queue for $serverId has been enabled!")
            .color(TextColor.color(0xFF69B4)))
    }

    private fun sendUsage(sender: CommandSender) {
        val baseCommands = listOf(
            "Usage: /queue <join|leave>",
            "  join <server> - Join a server queue",
            "  leave - Leave current queue"
        )

        val adminCommands = if (sender.hasPermission("sakura.queue.admin")) {
            listOf(
                "Admin Commands:",
                "  control disable <server> - Disable a queue",
                "  control enable <server> - Enable a queue"
            )
        } else emptyList()

        (baseCommands + adminCommands).forEach { line ->
            sender.sendMessage(Component.text(line)
                .color(TextColor.color(0xFF69B4)))
        }
    }
}

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
        if (sender.hasPermission("sakura.queue.admin")) {
            commands.add("control")
        }
        return commands.filter { it.startsWith(current.lowercase()) }
    }

    private fun getSecondArgumentCompletions(
        sender: CommandSender,
        firstArg: String,
        current: String
    ): List<String> = when (firstArg.lowercase()) {
        "join" -> ServerSelector.serverList.map { it.id }
        "control" -> if (sender.hasPermission("sakura.queue.admin")) {
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
                sender.hasPermission("sakura.queue.admin") -> {
            ServerSelector.serverList.map { it.id }
                .filter { it.startsWith(current.lowercase()) }
        }
        else -> emptyList()
    }
}