package com.shadow.commands

import com.shadow.Shadow
import com.shadow.config.ConfigManager
import com.shadow.features.selector.ServerSelector
import com.shadow.utils.ShadowUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.PluginDescriptionFile

/**
 * Command handler for the /shadow command
 * Provides plugin administration functionality
 */
class ShadowCommand : CommandExecutor, TabCompleter {
    private val subcommands = listOf("reload", "info")
    private val prefix = "<#9AABBD><bold>Shadow</bold><gray> | "
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("shadow.admin")) {
            sender.sendMessage(ShadowUtils.parseMessage("<gray>No permission."))
            return true
        }

        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> reloadConfig(sender)
            "info" -> showPluginInfo(sender)
            else -> showUsage(sender)
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return subcommands.filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }

    private fun showUsage(sender: CommandSender) {
        sender.sendMessage(ShadowUtils.parseMessage("$prefix<yellow>/shadow info <white>- Show plugin information"))
        sender.sendMessage(ShadowUtils.parseMessage("$prefix<yellow>/shadow reload <white>- Reload plugin configuration"))
    }

    private fun showPluginInfo(sender: CommandSender) {
        val description: PluginDescriptionFile = Shadow.instance.description

        sender.sendMessage(ShadowUtils.parseMessage("$prefix<yellow>Version: <white>${description.version}"))
        sender.sendMessage(ShadowUtils.parseMessage("$prefix<yellow>Authors: <white>${description.authors.joinToString(", ")}"))
        sender.sendMessage(ShadowUtils.parseMessage("$prefix<yellow>API Version: <white>${description.apiVersion}"))

        sender.sendMessage(ShadowUtils.parseMessage("$prefix<yellow>Available Commands:"))
        description.commands.forEach { (name, cmdInfo) ->
            sender.sendMessage(ShadowUtils.parseMessage("$prefix<yellow>/$name <white>- ${cmdInfo["description"]}"))
        }
    }

    private fun reloadConfig(sender: CommandSender) {
        try {
            // Reload config from disk
            Shadow.instance.reloadConfig()

            // Reinitialize config manager
            ConfigManager.init(Shadow.instance)

            // Update server selector with new config
            ServerSelector.updateAllSelectors()

            sender.sendMessage(ShadowUtils.parseMessage("$prefix<white>Configuration reloaded successfully!"))
        } catch (e: Exception) {
            sender.sendMessage(ShadowUtils.parseMessage("$prefix<red>Failed to reload configuration: ${e.message}"))
            Shadow.instance.logger.severe("Error reloading configuration: ${e.stackTraceToString()}")
        }
    }
}