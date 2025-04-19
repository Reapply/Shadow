package com.shadow.utils

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * A simplified item builder that doesn't rely on Twilight library
 */
class SafeItemBuilder(
    private val material: Material,
    private val amount: Int = 1,
    var name: Component? = null,
    var lore: MutableList<Component>? = null,
    var customModelData: Int? = null
) {
    private val enchants = HashMap<Enchantment, Int>()
    private val flags = mutableSetOf<ItemFlag>()

    fun setName(name: Component): SafeItemBuilder {
        this.name = name
        return this
    }

    fun setLore(lore: MutableList<Component>?): SafeItemBuilder {
        this.lore = lore
        return this
    }

    fun setCustomModelData(data: Int?): SafeItemBuilder {
        this.customModelData = data
        return this
    }

    fun addEnchant(enchant: Enchantment, level: Int): SafeItemBuilder {
        enchants[enchant] = level
        return this
    }

    fun addItemFlag(flag: ItemFlag): SafeItemBuilder {
        flags.add(flag)
        return this
    }

    fun build(): ItemStack {
        val item = ItemStack(material, amount)
        val meta = item.itemMeta

        if (meta != null) {
            if (name != null) {
                meta.displayName(name)
            }

            if (lore != null) {
                meta.lore(lore)
            }

            if (customModelData != null) {
                meta.setCustomModelData(customModelData)
            }

            enchants.forEach { (enchant, level) ->
                meta.addEnchant(enchant, level, true)
            }

            flags.forEach { flag ->
                meta.addItemFlags(flag)
            }

            item.itemMeta = meta
        }

        return item
    }
}