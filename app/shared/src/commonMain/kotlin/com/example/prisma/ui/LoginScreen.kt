package com.example.prisma.ui

// import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.prisma.network.AuthApiClient
import kotlinx.coroutines.launch

// LOGO (pendiente):
// import com.example.prisma.resources.Res
// import com.example.prisma.resources.prisma_logo
// import org.jetbrains.compose.resources.painterResource

// Colores
private val Cream = Color(0xFFFAF7EE)
private val Orange = Color(0xFFFF8C45)
private val Yellow = Color(0xFFFDAA2C)
private val Green = Color(0xFF0B4A1F)

@Composable
private fun PrismaField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                placeholder,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = Green
            )
        },
        singleLine = true,
        visualTransformation = visualTransformation,
        textStyle = TextStyle(color = Green, textAlign = TextAlign.Center),
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Cream,
            unfocusedContainerColor = Cream,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PrismaButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Yellow, contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (accessToken: String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totpCode by remember { mutableStateOf("") }

    var step by remember { mutableStateOf(1) } // 1: Login, 2: 2FA
    var tempToken by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val api = remember { AuthApiClient() }

    Column(modifier = Modifier.fillMaxSize()) {

        // Parte de arriba: fondo crema con el logo
        Box(
            modifier = Modifier.fillMaxWidth().weight(0.38f).background(Cream),
            contentAlignment = Alignment.Center
        ) {
            Text("PRISMA", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))
            // LOGO (pendiente): descomenta cuando Res funcione
            // Image(
            //     painter = painterResource(Res.drawable.prisma_logo),
            //     contentDescription = "Prisma",
            //     modifier = Modifier.fillMaxWidth(0.6f)
            // )

        }

        // Parte de abajo: fondo naranja con el formulario
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.62f)
                .background(Orange)
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Login", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text("Sign in to continue", fontSize = 14.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(24.dp))

            if (step == 1) {
                // PASO 1: Email y Contraseña
                PrismaField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "EMail"
                )
                Spacer(modifier = Modifier.height(12.dp))
                PrismaField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = "Password",
                    visualTransformation = PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(16.dp))

                PrismaButton(text = "Log in", onClick = {
                    scope.launch {
                        try {
                            val res = api.login(email, password)
                            tempToken = res.temp_token
                            step = 2 // Pasar a la pantalla de 2FA
                        } catch (e: Exception) {
                            // Muestra el mensaje de error técnico real en pantalla
                            errorMessage = "Error de red/app: ${e::class.simpleName} - ${e.message}"
                        }
                    }
                })

                Spacer(modifier = Modifier.height(24.dp))
                Text("Forgot Password", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Green)
                Text("www.TDSoft.com", fontSize = 10.sp, color = Green)
            } else {
                // PASO 2: Código 2FA / TOTP de 6 Dígitos
                Text(
                    "Ingresa el código TOTP de 6 dígitos de tu app autenticadora:",
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))

                PrismaField(
                    value = totpCode,
                    onValueChange = { if (it.length <= 6) totpCode = it },
                    placeholder = "Código 2FA (6 dígitos)"
                )
                Spacer(modifier = Modifier.height(16.dp))

                PrismaButton(text = "Verificar Código", onClick = {
                    scope.launch {
                        try {
                            val res = api.verify2Fa(tempToken, totpCode)
                            // Guardar tokens en KeyStore / EncryptedSharedPreferences y redirigir
                            onLoginSuccess(res.access_token)
                        } catch (e: Exception) {
                            errorMessage = "Código 2FA inválido o expirado"
                        }
                    }
                })
            }

            errorMessage?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }
        }
    }
}