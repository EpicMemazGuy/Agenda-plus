package com.example.network

import android.content.Context
import com.example.data.AppPreferencesStore
import com.example.model.AuthSession
import com.example.util.RawConsoleLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class TokenAcquireResult {
    data class Valid(val session: AuthSession, val accessToken: String) : TokenAcquireResult()
    data class RequiresReauth(val reason: String) : TokenAcquireResult()
    data class TransientFailure(val message: String, val fallbackToken: String? = null) : TokenAcquireResult()
    data object NotAuthenticated : TokenAcquireResult()
}

/**
 * Thread-safe single-flight Magister OAuth2 access/refresh token manager.
 * Guarantees concurrent workers and foreground syncs never burn a rotating single-use refresh token.
 */
object MagisterTokenManager {

    private const val PROACTIVE_REFRESH_BUFFER_MS = 300_000L // 5 minutes
    private val refreshMutex = Mutex()

    // Test hook allowing deterministic unit tests for single-flight refresh concurrency.
    internal var refreshExecutorOverride: (suspend (String) -> TokenRefreshResult)? = null
    internal var clockMsProvider: () -> Long = { System.currentTimeMillis() }

    fun isTokenExpiringSoon(
        session: AuthSession,
        nowMs: Long = clockMsProvider(),
        client: MagisterClient = MagisterClient()
    ): Boolean {
        val token = session.accessToken
        if (token.isNullOrBlank()) return true
        if (session.expiresAt > 0L && nowMs >= (session.expiresAt - PROACTIVE_REFRESH_BUFFER_MS)) {
            return true
        }
        val payload = PkceHelper.decodeJwtPayload(token)
        val expSec = payload?.optLong("exp", 0L) ?: 0L
        if (expSec > 0L) {
            val expMs = expSec * 1000L
            if (nowMs >= (expMs - PROACTIVE_REFRESH_BUFFER_MS)) {
                return true
            }
        }
        return client.isAccessTokenExpired(token, bufferSeconds = PROACTIVE_REFRESH_BUFFER_MS / 1000L)
    }

    suspend fun getValidAccessToken(
        context: Context,
        forceRefresh: Boolean = false,
        client: MagisterClient = MagisterClient(),
        onSessionUpdated: ((AuthSession) -> Unit)? = null
    ): TokenAcquireResult {
        val appContext = context.applicationContext
        val store = AppPreferencesStore(appContext)
        val initial = store.loadSession()
        if (!initial.isAuthenticated || initial.school == null) {
            return TokenAcquireResult.NotAuthenticated
        }

        val nowFast = clockMsProvider()
        val fastToken = initial.accessToken
        if (!forceRefresh && !fastToken.isNullOrBlank() && !isTokenExpiringSoon(initial, nowFast, client)) {
            return TokenAcquireResult.Valid(initial, fastToken)
        }

        return refreshMutex.withLock {
            val latest = store.loadSession()
            if (!latest.isAuthenticated || latest.school == null) {
                return@withLock TokenAcquireResult.NotAuthenticated
            }

            val nowLocked = clockMsProvider()
            val latestToken = latest.accessToken
            // Double-checked locking: another coroutine may have just refreshed cleanly while we waited.
            if (!forceRefresh && !latestToken.isNullOrBlank() && !isTokenExpiringSoon(latest, nowLocked, client)) {
                return@withLock TokenAcquireResult.Valid(latest, latestToken)
            }

            val currentRefreshToken = latest.refreshToken?.trim().orEmpty()
            if (currentRefreshToken.isBlank()) {
                return@withLock if (!latestToken.isNullOrBlank() && !forceRefresh) {
                    TokenAcquireResult.Valid(latest, latestToken)
                } else {
                    TokenAcquireResult.RequiresReauth("Geen geldig refresh token beschikbaar")
                }
            }

            val result = refreshExecutorOverride?.invoke(currentRefreshToken)
                ?: client.refreshAccessTokenDetailed(currentRefreshToken)

            when (result) {
                is TokenRefreshResult.Success -> {
                    val updatedSession = latest.copy(
                        accessToken = result.accessToken,
                        refreshToken = result.refreshToken.ifBlank { currentRefreshToken },
                        expiresAt = result.expiresAtMs
                    )
                    // Synchronous commit BEFORE releasing lock so all readers immediately see the new rotating refresh_token.
                    store.saveSessionSync(updatedSession)
                    onSessionUpdated?.invoke(updatedSession)
                    RawConsoleLogger.info(
                        "Auth",
                        "Magister sessie vernieuwd en synchroon opgeslagen (expiresAt=${result.expiresAtMs})"
                    )
                    TokenAcquireResult.Valid(updatedSession, result.accessToken)
                }

                is TokenRefreshResult.InvalidGrant -> {
                    RawConsoleLogger.error(
                        "Auth",
                        "Magister refresh geweigerd (invalid_grant): ${result.errorMessage}"
                    )
                    store.clearAuthCredentialsOnly()
                    val cleared = store.loadSession()
                    onSessionUpdated?.invoke(cleared)
                    TokenAcquireResult.RequiresReauth("Magister sessie verlopen. Log opnieuw in.")
                }

                is TokenRefreshResult.TransientError -> {
                    RawConsoleLogger.warn(
                        "Auth",
                        "Tijdelijke vernieuwingsfout (${result.code}), bestaand token blijft bewaard: ${result.errorMessage}"
                    )
                    TokenAcquireResult.TransientFailure(
                        message = result.errorMessage,
                        fallbackToken = latestToken
                    )
                }
            }
        }
    }
}
