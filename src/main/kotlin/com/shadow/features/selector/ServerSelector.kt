package com.shadow.features.selector

import com.shadow.Shadow
import com.shadow.domain.QueueResult
import org.bukkit.entity.Player
import org.bukkit.event.Listener

/**
 * Compatibility layer for the new ServerSelectorManager implementation.
 * This maintains backward compatibility with existing code that uses ServerSelector.
 */
object ServerSelector : Listener {
    /**
     * Initializes the ServerSelector system.
     * Delegates to ServerSelectorManager.
     */
    fun init(plugin: Shadow) {
        ServerSelectorManager.init(plugin)
    }

    /**
     * Updates all open server selector GUIs to reflect current queue statuses.
     * Delegates to ServerSelectorManager.
     */
    fun updateAllSelectors() {
        ServerSelectorManager.updateAllSelectors()
    }

    /**
     * Opens the server selector GUI for a player.
     * Delegates to ServerSelectorManager.
     */
    fun openSelector(player: Player) {
        ServerSelectorManager.openSelector(player)
    }

    /**
     * Adds a player to a server's queue.
     * Delegates to ServerSelectorManager.
     * @return QueueResult indicating success/failure and queue position
     */
    fun addToQueue(player: Player, serverId: String): QueueResult {
        return ServerSelectorManager.addToQueue(player, serverId)
    }

    /**
     * Removes a player from all server queues.
     * Delegates to ServerSelectorManager.
     */
    fun removeFromAllQueues(player: Player) {
        ServerSelectorManager.removeFromAllQueues(player)
    }

    /**
     * Gives the selector item to a player.
     * Delegates to ServerSelectorManager.
     */
    fun giveSelectorItem(player: Player) {
        ServerSelectorManager.giveSelectorItem(player)
    }

    /**
     * Disables a server's queue and updates the selector GUI.
     * Delegates to ServerSelectorManager.
     */
    fun disableQueue(serverId: String) {
        ServerSelectorManager.disableQueue(serverId)
    }

    /**
     * Enables a server's queue and updates the selector GUI.
     * Delegates to ServerSelectorManager.
     */
    fun enableQueue(serverId: String) {
        ServerSelectorManager.enableQueue(serverId)
    }
}