package com.shadow.commands

import com.shadow.Shadow
import com.shadow.config.ConfigManager
import com.shadow.features.selector.ServerSelector
import com.shadow.utils.ShadowUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

/**
 * Command to reload the plugin configuration
 */
class ReloadCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("absidien.admin")) {
            sender.sendMessage(ShadowUtils.parseMessage("<red>You don't have permission to use this command!"))
            return true
        }

        try {
            // Reload config from disk
            Shadow.instance.reloadConfig()

            // Reinitialize config manager
            ConfigManager.init(Shadow.instance)

            // Update server selector with new config
            ServerSelector.updateAllSelectors()

            sender.sendMessage(ShadowUtils.parseMessage("<gold>✓ <white>Configuration reloaded successfully!"))
        } catch (e: Exception) {
            sender.sendMessage(ShadowUtils.parseMessage("<red>✗ <white>Failed to reload configuration: ${e.message}"))
            Shadow.instance.logger.severe("Error reloading configuration: ${e.stackTraceToString()}")
        }

        return true
    }
}