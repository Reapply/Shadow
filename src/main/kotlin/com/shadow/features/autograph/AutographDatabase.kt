package com.shadow.features.autograph

import com.shadow.Shadow
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.*

/**
 * Database manager for the Autograph Book feature
 * Handles SQLite database operations for storing autographs
 */
object AutographDatabase {
    private const val DATABASE_NAME = "autographs.db"
    private var connection: Connection? = null

    /**
     * Initialize the database connection and create tables if they don't exist
     */
    fun init() {
        try {
            // Load the SQLite JDBC driver
            Class.forName("org.sqlite.JDBC")

            // Create a connection to the database
            val dbFile = Shadow.instance.dataFolder.resolve(DATABASE_NAME)
            val url = "jdbc:sqlite:${dbFile.absolutePath}"

            connection = DriverManager.getConnection(url)

            // Create tables if they don't exist
            createTables()

            Shadow.instance.logger.info("Autograph database initialized successfully")
        } catch (e: Exception) {
            Shadow.instance.logger.severe("Failed to initialize autograph database: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Create the necessary tables for storing autographs
     */
    private fun createTables() {
        val statement = connection?.createStatement() ?: return

        // Create table for autograph requests
        statement.execute("""
            CREATE TABLE IF NOT EXISTS autograph_requests (
                requester_uuid TEXT PRIMARY KEY,
                target_uuid TEXT NOT NULL,
                request_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent())

        // Create table for player autographs
        statement.execute("""
            CREATE TABLE IF NOT EXISTS player_autographs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                player_uuid TEXT NOT NULL,
                autograph_text TEXT NOT NULL,
                signer_name TEXT NOT NULL,
                date TEXT NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """.trimIndent())

        statement.close()
    }

    /**
     * Close the database connection
     */
    fun close() {
        try {
            connection?.close()
        } catch (e: SQLException) {
            Shadow.instance.logger.severe("Error closing database connection: ${e.message}")
        }
    }

    /**
     * Store an autograph request
     * @param requesterUuid UUID of the player requesting the autograph
     * @param targetUuid UUID of the player being asked for an autograph
     * @return true if successful, false otherwise
     */
    fun storeAutographRequest(requesterUuid: UUID, targetUuid: UUID): Boolean {
        try {
            val sql = """
                INSERT OR REPLACE INTO autograph_requests (requester_uuid, target_uuid)
                VALUES (?, ?)
            """.trimIndent()

            val preparedStatement = connection?.prepareStatement(sql) ?: return false
            preparedStatement.setString(1, requesterUuid.toString())
            preparedStatement.setString(2, targetUuid.toString())

            preparedStatement.executeUpdate()
            preparedStatement.close()
            return true
        } catch (e: SQLException) {
            Shadow.instance.logger.severe("Error storing autograph request: ${e.message}")
            return false
        }
    }

    /**
     * Get all pending autograph requests
     * @return Map of requester UUID to target UUID
     */
    fun getAutographRequests(): Map<UUID, UUID> {
        val requests = HashMap<UUID, UUID>()

        try {
            val sql = "SELECT requester_uuid, target_uuid FROM autograph_requests"
            val statement = connection?.createStatement() ?: return requests
            val resultSet = statement.executeQuery(sql)

            while (resultSet.next()) {
                val requesterUuid = UUID.fromString(resultSet.getString("requester_uuid"))
                val targetUuid = UUID.fromString(resultSet.getString("target_uuid"))
                requests[requesterUuid] = targetUuid
            }

            resultSet.close()
            statement.close()
        } catch (e: SQLException) {
            Shadow.instance.logger.severe("Error retrieving autograph requests: ${e.message}")
        }

        return requests
    }

    /**
     * Remove an autograph request
     * @param requesterUuid UUID of the player who requested the autograph
     * @return true if successful, false otherwise
     */
    fun removeAutographRequest(requesterUuid: UUID): Boolean {
        try {
            val sql = "DELETE FROM autograph_requests WHERE requester_uuid = ?"
            val preparedStatement = connection?.prepareStatement(sql) ?: return false
            preparedStatement.setString(1, requesterUuid.toString())

            preparedStatement.executeUpdate()
            preparedStatement.close()
            return true
        } catch (e: SQLException) {
            Shadow.instance.logger.severe("Error removing autograph request: ${e.message}")
            return false
        }
    }

    /**
     * Store a player's autograph
     * @param playerUuid UUID of the player receiving the autograph
     * @param autographText The formatted autograph text
     * @param signerName Name of the player giving the autograph
     * @param date Date when the autograph was given
     * @return true if successful, false otherwise
     */
    fun storeAutograph(playerUuid: UUID, autographText: String, signerName: String, date: String): Boolean {
        try {
            val sql = """
                INSERT INTO player_autographs (player_uuid, autograph_text, signer_name, date)
                VALUES (?, ?, ?, ?)
            """.trimIndent()

            val preparedStatement = connection?.prepareStatement(sql) ?: return false
            preparedStatement.setString(1, playerUuid.toString())
            preparedStatement.setString(2, autographText)
            preparedStatement.setString(3, signerName)
            preparedStatement.setString(4, date)

            preparedStatement.executeUpdate()
            preparedStatement.close()
            return true
        } catch (e: SQLException) {
            Shadow.instance.logger.severe("Error storing autograph: ${e.message}")
            return false
        }
    }

    /**
     * Get all autographs for a player
     * @param playerUuid UUID of the player
     * @return List of autograph texts
     */
    fun getPlayerAutographs(playerUuid: UUID): List<String> {
        val autographs = mutableListOf<String>()

        try {
            val sql = "SELECT autograph_text FROM player_autographs WHERE player_uuid = ? ORDER BY timestamp DESC"
            val preparedStatement = connection?.prepareStatement(sql) ?: return autographs
            preparedStatement.setString(1, playerUuid.toString())

            val resultSet = preparedStatement.executeQuery()

            while (resultSet.next()) {
                autographs.add(resultSet.getString("autograph_text"))
            }

            resultSet.close()
            preparedStatement.close()
        } catch (e: SQLException) {
            Shadow.instance.logger.severe("Error retrieving player autographs: ${e.message}")
        }

        return autographs
    }

    /**
     * Check if a player already has an autograph from a specific signer
     * @param playerUuid UUID of the player
     * @param signerName Name of the signer to check
     * @return true if the player already has an autograph from this signer, false otherwise
     */
    fun hasAutographFromSigner(playerUuid: UUID, signerName: String): Boolean {
        try {
            val sql = "SELECT COUNT(*) FROM player_autographs WHERE player_uuid = ? AND signer_name = ?"
            val preparedStatement = connection?.prepareStatement(sql) ?: return false
            preparedStatement.setString(1, playerUuid.toString())
            preparedStatement.setString(2, signerName)

            val resultSet = preparedStatement.executeQuery()

            if (resultSet.next()) {
                val count = resultSet.getInt(1)
                resultSet.close()
                preparedStatement.close()
                return count > 0
            }

            resultSet.close()
            preparedStatement.close()
        } catch (e: SQLException) {
            Shadow.instance.logger.severe("Error checking if player has autograph from signer: ${e.message}")
        }

        return false
    }
}
