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
    implementation("org.apache.pdfbox:pdfbox:3.0.8")
    implementation(libs.logback)
    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serializationKotlinxJson)
    implementation(libs.ktor.serverCors)
    // Seguridad (Actividad 3.2): JWT, cabeceras, redirección HTTPS, HSTS, errores sin trazas y Swagger.
    val ktorVersion = libs.versions.ktor.get()
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-default-headers:$ktorVersion")
    implementation("io.ktor:ktor-server-http-redirect:$ktorVersion")
    implementation("io.ktor:ktor-server-hsts:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.javaTime)
    implementation(libs.hikari)
    implementation(libs.postgresql)
    implementation(libs.bcrypt)
    implementation("com.sun.mail:jakarta.mail:2.0.1")
    implementation("org.apache.pdfbox:pdfbox:3.0.1")
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.kotlin.testJunit)
}
