package com.example.prisma.db

import com.example.prisma.config.AppConfig
import com.example.prisma.models.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

object DatabaseFactory {

    fun init() {
        val dbUrl = AppConfig.get(
            "JDBC_DATABASE_URL",
            "jdbc:postgresql://localhost:5432/prisma_db",
        )

        val dbUser = AppConfig.get(
            "DB_USER",
            "postgres",
        )

        val dbPassword = requireNotNull(
            AppConfig.get("DB_PASSWORD"),
        ) {
            "Falta DB_PASSWORD en server/.env"
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

        transaction {
            SchemaUtils.create(
                UsuariosTable,
                ConsentimientosUsuarioTable,
                DispositivosBiometricosTable,
                TarjetasCreditoTable,
                DocumentosTarjetaTable,
                IngresosTable,
                IngresosPlanificacionTable,
                PresupuestosTable,
                TransaccionesTable,
                CategoriasTable,
                EstadosCuentaConfirmadosTable,
                PagosEstadoTable,
                LecturasEstadoTable,
                AnalisisEstadosTable,
                MovimientosAnalizadosTable,
                ConsejosDiariosTable,
            )
        }
    }

    suspend fun <T> dbQuery(block: () -> T): T {
        return newSuspendedTransaction(Dispatchers.IO) {
            block()
        }
    }
}