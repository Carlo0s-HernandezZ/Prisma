plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlinSerialization)
}

group = "com.example.prisma"
version = "1.0.0"
application {
    mainClass = "com.example.prisma.ApplicationKt"
}

dependencies {
    api(project(":core"))
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
    implementation(libs.exposed.java.time)



    // Autenticación JWT y Contenido JSON para Ktor
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.jwt)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.serialization.json)

    // Seguridad (Bcrypt) y Driver PostgreSQL y TOTP
    implementation(libs.bcrypt)
    implementation(libs.postgresql)
    implementation(libs.totp)


    // HikariCP (Gestor de conexiones)
    implementation(libs.hikariCP)

    // Exposed Framework (ORM)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.dao)


}