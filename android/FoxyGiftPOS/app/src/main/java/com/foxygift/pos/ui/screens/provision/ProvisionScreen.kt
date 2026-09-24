package com.foxygift.pos.ui.screens.provision

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.foxygift.pos.ui.navigation.Routes
import com.foxygift.pos.ui.theme.*
import android.util.Size
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Terminal Provisioning screen with real CameraX + ML Kit QR scanner.
 *
 * Permissions flow:
 *   1. Check if CAMERA permission is already granted
 *   2. If not — show a rationale card and request permission
 *   3. On grant — start CameraX preview + ML Kit analysis
 *   4. On QR detected — decrypt + validate via ProvisionViewModel
 *   5. On success — show success card and navigate to PIN screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisionScreen(
    navController: NavController,
    viewModel: ProvisionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context  = LocalContext.current

    // Camera permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Graphite950, Graphite900)))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = FoxyAmber400)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Terminal Provisioning",
                            color      = FoxyAmber300,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = Graphite400)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Graphite900),
            )

            when (uiState.phase) {
                ProvisionPhase.IDLE -> IdleCard(
                    onScanClicked = {
                        if (hasCameraPermission) {
                            viewModel.startScanning()
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }
                )

                ProvisionPhase.PERMISSION_DENIED -> PermissionDeniedCard(
                    onRetry = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )

                ProvisionPhase.SCANNING -> CameraQrScanner(
                    onQrDetected = { rawQr ->
                        viewModel.onQrScanned(rawQr)
                    },
                    onCancel = { viewModel.reset() },
                )

                ProvisionPhase.PROCESSING -> ProcessingCard()

                ProvisionPhase.SUCCESS -> SuccessCard(
                    locationName = uiState.locationName,
                    networkName  = uiState.networkName,
                    terminalId   = uiState.terminalId,
                    onContinue   = {
                        navController.navigate(Routes.PIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )

                ProvisionPhase.ERROR -> ErrorCard(
                    message  = uiState.errorMessage ?: "Unknown error",
                    onRetry  = { viewModel.startScanning() },
                    onCancel = { viewModel.reset() },
                )
            }
        }

        // Listen for permission denial (user denied while on screen)
        LaunchedEffect(hasCameraPermission) {
            if (!hasCameraPermission && uiState.phase == ProvisionPhase.SCANNING) {
                viewModel.onPermissionDenied()
            }
        }
    }
}

// ─────────────────────── CameraX QR Scanner Composable ───────────────────────

/**
 * Full-screen CameraX preview with ML Kit barcode analyzer.
 * Only fires [onQrDetected] once (guarded by [analysisActive]).
 */
@Composable
fun CameraQrScanner(
    onQrDetected: (String) -> Unit,
    onCancel:     () -> Unit,
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisActive = remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // CameraX PreviewView
        AndroidView(
            factory  = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val cameraExecutor = Executors.newSingleThreadExecutor()
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    // Preview use case
                    val preview = Preview.Builder()
                        .build()
                        .also { it.setSurfaceProvider(previewView.surfaceProvider) }

                    // ML Kit barcode analyzer configured specifically for QR codes
                    val barcodeScanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                            .build()
                    )
                    val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            androidx.camera.core.resolutionselector.ResolutionStrategy(
                                Size(1920, 1080),
                                androidx.camera.core.resolutionselector.ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                            )
                        )
                        .build()
                    val imageAnalysis  = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setResolutionSelector(resolutionSelector)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (!analysisActive.value) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val image = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees,
                            )
                            barcodeScanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val qr = barcodes.firstOrNull {
                                        it.format == Barcode.FORMAT_QR_CODE
                                    }?.rawValue
                                    if (qr != null && analysisActive.value) {
                                        analysisActive.value = false   // prevent double fire
                                        onQrDetected(qr)
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.w("FoxyGift/QR", "Barcode analysis failed: ${e.message}")
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    // Bind to back camera
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis,
                        )
                    }.onFailure { e ->
                        Log.e("FoxyGift/QR", "Camera bind failed: ${e.message}")
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(20.dp)),
        )

        // QR viewfinder overlay
        QrViewfinderOverlay()

        // Cancel button at bottom
        Box(
            modifier          = Modifier.fillMaxSize(),
            contentAlignment  = Alignment.BottomCenter,
        ) {
            OutlinedButton(
                onClick  = onCancel,
                modifier = Modifier.padding(bottom = 16.dp),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = Graphite400),
            ) {
                Icon(Icons.Default.Close, null)
                Spacer(Modifier.width(8.dp))
                Text("Cancel")
            }
        }
    }
}

/**
 * Semi-transparent viewfinder overlay with corner brackets.
 */
