package com.shadow.features

import com.shadow.Shadow
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.pow

data class FireplaceLocation(
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double
) {
    companion object {
        fun fromLocation(location: Location): FireplaceLocation =
            FireplaceLocation(
                world = location.world?.name ?: throw IllegalArgumentException("World cannot be null"),
                x = location.x,
                y = location.y,
                z = location.z
            )

        fun fromString(str: String): FireplaceLocation {
            val parts = str.split(",")
            require(parts.size == 4) { "Invalid location string format" }
            return FireplaceLocation(
                world = parts[0],
                x = parts[1].toDouble(),
                y = parts[2].toDouble(),
                z = parts[3].toDouble()
            )
        }
    }

    fun toBukkitLocation(): Location? =
        Shadow.instance.server.getWorld(world)?.let { world ->
            Location(world, x, y, z)
        }

    override fun toString(): String = "$world,$x,$y,$z"
}

data class ParticleEffect(
    val type: Particle,
    val count: Int = 0,
    val offsetX: Double = 0.0,
    val offsetY: Double = 0.0,
    val offsetZ: Double = 0.0,
    val speed: Double = 0.02,
    val size: Double = 1.0,
    val chance: Double = 1.0
)

object FireplaceEffect {
    private val config: YamlConfiguration by lazy {
        YamlConfiguration.loadConfiguration(configFile)
    }

    private val configFile: File by lazy {
        createConfigFile()
    }

    private val fireplaces = mutableSetOf<FireplaceLocation>()
    private var effectTask: Int = -1

    private val baseFlameEffect = ParticleEffect(
        type = Particle.FLAME,
        count = 0,
        offsetY = 0.1,
        speed = 0.03,
        chance = 1.0
    )

    private val innerFlameEffect = ParticleEffect(
        type = Particle.SOUL_FIRE_FLAME,
        count = 0,
        offsetY = 0.05,
        speed = 0.02,
        chance = 0.7
    )

    private val emberEffect = ParticleEffect(
        type = Particle.LAVA,
        count = 0,
        offsetY = 0.1,
        speed = 0.01,
        chance = 0.3
    )

    private val smokeEffect = ParticleEffect(
        type = Particle.SMOKE,
        count = 0,
        offsetY = 0.2,
        speed = 0.01,
        chance = 0.4
    )

    private val largeSmokeEffect = ParticleEffect(
        type = Particle.LARGE_SMOKE,
        count = 0,
        offsetY = 0.3,
        speed = 0.005,
        chance = 0.2
    )

    fun init(plugin: Shadow) {
        initializeConfig()
        loadFireplaces()
        registerCommand(plugin)
        startEffect(plugin)
    }

    private fun createConfigFile(): File =
        File(Shadow.instance.dataFolder, "fireplaces.yml").apply {
            if (!exists()) createNewFile()
        }

    private fun initializeConfig() {
        if (!config.contains("fireplaces")) {
            config.set("fireplaces", emptyList<String>())
            config.save(configFile)
        }
    }

    private fun loadFireplaces() {
        config.getStringList("fireplaces")
            .mapNotNull {
                runCatching { FireplaceLocation.fromString(it) }.getOrNull()
            }
            .toCollection(fireplaces)
    }

    private fun saveFireplaces() {
        config.set("fireplaces", fireplaces.map(FireplaceLocation::toString))
        config.save(configFile)
    }

    private fun registerCommand(plugin: Shadow) {
        plugin.getCommand("fireplace")?.let { command ->
            command.setExecutor(FireplaceCommand())
            command.tabCompleter = FireplaceTabCompleter()
        }
    }

    private fun startEffect(plugin: Shadow) {
        effectTask = createEffectTask().runTaskTimer(plugin, 0L, 1L).taskId
    }

