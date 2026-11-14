package com.example.ui.dialogs

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.SchoolTenant

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MagisterOAuthWebViewDialog(
    authUrl: String,
    tenant: SchoolTenant?,
    onCodeReceived: (code: String, idToken: String?) -> Unit,
    onOpenRawConsole: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0.1f) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showManualTokenDialog by remember { mutableStateOf(false) }
    var manualInput by remember { mutableStateOf("") }
    var loadError by remember { mutableStateOf<String?>(null) }
    var redirectCaptured by remember { mutableStateOf(false) }

    val schoolTitle = if (tenant != null && tenant.subdomain != "accounts" && tenant.subdomain.isNotBlank()) {
        "Inloggen bij ${tenant.name}"
    } else {
        "Inloggen bij Magister"
    }

    fun openInExternalBrowser(ctx: Context, url: String) {
        com.example.util.RawConsoleLogger.info("Browser", "Opening in external browser: $url")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            com.example.util.RawConsoleLogger.error("Browser", "Failed to launch browser: ${e.message}")
        }
    }

    fun parseAndProcessUrlOrToken(rawText: String, fromManualInput: Boolean = false): Boolean {
        val clean = rawText.trim()
        if (clean.isBlank()) return false

        // NEVER match initial authorize request URLs containing code_challenge or connect/authorize
        if (clean.contains("code_challenge") || clean.contains("connect/authorize")) {
            return false
        }

        val normalized = clean.replace("#", "?")
        val uri = try { Uri.parse(normalized) } catch (e: Exception) { null }
        if (uri != null) {
            val code = uri.getQueryParameter("code")
            val idToken = uri.getQueryParameter("id_token")
            val accessToken = uri.getQueryParameter("access_token")

            val tokenOrCode = code?.takeIf { it.isNotBlank() }
                ?: accessToken?.takeIf { it.isNotBlank() }
                ?: idToken

            if (!tokenOrCode.isNullOrBlank()) {
                if (redirectCaptured && !fromManualInput) {
                    return true
                }
                redirectCaptured = true
                webViewRef?.stopLoading()
                com.example.util.RawConsoleLogger.info(
                    "OAuth",
                    "Redirect verwerkt (codeLen=${code?.length ?: 0}, hasIdToken=${!idToken.isNullOrBlank()})"
                )
                onCodeReceived(tokenOrCode, idToken)
                return true
            }
        }

        // If user manually pasted a raw code or token (not an http/https URL)
        if (clean.length >= 15 && !clean.contains(" ") && !clean.startsWith("http://") && !clean.startsWith("https://")) {
            if (redirectCaptured && !fromManualInput) {
                return true
            }
            redirectCaptured = true
            webViewRef?.stopLoading()
            onCodeReceived(clean, null)
            return true
        }

        return false
    }

    val checkAndHandleRedirect: (String) -> Boolean = { url ->
        if (url.startsWith("m6loapp://") || url.startsWith("intent://")) {
            parseAndProcessUrlOrToken(url)
            true
        } else if (url.contains("code=") && !url.contains("code_challenge") && !url.contains("connect/authorize")) {
            parseAndProcessUrlOrToken(url)
        } else false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .testTag("magister_oauth_webview_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar (Zero Gradients)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = schoolTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "accounts.magister.net",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onOpenRawConsole,
                            modifier = Modifier.testTag("open_raw_cli_log_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Raw CLI Log",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = { openInExternalBrowser(context, authUrl) },
                            modifier = Modifier.testTag("open_in_browser_header_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInBrowser,
                                contentDescription = "Open in Chrome",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(
                            onClick = {
                                redirectCaptured = false
                                webViewRef?.reload()
                            },
                            modifier = Modifier.testTag("reload_webview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Herladen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showManualTokenDialog = true },
                            modifier = Modifier.testTag("open_manual_token_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = "Code/Link Invoeren",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("close_webview_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Sluiten",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Chrome Launch & Paste Quick Action Bar (Zero Gradients)
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Log in via browser of in-app:",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clipData = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                    if (!parseAndProcessUrlOrToken(clipData, fromManualInput = true)) {
                                        showManualTokenDialog = true
                                        manualInput = clipData
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.testTag("paste_clipboard_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentPaste,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Plak Link", style = MaterialTheme.typography.labelSmall)
                            }

                            Button(
                                onClick = { openInExternalBrowser(context, authUrl) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("open_in_chrome_banner_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("In Chrome", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Progress Indicator
                if (isLoading) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }

                // Main Content View (WebView + White Background)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.White)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewRef = this
                                setBackgroundColor(android.graphics.Color.WHITE)
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    javaScriptCanOpenWindowsAutomatically = true
                                    // CRITICAL: Keep false so popups & redirects load in main visible WebView!
                                    setSupportMultipleWindows(false)
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    setSupportZoom(true)
                                    builtInZoomControls = false
                                    displayZoomControls = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress / 100f
                                        if (newProgress >= 100) {
                                            isLoading = false
                                        }
                                    }

                                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                                        if (consoleMessage != null) {
                                            val level = when (consoleMessage.messageLevel()) {
                                                android.webkit.ConsoleMessage.MessageLevel.ERROR -> "ERROR"
                                                android.webkit.ConsoleMessage.MessageLevel.WARNING -> "WARN"
                                                else -> "CONSOLE"
                                            }
                                            com.example.util.RawConsoleLogger.log(level, "WebView-JS", "${consoleMessage.message()} [Line ${consoleMessage.lineNumber()} in ${consoleMessage.sourceId()}]")
                                        }
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isLoading = true
                                        loadError = null
                                        com.example.util.RawConsoleLogger.info("WebView-Nav", "Started: $url")
                                        url?.let { checkAndHandleRedirect(it) }
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isLoading = false
                                        com.example.util.RawConsoleLogger.info("WebView-Nav", "Finished: $url")
                                        if (url != null && url.contains("/home/error", ignoreCase = true)) {
                                            view?.evaluateJavascript(
                                                "(function(){ return document.body ? document.body.innerText.trim() : ''; })();"
                                            ) { bodyText ->
                                                if (!bodyText.isNullOrBlank() && bodyText != "null") {
                                                    com.example.util.RawConsoleLogger.error(
                                                        "Magister-ErrorPage",
                                                        bodyText.take(400)
                                                    )
                                                }
                                            }
                                        }
                                        url?.let { checkAndHandleRedirect(it) }
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        com.example.util.RawConsoleLogger.info("WebView-Nav", "Navigating: $url")
                                        if (checkAndHandleRedirect(url)) return true
                                        return false
                                    }

                                    @Suppress("DEPRECATION")
                                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                        if (url == null) return false
                                        com.example.util.RawConsoleLogger.info("WebView-Nav", "Navigating: $url")
                                        if (checkAndHandleRedirect(url)) return true
                                        return false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        errorCode: Int,
                                        description: String?,
                                        failingUrl: String?
                                    ) {
                                        val msg = "Netwerkfout: $description ($errorCode) bij $failingUrl"
                                        loadError = msg
                                        com.example.util.RawConsoleLogger.error("WebView-Net", msg)
                                    }

                                    override fun onReceivedHttpError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        errorResponse: android.webkit.WebResourceResponse?
                                    ) {
                                        val code = errorResponse?.statusCode ?: 0
                                        val url = request?.url?.toString() ?: ""
                                        com.example.util.RawConsoleLogger.error("WebView-HTTP", "HTTP $code ${errorResponse?.reasonPhrase} at $url")
                                    }

                                    override fun onReceivedSslError(
                                        view: WebView?,
                                        handler: android.webkit.SslErrorHandler?,
                                        error: android.net.http.SslError?
                                    ) {
                                        com.example.util.RawConsoleLogger.error("WebView-SSL", "SSL error ${error?.primaryError} on ${error?.url}")
                                        handler?.proceed()
                                    }
                                }

                                loadUrl(authUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (loadError != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(12.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = loadError ?: "Laden mislukt",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = { openInExternalBrowser(context, authUrl) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Open in Chrome / Browser")
                                }
                            }
                        }
                    }
                }

                // Bottom Footer Toolbar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { openInExternalBrowser(context, authUrl) },
                            modifier = Modifier.testTag("open_chrome_footer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Launch,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open in Chrome")
                        }

                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipData = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                                manualInput = clipData
                                showManualTokenDialog = true
                            },
                            modifier = Modifier.testTag("manual_token_footer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Link / Code Plakken")
                        }
                    }
                }
            }
        }
    }

    // Manual Token / Auth Code / URL Input Sheet
    if (showManualTokenDialog) {
        AlertDialog(
            onDismissRequest = { showManualTokenDialog = false },
            title = {
                Text(
                    text = "Plak Browser Link of Code",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Als je hebt ingelogd via Google Chrome, plak hier de volledige redirect URL of autorisatiecode:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clipData = clipboard?.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (clipData.isNotBlank()) manualInput = clipData
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Plak uit klembord")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        label = { Text("Redirect URL of Code") },
                        placeholder = { Text("m6loapp://oauth2redirect/?code=...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_token_input"),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 4
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val input = manualInput.trim()
                        if (input.isNotBlank()) {
                            showManualTokenDialog = false
                            parseAndProcessUrlOrToken(input, fromManualInput = true)
                        }
                    },
                    modifier = Modifier.testTag("submit_manual_token_button")
                ) {
                    Text("Inladen & Verbinden")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualTokenDialog = false }) {
                    Text("Annuleren")
                }
            }
        )
    }
}
