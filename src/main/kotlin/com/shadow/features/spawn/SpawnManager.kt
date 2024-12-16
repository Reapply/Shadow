package com.shadow.features.spawn

import com.shadow.Shadow
import gg.flyte.twilight.event.event
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.io.File

object SpawnManager {
    private lateinit var spawnConfig: YamlConfiguration
    private lateinit var spawnFile: File
    private var spawnLocation: Location? = null

    fun init(plugin: Shadow) {
        // Initialize config
        spawnFile = File(plugin.dataFolder, "spawn.yml")
        if (!spawnFile.exists()) {
            spawnFile.createNewFile()
        }
        spawnConfig = YamlConfiguration.loadConfiguration(spawnFile)

        // Load spawn location
        loadSpawn()

        // Register events
        registerEvents()

        // Register command
        plugin.getCommand("setspawn")?.setExecutor(SetSpawnCommand())
    }

    private fun loadSpawn() {
        if (spawnConfig.contains("spawn")) {
            val world = Bukkit.getWorld(spawnConfig.getString("spawn.world")!!)
            val x = spawnConfig.getDouble("spawn.x")
            val y = spawnConfig.getDouble("spawn.y")
            val z = spawnConfig.getDouble("spawn.z")
            val yaw = spawnConfig.getDouble("spawn.yaw").toFloat()
            val pitch = spawnConfig.getDouble("spawn.pitch").toFloat()

            spawnLocation = Location(world, x, y, z, yaw, pitch)
        }
    }

    private fun saveSpawn() {
        spawnLocation?.let { loc ->
            spawnConfig.set("spawn.world", loc.world?.name)
            spawnConfig.set("spawn.x", loc.x)
            spawnConfig.set("spawn.y", loc.y)
            spawnConfig.set("spawn.z", loc.z)
            spawnConfig.set("spawn.yaw", loc.yaw)
            spawnConfig.set("spawn.pitch", loc.pitch)
            spawnConfig.save(spawnFile)
        }
    }

    private fun registerEvents() {
        // Teleport to spawn on join
        event<PlayerJoinEvent> {
            spawnLocation?.let { spawn ->
                player.teleport(spawn)
            }
        }

        // Teleport to spawn on respawn
        event<PlayerRespawnEvent> {
            spawnLocation?.let { spawn ->
                player.teleport(spawn)
            }
        }
    }

    fun teleportToSpawn(player: Player) {
        spawnLocation?.let { spawn ->
            player.teleport(spawn)
        }
    }

    fun setSpawn(location: Location) {
        spawnLocation = location
        saveSpawn()
    }

    fun getSpawn(): Location? = spawnLocation
}

class SetSpawnCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can use this command!")
                .color(TextColor.color(0xFF69B4)))
            return true
        }

        if (!sender.hasPermission("sakura.setspawn")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!")
                .color(TextColor.color(0xFF69B4)))
            return true
        }

        SpawnManager.setSpawn(sender.location)
        sender.sendMessage(Component.text("Spawn location has been set!")
            .color(TextColor.color(0xFF69B4)))

        return true
    }
}