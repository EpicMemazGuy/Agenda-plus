package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.model.SchoolProvider
import com.example.model.SchoolTenant
import com.example.network.PkceHelper
import com.example.ui.AuthUiError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginDialog(
    provider: SchoolProvider,
    availableTenants: List<SchoolTenant>,
    currentTenant: SchoolTenant?,
    activeVerifier: String,
    isAuthenticating: Boolean,
    authError: AuthUiError?,
    onDismiss: () -> Unit,
    onLogin: (SchoolTenant, String) -> Unit,
    onOpenWebViewLogin: (SchoolTenant) -> Unit,
    onClearError: () -> Unit
) {
    val providerTenants = remember(provider, availableTenants) {
        availableTenants.filter { it.provider == provider }
    }

    var selectedTenant by remember(currentTenant, providerTenants) {
        mutableStateOf(currentTenant?.takeIf { it.provider == provider } ?: providerTenants.firstOrNull() ?: SchoolTenant("c1", "Mijn School", "Nederland", "school", provider))
    }
    var tenantDropdownExpanded by remember { mutableStateOf(false) }
    var authCodeInput by remember { mutableStateOf("") }
    var customSubdomain by remember { mutableStateOf("") }
    var isCustomTenant by remember { mutableStateOf(false) }
    var showTechnicalDetails by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .testTag("school_login_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.School,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Magister Koppeling",
                                style = MaterialTheme.typography.titleMedium,
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

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_login_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Sluiten",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Diagnostic Error Banner (When authentication fails)
                authError?.let { err ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .testTag("auth_error_banner")
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = err.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = err.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = err.technicalDetails,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }

                // School Tenant Selector
                Text(
                    text = "Selecteer je school (${provider.displayName}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (!isCustomTenant) {
                    ExposedDropdownMenuBox(
                        expanded = tenantDropdownExpanded,
                        onExpandedChange = { tenantDropdownExpanded = !tenantDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = "${selectedTenant.name} (${selectedTenant.city})",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tenantDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                                .testTag("tenant_selector_field"),
                            shape = RoundedCornerShape(14.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = tenantDropdownExpanded,
                            onDismissRequest = { tenantDropdownExpanded = false }
                        ) {
                            providerTenants.forEach { tenant ->
                                DropdownMenuItem(
                                    text = { Text("${tenant.name} (${tenant.city})") },
                                    onClick = {
                                        selectedTenant = tenant
                                        tenantDropdownExpanded = false
                                        onClearError()
                                    }
                                )
                            }
                        }
                    }

                    TextButton(
                        onClick = { isCustomTenant = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Mijn school staat er niet tussen")
                    }
                } else {
                    OutlinedTextField(
                        value = customSubdomain,
                        onValueChange = { customSubdomain = it.trim() },
                        placeholder = { Text("bv. mijnschool") },
                        label = { Text("Schooldomein (zonder .magister.net)") },
                        trailingIcon = {
                            TextButton(onClick = {
                                if (customSubdomain.isNotBlank()) {
                                    selectedTenant = SchoolTenant(
                                        id = customSubdomain,
                                        name = customSubdomain.replaceFirstChar { it.uppercase() },
                                        city = "Nederland",
                                        subdomain = customSubdomain.lowercase(),
                                        provider = provider
                                    )
                                }
                                isCustomTenant = false
                            }) {
                                Text("OK")
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // PRIMARY ACTION: Real Web Login in OAuth WebView
                Button(
                    onClick = {
                        val effectiveTenant = if (isCustomTenant && customSubdomain.isNotBlank()) {
                            SchoolTenant(customSubdomain, customSubdomain, "Nederland", customSubdomain.lowercase(), provider)
                        } else selectedTenant

                        onOpenWebViewLogin(effectiveTenant)
                    },
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("open_web_login_button")
                ) {
                    Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Inloggen via Magister",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                Spacer(modifier = Modifier.height(14.dp))

                // SECONDARY OPTION: Manual Auth Code or Bearer Token input
                Text(
                    text = "Of handmatige code / Bearer Token:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = authCodeInput,
                    onValueChange = { authCodeInput = it },
                    placeholder = { Text("Auth code of Bearer token") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_code_input"),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        val effectiveTenant = if (isCustomTenant && customSubdomain.isNotBlank()) {
                            SchoolTenant(customSubdomain, customSubdomain, "Nederland", customSubdomain.lowercase(), provider)
                        } else selectedTenant

                        onLogin(effectiveTenant, authCodeInput)
                    },
                    enabled = !isAuthenticating && authCodeInput.isNotBlank(),
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("submit_manual_token_button")
                ) {
                    if (isAuthenticating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifiëren bij ${provider.displayName}...")
                    } else {
                        Text("Handmatig Inloggen & Data Ophalen")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // OAuth2 / PKCE Parameter Details Toggle
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "OAuth2 Specificaties (${provider.displayName})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            TextButton(
                                onClick = { showTechnicalDetails = !showTechnicalDetails },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (showTechnicalDetails) "Verberg" else "Details")
                            }
                        }

                        if (showTechnicalDetails) {
                            Text(
                                text = "Systeem: Magister\nServer: ${selectedTenant.subdomain}.magister.net\nRedirect: m6loapp://oauth2redirect/\nChallenge S256: ${PkceHelper.generateChallenge(activeVerifier).take(16)}...",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
