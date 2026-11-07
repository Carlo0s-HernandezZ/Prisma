
package com.example.prisma

import android.os.Bundle

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity

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