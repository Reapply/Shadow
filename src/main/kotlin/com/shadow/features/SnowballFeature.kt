package com.shadow.features

import com.shadow.Shadow
import gg.flyte.twilight.builders.item.ItemBuilder
import gg.flyte.twilight.event.event
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Snowball
import org.bukkit.event.block.Action
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.cos
import kotlin.math.sin

data class ParticleConfig(
    val type: Particle,
    val amount: Int,
    val offsetX: Double,
    val offsetY: Double,
    val offsetZ: Double,
    val speed: Double
)

object SnowballFeature {
    private val mm = MiniMessage.miniMessage()

    private val snowballItem by lazy { createSnowballItem() }

    private val snowflakeEffect = ParticleConfig(
        type = Particle.SNOWFLAKE,
        amount = 3,
        offsetX = 0.0,
        offsetY = 0.0,
        offsetZ = 0.0,
        speed = 0.05
    )

    private val snowballEffect = ParticleConfig(
        type = Particle.ITEM_SNOWBALL,
        amount = 5,
        offsetX = 0.2,
        offsetY = 0.2,
        offsetZ = 0.2,
        speed = 0.1
    )

    private fun createSnowballItem(): ItemBuilder =
        ItemBuilder(
            Material.SNOWBALL,
            name = mm.deserialize("<dark_purple>Magic Snowball")
        ).apply {
            lore = mutableListOf(
                mm.deserialize("<gray>Right-click to throw!"),
                mm.deserialize("<gray>Watch the magical effects!")
            )
            customModelData = 1002
        }

    fun init() {
        registerInteractEvent()
        registerProjectileHitEvent()
    }

    private fun registerInteractEvent() {
        event<PlayerInteractEvent> {
            when {
                isSnowballThrow(item) -> handleSnowballThrow(player)
                isSnowBlockInteraction(action, clickedBlock?.type) ->
                    handleSnowBlockInteraction(player, clickedBlock?.location)
            }
        }
    }

    private fun registerProjectileHitEvent() {
        event<ProjectileHitEvent> {
            if (entity !is Snowball) return@event
            handleSnowballHit(entity as Snowball)
        }
    }

    private fun isSnowballThrow(item: ItemStack?): Boolean =
        item?.itemMeta?.let { meta ->
            meta.hasCustomModelData() && meta.customModelData == 1002
        } == true

    private fun isSnowBlockInteraction(action: Action, material: Material?): Boolean =
        action == Action.RIGHT_CLICK_BLOCK && material == Material.SNOW_BLOCK

    private fun handleSnowballThrow(player: Player) {
        player.inventory.addItem(snowballItem.build())
    }

    private fun handleSnowBlockInteraction(player: Player, blockLocation: Location?) {
        blockLocation?.let { location ->
            giveSnowballItem(player)
            spawnSnowBlockParticles(location)
            playSnowBlockSound(player)
        }
    }

    private fun spawnSnowBlockParticles(location: Location) {
        location.world.spawnParticle(
            Particle.ITEM_SNOWBALL,
            location.clone().add(0.5, 1.0, 0.5),
            15,
            0.3, 0.3, 0.3,
            0.05
        )
    }

    private fun playSnowBlockSound(player: Player) {
        player.playSound(
            player.location,
            Sound.BLOCK_SNOW_STEP,
            1.0f,
            1.2f
        )
    }

    private fun handleSnowballHit(snowball: Snowball) {
        createSpiralEffect(snowball.location)
        playHitSound(snowball.location)
    }

    private fun createSpiralEffect(location: Location) {
        object : BukkitRunnable() {
            private var angle = 0.0
            private var height = 0.0
            private var iterations = 0
            private val maxIterations = 20
            private val radius = 1.0
            private val angleIncrement = Math.PI / 8
            private val heightIncrement = 0.1

            override fun run() {
                if (iterations >= maxIterations) {
                    cancel()
                    return
                }

                spawnSpiralParticles(location)
                updateParameters()
            }

            private fun spawnSpiralParticles(baseLocation: Location) {
                val x = baseLocation.x + radius * cos(angle)
                val z = baseLocation.z + radius * sin(angle)
                val y = baseLocation.y + height

                with(baseLocation.world) {
                    spawnParticle(snowflakeEffect, x, y, z)
                    spawnParticle(snowballEffect, x, y, z)
                }
            }

            private fun updateParameters() {
                angle += angleIncrement
                height += heightIncrement
                iterations++
            }

        }.runTaskTimer(Shadow.instance, 0L, 1L)
    }

    private fun org.bukkit.World.spawnParticle(
        config: ParticleConfig,
        x: Double,
        y: Double,
        z: Double
    ) {
        spawnParticle(
            config.type,
            x, y, z,
            config.amount,
            config.offsetX,
            config.offsetY,
            config.offsetZ,
            config.speed
        )
    }

    private fun playHitSound(location: Location) {
        location.world.playSound(
            location,
            Sound.BLOCK_SNOW_BREAK,
            1.0f,
            1.2f
        )
    }

    fun giveSnowballItem(player: Player) {
        val slot = 2 // third slot
        player.inventory.setItem(slot, snowballItem.build())
        player.playSound(player.location, Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f)
    }
}