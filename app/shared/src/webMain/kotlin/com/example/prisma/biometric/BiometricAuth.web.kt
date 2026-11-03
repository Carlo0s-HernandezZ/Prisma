package com.example.prisma.biometric

import androidx.compose.runtime.Composable

/** En la web no hay huella ni Face ID: el acceso siempre es con contraseña + código 2FA. */
@Composable
actual fun rememberBiometricAuth(): BiometricAuth = UnsupportedBiometricAuth
