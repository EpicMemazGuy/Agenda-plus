package com.example.ui.dialogs

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.components.appCardShape
import com.example.ui.components.isNothingOs
import com.example.ui.components.nothingCardBorder
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

@Composable
fun QrPairingDialog(
    onPairUri: (String) -> Result<Unit>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current
    val nothingMode = isNothingOs

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var manualUriInput by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    var handledScan by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = appCardShape(standard = 24.dp, nothing = 24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = nothingCardBorder(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .testTag("qr_pairing_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (nothingMode) "KOPPEL MET AGENDA+ WEB" else "Koppel met Agenda+ Web",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("qr_dialog_close_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Sluiten")
                    }
                }

                Text(
                    text = "Open agendaplus.nl op je computer, ga naar Instellingen -> Synchronisatie en scan de QR-code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // CameraX + ML Kit Barcode Scanner Viewport
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.Black.copy(alpha = 0.9f))
                        .border(
                            BorderStroke(
                                1.5.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                            ),
                            RoundedCornerShape(18.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission && cameraError == null) {
                        val barcodeScanner = remember {
                            val options = BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build()
                            BarcodeScanning.getClient(options)
                        }
                        val cameraController = remember(context) {
                            LifecycleCameraController(context).apply {
                                cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
                            }
                        }

                        DisposableEffect(cameraController, barcodeScanner, lifecycleOwner) {
                            val executor = ContextCompat.getMainExecutor(context)
                            val analyzer = MlKitAnalyzer(
                                listOf(barcodeScanner),
                                CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED,
                                executor
                            ) { result ->
                                if (handledScan) return@MlKitAnalyzer
                                val barcodes = result?.getValue(barcodeScanner) ?: return@MlKitAnalyzer
                                val rawValue = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                    ?: return@MlKitAnalyzer
                                if (rawValue.trim().startsWith("agendaplus://", ignoreCase = true)) {
                                    handledScan = true
                                    val pairRes = onPairUri(rawValue.trim())
                                    if (pairRes.isSuccess) {
                                        onDismiss()
                                    } else {
                                        handledScan = false
                                        validationError = pairRes.exceptionOrNull()?.localizedMessage
                                            ?: "Ongeldige QR-code."
                                    }
                                } else {
                                    validationError = "Geen Agenda+ QR-code herkend."
                                }
                            }

                            runCatching {
                                cameraController.setImageAnalysisAnalyzer(executor, analyzer)
                                cameraController.bindToLifecycle(lifecycleOwner)
                            }.onFailure { err ->
                                cameraError = err.localizedMessage ?: "Camera niet beschikbaar"
                            }

                            onDispose {
                                runCatching { cameraController.clearImageAnalysisAnalyzer() }
                                runCatching { cameraController.unbind() }
                                runCatching { barcodeScanner.close() }
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                PreviewView(ctx).apply {
                                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                    controller = cameraController
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Aiming reticle overlay
                        Box(
                            modifier = Modifier
                                .size(156.dp)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(16.dp)
                                )
                        )
                    } else {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = cameraError
                                    ?: "Cameratoegang is vereist om de QR-code te scannen. Je kunt ook de koppel-URI hieronder plakken.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center
                            )
                            if (cameraError == null) {
                                FilledTonalButton(
                                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Text("Camera toestaan")
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text(
                    text = "Of plak de koppel-URI handmatig:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = manualUriInput,
                    onValueChange = {
                        manualUriInput = it
                        validationError = null
                    },
                    label = { Text("agendaplus://pair?id=...&key=...") },
                    singleLine = false,
                    maxLines = 3,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val pasted = clipboard.getText()?.text?.trim().orEmpty()
                                if (pasted.isNotEmpty()) {
                                    manualUriInput = pasted
                                    validationError = null
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Plak uit klembord"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_uri_input")
                )

                validationError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag("qr_validation_error")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Text("Annuleren")
                    }
                    Button(
                        onClick = {
                            val res = onPairUri(manualUriInput)
                            if (res.isSuccess) {
                                manualUriInput = ""
                                onDismiss()
                            } else {
                                validationError = res.exceptionOrNull()?.localizedMessage
                                    ?: "Ongeldige koppel-URI."
                            }
                        },
                        enabled = manualUriInput.isNotBlank(),
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("confirm_pair_button")
                    ) {
                        Text("Koppelen")
                    }
                }
            }
        }
    }
}
