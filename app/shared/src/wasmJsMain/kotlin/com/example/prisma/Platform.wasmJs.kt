package com.example.prisma

class WasmPlatform: Platform {
    override val isWeb: Boolean = true
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()