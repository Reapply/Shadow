package com.shadow.features

import com.shadow.Shadow
import com.shadow.utils.ShadowUtils
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.util.Vector
import java.util.*

/**
 * Launch pad feature that propels players when they step on pressure plates
 */
object LaunchPad : Listener {
    private data class PadProperties(
        val multiplier: Double,
        val verticalBoost: Double,
        val cooldown: Long,
        val particle: Particle,
        val sound: Sound
    )

    private val launchPadTypes = mutableMapOf<Material, PadProperties>()
    private val launchCooldown = mutableMapOf<UUID, Long>()
    private var enabled = true

    /**
     * Initialize the launch pad feature
     */
    fun init() {
        loadConfig()

        if (enabled) {
            Shadow.instance.server.pluginManager.registerEvents(this, Shadow.instance)
            Shadow.instance.logger.info("Launch pads enabled with ${launchPadTypes.size} pad types")
        } else {
            Shadow.instance.logger.info("Launch pads are disabled in config")
        }
    }

    /**
     * Load launch pad configuration from config.yml
     */
    private fun loadConfig() {
        val config = Shadow.instance.config

        // Check if launch pads are enabled
        enabled = config.getBoolean("launch-pads.enabled", true)
        if (!enabled) return

        // Load each launch pad type
        val launchPadsSection = config.getConfigurationSection("launch-pads") ?: return

        // Skip the "enabled" key and process only pad configurations
        launchPadsSection.getKeys(false)
            .filter { it != "enabled" }
            .forEach { padKey ->
                val padSection = launchPadsSection.getConfigurationSection(padKey) ?: return@forEach
                loadPadType(padSection)
            }

        // If no pads were loaded, use defaults
        if (launchPadTypes.isEmpty()) {
            loadDefaultPads()
        }
    }

    /**
     * Load a single pad type from configuration
     */
    private fun loadPadType(config: ConfigurationSection) {
        try {
            val materialName = config.getString("material") ?: return
            val material = Material.valueOf(materialName)

            val padProperties = PadProperties(
                multiplier = config.getDouble("multiplier", 2.0),
                verticalBoost = config.getDouble("vertical-boost", 0.8),
                cooldown = config.getLong("cooldown", 1000L),
                particle = try {
                    Particle.valueOf(config.getString("particle", "CLOUD")!!)
                } catch (e: Exception) {
                    Shadow.instance.logger.warning("Invalid particle type: ${config.getString("particle")}. Using CLOUD.")
                    Particle.CLOUD
                },
                sound = try {
                    Sound.valueOf(config.getString("sound", "BLOCK_IRON_TRAPDOOR_OPEN")!!)
                } catch (e: Exception) {
                    Shadow.instance.logger.warning("Invalid sound: ${config.getString("sound")}. Using BLOCK_IRON_TRAPDOOR_OPEN.")
                    Sound.BLOCK_IRON_TRAPDOOR_OPEN
                }
            )

            launchPadTypes[material] = padProperties
        } catch (e: Exception) {
            Shadow.instance.logger.warning("Error loading launch pad configuration: ${e.message}")
        }
    }

    /**
     * Load default pad configurations if none were loaded from config
     */
    private fun loadDefaultPads() {
        launchPadTypes[Material.STONE_PRESSURE_PLATE] = PadProperties(
            multiplier = 2.0,
            verticalBoost = 0.8,
            cooldown = 1000L,
            particle = Particle.CLOUD,
            sound = Sound.BLOCK_IRON_TRAPDOOR_OPEN
        )

        launchPadTypes[Material.HEAVY_WEIGHTED_PRESSURE_PLATE] = PadProperties(
            multiplier = 3.0,
            verticalBoost = 1.2,
            cooldown = 1500L,
            particle = Particle.FIREWORK,
            sound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH
        )

        launchPadTypes[Material.LIGHT_WEIGHTED_PRESSURE_PLATE] = PadProperties(
            multiplier = 4.0,
            verticalBoost = 1.5,
            cooldown = 2000L,
            particle = Particle.TOTEM_OF_UNDYING,
            sound = Sound.ENTITY_WITHER_SHOOT
        )
    }

    /**
     * Handle player stepping on pressure plates
     */
    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // Check if player stepped on a pressure plate
        if (event.action != Action.PHYSICAL) return

        val player = event.player
        val currentTime = System.currentTimeMillis()

        // Get pad properties or return if not a launch pad
        val padProps = launchPadTypes[event.clickedBlock?.type] ?: return

        // Check cooldown
        if (launchCooldown[player.uniqueId]?.let { currentTime - it < padProps.cooldown } == true) {
            return
        }

        launchPlayer(player, padProps)
        launchCooldown[player.uniqueId] = currentTime
    }

    /**
     * Launch a player with the given pad properties
     */
    private fun launchPlayer(player: Player, props: PadProperties) {
        val direction = player.location.direction.normalize()

        // Preserve some of the current momentum
        val currentVel = player.velocity
        val preservedMomentum = Vector(
            currentVel.x * 0.2,
            currentVel.y * 0.1,
            currentVel.z * 0.2
        )

        // Calculate and apply launch velocity
        val launchVel = Vector(
            direction.x * props.multiplier,
            props.verticalBoost,
            direction.z * props.multiplier
        ).add(preservedMomentum)

        player.velocity = launchVel

        // Effects
        player.world.spawnParticle(
            props.particle,
            player.location,
            75,  // particle count
            0.4, // spread
            0.4,
            0.4,
            0.15 // speed
        )

        ShadowUtils.playSound(player, props.sound)
    }
}

/**
 * Handles world protection features for the hub server
 */
object WorldProtection : Listener {
    /**
     * Initialize world protection
     */
    fun init() {
        Shadow.instance.server.pluginManager.registerEvents(this, Shadow.instance)
    }

    /**
     * Prevent PvP
     */
    @EventHandler
    fun onEntityDamage(event: EntityDamageByEntityEvent) {
        if (event.damager is Player && event.entity is Player) {
            event.isCancelled = true
        }
    }
}