    private fun createEffectTask() = object : BukkitRunnable() {
        private var tick = 0.0
        private val baseRadius = 0.3
        private val spiralPoints = 5

        override fun run() {
            fireplaces.forEach { fireplace ->
                fireplace.toBukkitLocation()?.let { location ->
                    spawnFireEffects(location)
                }
            }
            tick += 0.2
        }

        private fun spawnFireEffects(location: Location) {
            // Base flame spiral
            spawnSpiralEffect(location, baseRadius, spiralPoints, baseFlameEffect)

            // Inner flame (more intense center)
            spawnSpiralEffect(location, baseRadius * 0.6, 3, innerFlameEffect)

            // Random embers
            if (Math.random() < emberEffect.chance) {
                val emberLoc = location.clone().add(
                    (Math.random() - 0.5) * baseRadius * 2,
                    Math.random() * 0.5,
                    (Math.random() - 0.5) * baseRadius * 2
                )
                spawnParticle(emberLoc, emberEffect)
            }

            // Smoke columns
            spawnSmokeColumns(location)
        }

        private fun spawnSpiralEffect(location: Location, radius: Double, points: Int, effect: ParticleEffect) {
            repeat(points) { i ->
                val height = (sin(tick * 0.5) * 0.2) + 0.5 // Oscillating height
                val angle = tick + (Math.PI * 2 * i / points)

                val effectLocation = location.clone().add(
                    radius * cos(angle),
                    height * effect.size,
                    radius * sin(angle)
                )

                if (Math.random() < effect.chance) {
                    spawnParticle(effectLocation, effect)
                }
            }
        }

        private fun spawnSmokeColumns(location: Location) {
            // Regular smoke
            if (Math.random() < smokeEffect.chance) {
                val smokeLoc = location.clone().add(
                    (Math.random() - 0.5) * baseRadius * 2,
                    1.0 + Math.random() * 0.5,
                    (Math.random() - 0.5) * baseRadius * 2
                )
                spawnParticle(smokeLoc, smokeEffect)
            }

            // Large smoke clouds
            if (Math.random() < largeSmokeEffect.chance) {
                val largesmokeLoc = location.clone().add(
                    (Math.random() - 0.5) * baseRadius * 3,
                    1.5 + Math.random() * 0.7,
                    (Math.random() - 0.5) * baseRadius * 3
                )
                spawnParticle(largesmokeLoc, largeSmokeEffect)
            }
        }

        private fun spawnParticle(location: Location, effect: ParticleEffect) {
            location.world?.spawnParticle(
                effect.type,
                location,
                effect.count,
                effect.offsetX,
                effect.offsetY,
                effect.offsetZ,
                effect.speed
            )
        }
    }

    fun addFireplace(location: Location) {
        FireplaceLocation.fromLocation(location).let { fireplaceLocation ->
            fireplaces.add(fireplaceLocation)
            saveFireplaces()
        }
    }

    fun removeNearbyFireplace(location: Location, radius: Double = 2.0): Boolean {
        val radiusSquared = radius.pow(2)
        val fireplace = fireplaces
            .mapNotNull { it.toBukkitLocation() }
            .find { it.distanceSquared(location) <= radiusSquared }

        return fireplace?.let { loc ->
            fireplaces.remove(FireplaceLocation.fromLocation(loc))
            saveFireplaces()
            true
        } ?: false
    }
}

class FireplaceCommand : CommandExecutor {
    private val pink = TextColor.color(0xFF69B4)

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(Component.text("Only players can use this command!").color(pink))
            return true
        }

        if (!sender.hasPermission("sakura.fireplace")) {
            sender.sendMessage(Component.text("You don't have permission to use this command!").color(pink))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "add" -> handleAdd(sender)
            "remove" -> handleRemove(sender)
            else -> sendUsage(sender)
        }

        return true
    }

    private fun handleAdd(player: Player) {
        FireplaceEffect.addFireplace(player.location)
        player.sendMessage(Component.text("Added a new fireplace at your location!").color(pink))
        player.playSound(player.location, Sound.BLOCK_FIRE_AMBIENT, 1.0f, 1.0f)
    }

    private fun handleRemove(player: Player) {
        if (FireplaceEffect.removeNearbyFireplace(player.location)) {
            player.sendMessage(Component.text("Removed the nearest fireplace!").color(pink))
            player.playSound(player.location, Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 1.0f)
        } else {
            player.sendMessage(Component.text("No fireplace found nearby!").color(pink))
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(Component.text("Usage: /fireplace <add|remove>").color(pink))
    }
}

class FireplaceTabCompleter : TabCompleter {
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): List<String> = when {
        args.size == 1 -> listOf("add", "remove").filter { it.startsWith(args[0].lowercase()) }
        else -> emptyList()
    }
}