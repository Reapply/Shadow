package com.shadow.features

import gg.flyte.twilight.event.event
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.block.Action
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.util.Vector
import java.util.*

object LaunchPad {
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

    fun init() {
        event<PlayerInteractEvent> {
            // Check if player stepped on a pressure plate
            if (action != Action.PHYSICAL) return@event

            val player = player
            val currentTime = System.currentTimeMillis()

            // Get pad properties or return if not a launch pad
            val padProps = launchPadTypes[clickedBlock?.type] ?: return@event

            // Check cooldown
            if (launchCooldown[player.uniqueId]?.let { currentTime - it < padProps.cooldown } == true) {
                return@event
            }

            launchPlayer(player, padProps)
            launchCooldown[player.uniqueId] = currentTime
        }
    }

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

        player.playSound(
            player.location,
            props.sound,
            1.0f,
            1.0f
        )
    }
}
object WorldProtection {
    fun init() {
        // Prevent PvP
        event<EntityDamageByEntityEvent> {
            if (damager is Player && entity is Player) {
                isCancelled = true
            }
        }
    }
}