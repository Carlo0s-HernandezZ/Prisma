package com.example.prisma.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory

object DatabaseFactory {
    private val log = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init() {
        val dbUrl = System.getenv("JDBC_DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/prisma_db"
        val dbUser = System.getenv("DB_USER") ?: "postgres"
        val dbPassword = System.getenv("DB_PASSWORD")
            ?: "postgres".also {
                log.warn("DB_PASSWORD no está definida: usando la contraseña por defecto de desarrollo. No uses esto en producción.")
            }

        val config = HikariConfig().apply {
            driverClassName = "org.postgresql.Driver"
            jdbcUrl = dbUrl
            username = dbUser
            password = dbPassword
            maximumPoolSize = 3
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        Database.connect(HikariDataSource(config))
    }

    suspend fun <T> dbQuery(block: () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) {
            block()
        }
}
