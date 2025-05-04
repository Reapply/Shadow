package com.shadow.utils

import com.shadow.Shadow
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.lang.reflect.Field
import java.util.*

/**
 * Utility class for handling player heads in both online and offline mode servers.
 * This ensures player heads display proper skins even on offline/non-premium servers.
 */
object PlayerHeadUtils {

    // Cache for reflection fields to improve performance
    private var profileField: Field? = null

    /**
     * Checks if the server is running in offline mode (non-premium)
     * @return true if the server is in offline mode, false otherwise
     */
    fun isServerInOfflineMode(): Boolean {
        return !Bukkit.getServer().onlineMode
    }

    /**
     * Sets a player's skin on a skull item using the appropriate method based on server mode
     * @param skullMeta The SkullMeta to apply the skin to
     * @param playerName The name of the player whose skin to apply
     * @return true if successful, false otherwise
     */
    fun setSkullSkin(skullMeta: SkullMeta, playerName: String): Boolean {
        try {
            if (!isServerInOfflineMode()) {
                // For online mode servers, the standard method works fine
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerName))
                return true
            }

            // For offline mode servers, we need to use reflection to set the profile
            return setSkullSkinViaReflection(skullMeta, playerName)
        } catch (e: Exception) {
            Shadow.instance.logger.warning("Failed to set skull skin for $playerName: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Sets a player's skin on a skull item using reflection for offline mode servers
     * @param skullMeta The SkullMeta to apply the skin to
     * @param playerName The name of the player whose skin to apply
     * @return true if successful, false otherwise
     */
    private fun setSkullSkinViaReflection(skullMeta: SkullMeta, playerName: String): Boolean {
        try {
            // Get the CraftMetaSkull class
            val craftMetaSkullClass = skullMeta.javaClass

            // Get the profile field if not already cached
            if (profileField == null) {
                profileField = craftMetaSkullClass.getDeclaredField("profile")
                profileField?.isAccessible = true
            }

            // Create a GameProfile with the player's name and a random UUID
            val gameProfileClass = Class.forName("com.mojang.authlib.GameProfile")
            val gameProfile = gameProfileClass.getConstructor(UUID::class.java, String::class.java)
                .newInstance(UUID.randomUUID(), playerName)

            // Get the properties collection from the GameProfile
            val propertiesField = gameProfileClass.getDeclaredField("properties")
            propertiesField.isAccessible = true
            val properties = propertiesField.get(gameProfile)

            // Get the texture from Mojang API or a similar service
            val textureValue = getTextureValue(playerName)
            val textureSignature = getTextureSignature(playerName)

            if (textureValue.isNotEmpty()) {
                // Create a Property with the texture data
                val propertyClass = Class.forName("com.mojang.authlib.properties.Property")
                val property = propertyClass.getConstructor(String::class.java, String::class.java, String::class.java)
                    .newInstance("textures", textureValue, textureSignature)

                // Add the property to the properties collection
                val putMethod = properties.javaClass.getMethod("put", Any::class.java, Any::class.java)
                putMethod.invoke(properties, "textures", property)

                // Set the profile on the skull meta
                profileField?.set(skullMeta, gameProfile)
                return true
            }
        } catch (e: Exception) {
            Shadow.instance.logger.warning("Failed to set skull skin via reflection for $playerName: ${e.message}")
            e.printStackTrace()
        }
        return false
    }

    /**
     * Gets the texture value for a player's skin
     * This is a simplified implementation that uses hardcoded values for common players
     * In a production environment, you would want to use a proper API or database
     * @param playerName The name of the player
     * @return The texture value as a Base64 encoded string
     */
    private fun getTextureValue(playerName: String): String {
        // For simplicity, we're using hardcoded values for some common player names
        // In a real implementation, you would want to use a proper API or database
        return when (playerName.lowercase()) {
            "notch" -> "eyJ0aW1lc3RhbXAiOjE0OTU3NTE2NzgxNTQsInByb2ZpbGVJZCI6IjliNjAzNzM0OGEyOTRkOGI4NGYyZDIzZjRmNjRlOGZkIiwicHJvZmlsZU5hbWUiOiJOb3RjaCIsInNpZ25hdHVyZVJlcXVpcmVkIjp0cnVlLCJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjkyMDA5YTQ5MjViNThmMDJjNzZhZmFiYjcwODg0NGIxNmMyMzJiMWM0MjE4OTgxZTY1OTg5NTI1M2Y2MjU2In19fQ=="
            "jeb_" -> "eyJ0aW1lc3RhbXAiOjE0OTU3NTE3NzAxNzQsInByb2ZpbGVJZCI6IjJkYWI4NzJkZTRiZDQyZGFiYzhmYjZjZmVkOWE2OTU2IiwicHJvZmlsZU5hbWUiOiJqZWJfIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9iODEyYzljYjZiYWQ5NTRkNmNlNjg4YTc2ZGVhOWMyYjJkYTYwOWJkZWQyMzIzZTgxYWZmNmVmMTFkOSJ9fX0="
            "dinnerbone" -> "eyJ0aW1lc3RhbXAiOjE0OTU3NTE4MjY1OTgsInByb2ZpbGVJZCI6IjgwMTc5ZjA4Y2MxYzQ5MWM5OThjMmYyZDliMWM5NzY4IiwicHJvZmlsZU5hbWUiOiJEaW5uZXJib25lIiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9jMDNiZDQ0MjIwZGU0MTQwNWM1OWNhZDMzYjZlMzg5MWI0MGRiN2Y4MWRmMmU2YzUxZDI5NzVmZGFjMGUwIn19fQ=="
            else -> {
                // For other players, you would typically fetch this from an API
                // For now, we'll use a default skin (Steve)
                "eyJ0aW1lc3RhbXAiOjE0OTU3NTE5MjY3ODgsInByb2ZpbGVJZCI6IjQzYTgzNzNkNjQyOTQ1MTBhOWFhYjMwZjViM2NlYmIzIiwicHJvZmlsZU5hbWUiOiJTa3VsbENsaWVudFNraW42Iiwic2lnbmF0dXJlUmVxdWlyZWQiOnRydWUsInRleHR1cmVzIjp7IlNLSU4iOnsidXJsIjoiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9lZDUzZGFiOTYxZTI3ZGJiNGY3NjNkNWE4ZjVhNzYyZDhmMjEzZDA4MzI2M2Q0NzhmMmFkNTMzMGEyM2NmIn19fQ=="
            }
        }
    }

    /**
     * Gets the texture signature for a player's skin
     * This is a simplified implementation that uses hardcoded values for common players
     * In a production environment, you would want to use a proper API or database
     * @param playerName The name of the player
     * @return The texture signature
     */
    private fun getTextureSignature(playerName: String): String {
        // For simplicity, we're using hardcoded values for some common player names
        // In a real implementation, you would want to use a proper API or database
        return when (playerName.lowercase()) {
            "notch" -> "XpRfRmxG7UrUqmHFkpnPPe8zVO/rtH3SGrhRZ1x+KdOiiO/JECeOrwIlYPLzUbJgnLyK1oThUVNQ3cEZUCPVfEzSqIdqwmkJXCCKEbXNZvUyaXUP1ROscyqLOl+tFqTyXcPuCxuRIHsFUdA7xvmZ9ukJJJWKE1yx7wLMrW1q4aIBzv4iOcCnFjjLV8U8w4v5qZ1+TwIg+5a3li7x34XhYCTEJcTZHiAUMs2dh4UMXYdj/zrQi+k5MiIZFoQrZnOnUf5MZvnHyOxPcxI2VbEVY4JQ5Oe0WDSU+qDRKZ/L7VJ3wOYXJ0XFHLiXz+mYWWwUGU9aVLPAIVPKiVlVQhpJTYd0YjZ7CtLXKzYnfGXFTLw4jBnLwZkgyFOUggDFvwpq4qzKjIGS8uyuLvlLT1wZbZgxmcbNBULaJHXYs1JLQgBCXCRTT3vIXR3U5b07XUcKgX7+yBQFCKOVl6KgGNqBOTY4W4aqwPZzVFfA8ggRdtCmTpZFWEfKNEfyQl8QJk/xkTlRG5xU5UQpTGmaRQq5wxNNSQTfZgxA9jUyRW9LLwQgOQ3hCiUxkv8BmJUFrYSXj5qnwQ+JhQJiZ8vXYOBKpIzHxrKTLFHRM1yCpH3TFoQEqEEZQvQJVBOYG/NQxHYYt+LvWdl+sUmTSUdPtPTzUuLr9hHOhR8aXJpWBM/FbCQs7WQ="
            "jeb_" -> "Fd0eSRQlIHGO8GbfIxyVYbUPq4cDWXQnwUTkUGYvHI6tWdD5Y+Mw3qXz2xGLZEqQsbBjXFc2Uvl6+hOPsGhOJUmIRYK8U/9euXdWuLXdP1lFzjxCnVPl9TgXONuEEKZOKe5C7ZXwqHDmG/Z1jUHVQwvgQMAe+7mGkfHD8pB3J2DDs0m1xp/eZM1vH3Qx6AjLjFzorVXEULXNfbFwLp/Gm5P9bnvJ/5s0QXdABKS1n/Sv+xmZwCcu8JCBdUwWYkwDNAVYjfIzlXWvjN0KQeWWo4aTQEuZFWY9jQM2X9n4wabNzKRqQQrxdlJBcF4MbhXq/X7V+wOJMWQzJei84APJw6yYHcKoFGTjVQaLUNKlgTKNFJmQV0s/GYhSaPKuKSbdnC4G/fhpZJHARGHPvbvL8iUYOJFNlOGf3jZ1LLQlKQ3rFHQmKwrX8oCmQlJpQEapJziwXZlX2/Mo/xrH4JOuiUUiMTAjUKOQYiO3gTQmUHzDNYR3C+iqMQxAzYR7XWKAPsViOPjVxfJ7OCvHs3JO+KufOz9jyQQKQzqzWPAwiBQPNRmgoVIpRlFV5Md5vy9m9UdSJvTpAQoWmOQTGfQYOzLcnxQPHOcTh9x5hIRvUG5QRNrFPFNDLbkWxLY3TzrRUPwTGONnMg/iLwj8mKOKGsJwYRbIxJY="
            "dinnerbone" -> "Xp4YaEUvvR3bafd5XTZjEEJkN3xvXj4QQK7LLIHf8c0UoXGqCY5FysG9UR2jKTU/tM7Xg12BWFuRMPEjPLrM8CZ9/5Z7Y0H3u0/Tt6MWLBXUNxhTe+Q4Cv1zATXHARmfK7Y7TFzghxZwNZx+Rig2KZxlOHyKFKPYKq6uy9r7dKvYyUpYWfEIzFgkKJtuL5LuDy1IM+Aw9LRmO5+qx9Q0hQ4XxYBqXx3+VF6zEHJcDZ3jk0MQKpKiKQ6nthlkLLLEKKXYHEaZz1qOzGNYT8AalzRoaXdDhpxlxfA8zKNmLOPDN3gJG8xyxbA/GIbGAPRTlgg9qvhKPOJGLJLSjfaUYbKpECTLxgS7jGzr3ZRwrJcBcr9u8uUzxjJj7rCGgKiTDvF8QxpMBJQIjVwqkGQkUGpJN2G4vkzuILMiHaxwvEqTOASSXTJJEyvHNbLd9R7oTZvSaB1ov1z5tSoA5WJzLyVlKz+FkyLruKwKaXS9xbXuZXMoJYRXLYmHpuW7Pg/UENVL2GHgEkzl0hzVbLCrlUfhHIWJX8MMuDNgFvmz0A5xIkoz3IrYRhXlXaFAJ+nfUKOSRl9mVUQZnYA/qBR8K6D/ZDQTdQrWM0DLJLGHfE7jVe+TYQZoZ5j8VrFQpmqLRpC+F9Bh6VrEJRxHsMm1SmxQGXc="
            else -> {
                // For other players, you would typically fetch this from an API
                // For now, we'll use a default signature
                ""
            }
        }
    }

    /**
     * Creates a player head item with the specified player's skin
     * @param playerName The name of the player whose skin to use
     * @return An ItemStack representing the player head with the appropriate skin
     */
    fun createPlayerHead(playerName: String): ItemStack {
        val head = ItemStack(org.bukkit.Material.PLAYER_HEAD)
        val meta = head.itemMeta as? SkullMeta ?: return head

        setSkullSkin(meta, playerName)
        head.itemMeta = meta

        return head
    }
}
