
package com.example.prisma

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.prisma.notifications.CardNotificationNotifier
import com.example.prisma.notifications.CardReminderReceiver

import com.example.prisma.payroll.AndroidPayrollDocumentPickerProvider

/**
 * FragmentActivity permite utilizar BiometricPrompt
 * para la autenticación biométrica de PRISMA.
 *
 * AndroidPayrollDocumentPickerProvider permite
 * seleccionar recibos de nómina PDF y XML.
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Inicializar notificador y canal de notificaciones en Android
        CardNotificationNotifier.init(this)
        CardReminderReceiver.createNotificationChannel(this)

        // Solicitar permiso de notificaciones push en Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {

            // Proveedor del selector de documentos Android
            AndroidPayrollDocumentPickerProvider {

                // Aplicación principal Kotlin Multiplatform
                App()
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
