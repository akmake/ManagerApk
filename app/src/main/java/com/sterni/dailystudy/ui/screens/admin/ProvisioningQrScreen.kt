package com.sterni.dailystudy.ui.screens.admin

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

private const val APK_URL = "https://dahanswebsite.com/api/tether/app/download"
private const val APK_CHECKSUM = "xycL3kb4-mXnqvRJV43SyQyqIgU1D4Nw9U1UWdJ8urs"
private const val ADMIN_COMPONENT = "com.sterni.tether/.admin.TetherDeviceAdminReceiver"

private fun buildProvisioningJson(): String = """
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "$ADMIN_COMPONENT",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "$APK_URL",
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "$APK_CHECKSUM",
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": false,
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true,
  "android.app.extra.PROVISIONING_LOCALE": "he_IL",
  "android.app.extra.PROVISIONING_TIME_ZONE": "Asia/Jerusalem"
}
""".trimIndent()

private fun generateQr(content: String, size: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val bits = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
    for (x in 0 until size) for (y in 0 until size)
        bmp.setPixel(x, y, if (bits[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
    return bmp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisioningQrScreen(onBack: () -> Unit) {
    val qrBitmap = remember {
        generateQr(buildProvisioningJson(), 800)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR הגדרת מכשיר") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, "חזרה")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "הוראות למשתמש חדש",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepRow("1", "אפס את הטלפון לגדרות יצרן (Factory Reset)")
                    StepRow("2", "בחלון ההגדרה הראשון — הקש 6 פעמות על המסך")
                    StepRow("3", "סרוק את ה-QR הבא")
                    StepRow("4", "המתן — האפליקציה תותקן אוטומטית עם הרשאות מלאות")
                }
            }

            Box(
                modifier = Modifier
                    .size(280.dp)
                    .background(Color.White)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR הגדרה",
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (APK_CHECKSUM == "REPLACE_WITH_YOUR_CHECKSUM") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(
                        text = "שים לב: יש להגדיר את ה-APK_CHECKSUM בקוד לפני שימוש",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                number,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Text(text, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
}