@Composable
private fun QrViewfinderOverlay() {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {

        // Instruction text at top
        Box(
            modifier         = Modifier.fillMaxWidth().padding(top = 16.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                color  = Color.Black.copy(alpha = 0.6f),
                shape  = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "Point camera at the FoxyGift QR code",
                    modifier  = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color     = Color.White,
                    style     = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Corner bracket decorations
        val bracketColor = FoxyAmber400
        val bracketSize  = 32.dp
        val strokeWidth  = 3.dp

        // Top-left bracket
        Box(modifier = Modifier.align(Alignment.Center)) {
            Canvas(modifier = Modifier.size(200.dp)) {
                val s = bracketSize.toPx()
                val w = strokeWidth.toPx()
                drawLine(
                    color       = bracketColor,
                    start       = androidx.compose.ui.geometry.Offset(0f, s),
                    end         = androidx.compose.ui.geometry.Offset(0f, 0f),
                    strokeWidth = w,
                )
                drawLine(
                    color       = bracketColor,
                    start       = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end         = androidx.compose.ui.geometry.Offset(s, 0f),
                    strokeWidth = w,
                )
                // Top-right
                drawLine(
                    color       = bracketColor,
                    start       = androidx.compose.ui.geometry.Offset(size.width, s),
                    end         = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = w,
                )
                drawLine(
                    color       = bracketColor,
                    start       = androidx.compose.ui.geometry.Offset(size.width - s, 0f),
                    end         = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = w,
                )
                // Bottom-left
                drawLine(
                    color       = bracketColor,
                    start       = androidx.compose.ui.geometry.Offset(0f, size.height - s),
                    end         = androidx.compose.ui.geometry.Offset(0f, size.height),
                    strokeWidth = w,
                )
                drawLine(
                    color       = bracketColor,
                    start       = androidx.compose.ui.geometry.Offset(0f, size.height),
                    end         = androidx.compose.ui.geometry.Offset(s, size.height),
                    strokeWidth = w,
                )
                // Bottom-right
                drawLine(
                    color       = bracketColor,
                    start       = androidx.compose.ui.geometry.Offset(size.width, size.height - s),
                    end         = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = w,
                )
                drawLine(
                    color       = bracketColor,
                    start       = androidx.compose.ui.geometry.Offset(size.width - s, size.height),
                    end         = androidx.compose.ui.geometry.Offset(size.width, size.height),
                    strokeWidth = w,
                )
            }
        }
    }
}

// ─────────────────────── State Cards ─────────────────────────────────────────

@Composable
private fun IdleCard(onScanClicked: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Surface(color = Graphite800, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    null,
                    tint     = FoxyAmber500,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Scan Provisioning QR",
                    style      = MaterialTheme.typography.titleLarge,
                    color      = Graphite100,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "The QR code is generated by the FoxyGift Vendor Tool.\nIt configures this terminal for your merchant network.",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = Graphite400,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick  = onScanClicked,
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = FoxyAmber700),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(10.dp))
                    Text("OPEN CAMERA & SCAN QR", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PermissionDeniedCard(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color(0xFF7F1D1D), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.NoPhotography, null, tint = FoxyError, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Camera Permission Denied", style = MaterialTheme.typography.titleMedium,
                     color = FoxyError, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Camera access is required to scan the provisioning QR code.\nGo to Settings → Apps → FoxyGift POS → Permissions.",
                     style = MaterialTheme.typography.bodySmall, color = Color(0xFFFEE2E2),
                     textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = FoxyError)) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Request Permission Again")
                }
            }
        }
    }
}

@Composable
private fun ProcessingCard() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = FoxyAmber500, modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
            Text("Validating QR payload…", style = MaterialTheme.typography.titleMedium, color = Graphite300)
            Text("Decrypting provisioning data", style = MaterialTheme.typography.bodySmall, color = Graphite500)
        }
    }
}

@Composable
private fun SuccessCard(
    locationName: String,
    networkName:  String,
    terminalId:   String,
    onContinue:   () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color(0xFF064E3B), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CheckCircle, null, tint = FoxySuccess, modifier = Modifier.size(72.dp))
                Spacer(Modifier.height(12.dp))
                Text("Terminal Provisioned! ✓", style = MaterialTheme.typography.titleLarge,
                     color = FoxySuccess, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(16.dp))

                // Config summary
                listOf(
                    "📍 Location"  to locationName,
                    "🌐 Network"   to networkName,
                    "🖥 Terminal"  to terminalId,
                ).forEach { (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6EE7B7))
                        Text(value, style = MaterialTheme.typography.bodySmall, color = Color.White,
                             fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick  = onContinue,
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = FoxySuccess),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Default.Lock, null)
                    Spacer(Modifier.width(10.dp))
                    Text("CONTINUE TO LOGIN", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
        Surface(color = Color(0xFF7F1D1D), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ErrorOutline, null, tint = FoxyError, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(12.dp))
                Text("Provisioning Failed", style = MaterialTheme.typography.titleMedium,
                     color = FoxyError, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall,
                     color = Color(0xFFFEE2E2), textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onCancel) { Text("Cancel", color = Graphite400) }
                    Button(
                        onClick = onRetry,
                        colors  = ButtonDefaults.buttonColors(containerColor = FoxyError),
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Scan Again")
                    }
                }
            }
        }
    }
}
