
package com.example.prisma.payroll

import androidx.compose.runtime.staticCompositionLocalOf

fun interface PayrollDocumentPicker {
    fun open(onSelected: (String, ByteArray) -> Unit)
}

val LocalPayrollDocumentPicker =
    staticCompositionLocalOf<PayrollDocumentPicker?> {
        null
    }
