package com.shadow.features

import com.shadow.Shadow
import com.shadow.utils.ShadowUtils
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
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

    private val launchPadTypes = mapOf(
        Material.STONE_PRESSURE_PLATE to PadProperties(
            multiplier = 2.0,
            verticalBoost = 0.8,
            cooldown = 1000L,
            particle = Particle.CLOUD,
            sound = Sound.BLOCK_IRON_TRAPDOOR_OPEN
        ),
        Material.HEAVY_WEIGHTED_PRESSURE_PLATE to PadProperties(
            multiplier = 3.0,
            verticalBoost = 1.2,
            cooldown = 1500L,
            particle = Particle.FIREWORK,
            sound = Sound.ENTITY_FIREWORK_ROCKET_LAUNCH
        ),
        Material.LIGHT_WEIGHTED_PRESSURE_PLATE to PadProperties(
            multiplier = 4.0,
            verticalBoost = 1.5,
            cooldown = 2000L,
            particle = Particle.TOTEM_OF_UNDYING,
            sound = Sound.ENTITY_WITHER_SHOOT
        )
    )

    private val launchCooldown = mutableMapOf<UUID, Long>()

    /**
     * Initialize the launch pad feature
     */
    fun init() {
        Shadow.instance.server.pluginManager.registerEvents(this, Shadow.instance)
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