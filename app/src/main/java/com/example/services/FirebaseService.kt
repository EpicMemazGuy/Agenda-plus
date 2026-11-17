package com.example.services

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.example.model.AgendaStyle
import com.example.model.AppThemeStyle
import com.example.model.AuthSession
import com.example.model.DarkModePreference
import com.example.model.GradeUnboxingMode
import com.example.model.SchoolProvider
import com.example.model.SchoolTenant
import com.example.model.cleanSchoolText
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String?
): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = Firebase.auth
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

class GoogleSignInCancelledException : Exception("Inloggen met Google geannuleerd.")

@Keep
@IgnoreExtraProperties
data class UserCloudSettings(
    val appThemeStyle: String = AppThemeStyle.STANDARD.name,
    val darkModePreference: String = DarkModePreference.SYSTEM.name,
    val agendaStyle: String = AgendaStyle.COMPACT.name,
    val uiScale: Double = 1.0,
    val eloUrl: String = "https://elo.example.nl",
    val savedBusLine: String = "",
    val uitvalNotificationsEnabled: Boolean = true,
    val gradeNotificationsEnabled: Boolean = true,
    val testNotificationsEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = true,
    val quietHoursStartMinutes: Int = 22 * 60,
    val quietHoursEndMinutes: Int = 7 * 60,
    val mutedSubjects: List<String> = emptyList(),
    val smartBusAlertsEnabled: Boolean = true,
    val busWalkMinutes: Int = 5,
    val busArrivalBufferMinutes: Int = 10,
    val gradeUnboxingEnabled: Boolean = true,
    val gradeUnboxingMode: String = GradeUnboxingMode.CASE_OPENING.name
) {
    fun toFirestoreMap(): Map<String, Any> {
        val normalizedTheme = enumValues<AppThemeStyle>()
            .firstOrNull { it.name == appThemeStyle }?.name ?: AppThemeStyle.STANDARD.name
        val normalizedDark = enumValues<DarkModePreference>()
            .firstOrNull { it.name == darkModePreference }?.name ?: DarkModePreference.SYSTEM.name
        val normalizedAgenda = enumValues<AgendaStyle>()
            .firstOrNull { it.name == agendaStyle }?.name ?: AgendaStyle.COMPACT.name
        val normalizedUnboxing = enumValues<GradeUnboxingMode>()
            .firstOrNull { it.name == gradeUnboxingMode }?.name ?: GradeUnboxingMode.CASE_OPENING.name

        return mapOf(
            "appThemeStyle" to normalizedTheme,
            "darkModePreference" to normalizedDark,
            "agendaStyle" to normalizedAgenda,
            "uiScale" to uiScale.coerceIn(0.85, 1.20),
            "eloUrl" to eloUrl.take(500),
            "savedBusLine" to savedBusLine.take(32),
            "uitvalNotificationsEnabled" to uitvalNotificationsEnabled,
            "gradeNotificationsEnabled" to gradeNotificationsEnabled,
            "testNotificationsEnabled" to testNotificationsEnabled,
            "quietHoursEnabled" to quietHoursEnabled,
            "quietHoursStartMinutes" to quietHoursStartMinutes.coerceIn(0, 1439),
            "quietHoursEndMinutes" to quietHoursEndMinutes.coerceIn(0, 1439),
            "mutedSubjects" to mutedSubjects.map { it.trim().take(100) }.filter { it.isNotEmpty() }.take(50),
            "smartBusAlertsEnabled" to smartBusAlertsEnabled,
            "busWalkMinutes" to busWalkMinutes.coerceIn(1, 30),
            "busArrivalBufferMinutes" to busArrivalBufferMinutes.coerceIn(2, 30),
            "gradeUnboxingEnabled" to gradeUnboxingEnabled,
            "gradeUnboxingMode" to normalizedUnboxing
        )
    }

    fun resolvedAppThemeStyle(): AppThemeStyle =
        enumValues<AppThemeStyle>().firstOrNull { it.name == appThemeStyle } ?: AppThemeStyle.STANDARD

    fun resolvedDarkModePreference(): DarkModePreference =
        enumValues<DarkModePreference>().firstOrNull { it.name == darkModePreference } ?: DarkModePreference.SYSTEM

    fun resolvedAgendaStyle(): AgendaStyle =
        enumValues<AgendaStyle>().firstOrNull { it.name == agendaStyle } ?: AgendaStyle.COMPACT

    fun resolvedGradeUnboxingMode(): GradeUnboxingMode =
        enumValues<GradeUnboxingMode>().firstOrNull { it.name == gradeUnboxingMode } ?: GradeUnboxingMode.CASE_OPENING

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>?): UserCloudSettings {
            if (map == null) return UserCloudSettings()
            val rawMuted = (map["mutedSubjects"] as? List<*>)
                ?.mapNotNull { (it as? String)?.trim()?.take(100) }
                ?.filter { it.isNotEmpty() }
                ?.take(50)
                ?: emptyList()
            return UserCloudSettings(
                appThemeStyle = (map["appThemeStyle"] as? String) ?: AppThemeStyle.STANDARD.name,
                darkModePreference = (map["darkModePreference"] as? String) ?: DarkModePreference.SYSTEM.name,
                agendaStyle = (map["agendaStyle"] as? String) ?: AgendaStyle.COMPACT.name,
                uiScale = ((map["uiScale"] as? Number)?.toDouble() ?: 1.0).coerceIn(0.85, 1.20),
                eloUrl = ((map["eloUrl"] as? String) ?: "https://elo.example.nl").take(500),
                savedBusLine = ((map["savedBusLine"] as? String) ?: "").take(32),
                uitvalNotificationsEnabled = (map["uitvalNotificationsEnabled"] as? Boolean) ?: true,
                gradeNotificationsEnabled = (map["gradeNotificationsEnabled"] as? Boolean) ?: true,
                testNotificationsEnabled = (map["testNotificationsEnabled"] as? Boolean) ?: true,
                quietHoursEnabled = (map["quietHoursEnabled"] as? Boolean) ?: true,
                quietHoursStartMinutes = ((map["quietHoursStartMinutes"] as? Number)?.toInt() ?: (22 * 60)).coerceIn(0, 1439),
                quietHoursEndMinutes = ((map["quietHoursEndMinutes"] as? Number)?.toInt() ?: (7 * 60)).coerceIn(0, 1439),
                mutedSubjects = rawMuted,
                smartBusAlertsEnabled = (map["smartBusAlertsEnabled"] as? Boolean) ?: true,
                busWalkMinutes = ((map["busWalkMinutes"] as? Number)?.toInt() ?: 5).coerceIn(1, 30),
                busArrivalBufferMinutes = ((map["busArrivalBufferMinutes"] as? Number)?.toInt() ?: 10).coerceIn(2, 30),
                gradeUnboxingEnabled = (map["gradeUnboxingEnabled"] as? Boolean) ?: true,
                gradeUnboxingMode = (map["gradeUnboxingMode"] as? String) ?: GradeUnboxingMode.CASE_OPENING.name
            )
        }
    }
}

@Keep
@IgnoreExtraProperties
data class UserCloudProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val studentName: String = "",
    val studentId: String = "",
    val studentClass: String = "",
    val personId: Long = 0L,
    val schoolId: String = "",
    val schoolName: String = "",
    val schoolCity: String = "",
    val schoolSubdomain: String = "",
    val schoolProvider: String = SchoolProvider.MAGISTER.name,
    val magisterAccessToken: String = "",
    val magisterRefreshToken: String = "",
    val magisterAuthenticated: Boolean = false,
    val settings: UserCloudSettings = UserCloudSettings(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "uid" to uid,
            "email" to email.take(254),
            "displayName" to displayName.take(120),
            "studentName" to studentName.take(120),
            "studentId" to studentId.take(64),
            "studentClass" to studentClass.take(64),
            "personId" to personId.coerceAtLeast(0L),
            "schoolId" to schoolId.take(64),
            "schoolName" to schoolName.take(150),
            "schoolCity" to schoolCity.take(100),
            "schoolSubdomain" to schoolSubdomain.take(100),
            "schoolProvider" to SchoolProvider.MAGISTER.name,
            "magisterAccessToken" to magisterAccessToken.take(8192),
            "magisterRefreshToken" to magisterRefreshToken.take(8192),
            "magisterAuthenticated" to magisterAuthenticated,
            "settings" to settings.toFirestoreMap(),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toUpdateMap(): Map<String, Any> {
        val raw = mutableMapOf<String, Any?>(
            "email" to email.take(254),
            "displayName" to displayName.take(120),
            "studentName" to studentName.take(120),
            "studentId" to studentId.take(64),
            "studentClass" to studentClass.take(64),
            "personId" to personId.coerceAtLeast(0L),
            "schoolId" to schoolId.take(64),
            "schoolName" to schoolName.take(150),
            "schoolCity" to schoolCity.take(100),
            "schoolSubdomain" to schoolSubdomain.take(100),
            "schoolProvider" to SchoolProvider.MAGISTER.name,
            "magisterAccessToken" to magisterAccessToken.take(8192),
            "magisterRefreshToken" to magisterRefreshToken.take(8192),
            "magisterAuthenticated" to magisterAuthenticated,
            "settings" to settings.toFirestoreMap(),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        @Suppress("UNCHECKED_CAST")
        return raw.filterValues { it != null } as Map<String, Any>
    }

    fun toAuthSessionOrNull(): AuthSession? {
        if (!magisterAuthenticated || magisterAccessToken.isBlank()) return null
        val tenant = if (schoolSubdomain.isNotBlank() || schoolName.isNotBlank()) {
            SchoolTenant(
                id = schoolId.ifBlank { "cloud" },
                name = schoolName.ifBlank { "Magister" },
                city = schoolCity.ifBlank { "Nederland" },
                subdomain = schoolSubdomain.ifBlank { "accounts" },
                provider = SchoolProvider.MAGISTER
            )
        } else null
        return AuthSession(
            isAuthenticated = true,
            studentName = studentName.cleanSchoolText() ?: displayName.cleanSchoolText() ?: "Leerling",
            studentId = studentId.cleanSchoolText() ?: "",
            studentClass = studentClass.cleanSchoolText() ?: "",
            school = tenant,
            accessToken = magisterAccessToken,
            refreshToken = magisterRefreshToken.takeIf { it.isNotBlank() },
            personId = personId,
            provider = SchoolProvider.MAGISTER
        )
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromSnapshot(snapshot: DocumentSnapshot): UserCloudProfile? {
            if (!snapshot.exists()) return null
            val settingsMap = snapshot.get("settings") as? Map<String, Any?>
            return UserCloudProfile(
                uid = snapshot.getString("uid") ?: snapshot.id,
                email = snapshot.getString("email") ?: "",
                displayName = snapshot.getString("displayName") ?: "",
                studentName = snapshot.getString("studentName") ?: "",
                studentId = snapshot.getString("studentId") ?: "",
                studentClass = snapshot.getString("studentClass") ?: "",
                personId = snapshot.getLong("personId") ?: 0L,
                schoolId = snapshot.getString("schoolId") ?: "",
                schoolName = snapshot.getString("schoolName") ?: "",
                schoolCity = snapshot.getString("schoolCity") ?: "",
                schoolSubdomain = snapshot.getString("schoolSubdomain") ?: "",
                schoolProvider = snapshot.getString("schoolProvider") ?: SchoolProvider.MAGISTER.name,
                magisterAccessToken = snapshot.getString("magisterAccessToken") ?: "",
                magisterRefreshToken = snapshot.getString("magisterRefreshToken") ?: "",
                magisterAuthenticated = snapshot.getBoolean("magisterAuthenticated") ?: false,
                settings = UserCloudSettings.fromMap(settingsMap),
                createdAt = snapshot.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
                updatedAt = snapshot.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
        }
    }
}

class FirebaseService(
    private val db: FirebaseFirestore,
    private val appContext: Context,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        appContext = context.applicationContext
    )

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Gebruiker moet ingelogd zijn met Google voordat Firestore gebruikt kan worden.")
    }

    suspend fun attemptAutoSignIn(credentialManager: CredentialManager): FirebaseUser? {
        auth.currentUser?.let { return it }
        val clientId = runCatching {
            com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID
        }.getOrNull()?.takeIf { it.isNotBlank() && !it.contains("REPLACE") } ?: return null
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        return try {
            val cred = credentialManager.getCredential(appContext, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
                result.user
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun signInWithGoogle(
        activity: Activity,
        credentialManager: CredentialManager
    ): Result<FirebaseUser> {
        val clientId = try {
            val buildConfigId = com.example.BuildConfig.GOOGLE_WEB_CLIENT_ID
            buildConfigId.takeIf { it.isNotBlank() && !it.contains("REPLACE") }
                ?: return Result.failure(IllegalStateException("Google Sign-In configuratie ontbreekt (GOOGLE_WEB_CLIENT_ID)."))
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(signInOption)
            .build()

        return try {
            val response = credentialManager.getCredential(activity, request)
            val cred = response.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val idToken = GoogleIdTokenCredential.createFrom(cred.data).idToken
                val authResult = auth.signInWithCredential(
                    GoogleAuthProvider.getCredential(idToken, null)
                ).await()
                val user = authResult.user
                    ?: return Result.failure(IllegalStateException("Geen Firebase-gebruiker ontvangen."))
                Result.success(user)
            } else {
                Result.failure(IllegalStateException("Onverwacht Google-inlogtype ontvangen."))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            Log.w("Auth", "Google Sign-In cancelled or dismissed: ${e.message}", e)
            Result.failure(GoogleSignInCancelledException())
        } catch (e: Exception) {
            Log.e("Auth", "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(credentialManager: CredentialManager) {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("Auth", "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }

    fun observeUserCloudProfile(uid: String): Flow<UserCloudProfile?> {
        if (uid.isBlank()) return kotlinx.coroutines.flow.flowOf(null)
        val docRef = db.collection("users").document(uid)
        val path = docRef.path
        return flow {
            emitAll(
                docRef.snapshots()
                    .map { snapshot -> UserCloudProfile.fromSnapshot(snapshot) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val exceptionToLog = firestoreError ?: (error as? Exception)
                        if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                            handleFirestoreError(exceptionToLog, OperationType.GET, path)
                        }
                        throw error
                    }
            )
        }
    }

    suspend fun fetchUserCloudProfile(): Result<UserCloudProfile?> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val snapshot = docRef.get().await()
            Result.success(UserCloudProfile.fromSnapshot(snapshot))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveUserCloudProfile(
        session: AuthSession,
        settings: UserCloudSettings
    ): Result<UserCloudProfile> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val user = auth.currentUser
                ?: throw IllegalStateException("Gebruiker moet ingelogd zijn met Google voordat Firestore gebruikt kan worden.")
            val uid = user.uid
            require(uid.isNotBlank()) { "Ongeldig gebruikers-ID." }
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path

            val cleanSchool = session.school
            val profile = UserCloudProfile(
                uid = uid,
                email = (user.email ?: "").take(254),
                displayName = (user.displayName?.takeIf { it.isNotBlank() }
                    ?: session.studentName.cleanSchoolText()
                    ?: user.email
                    ?: "Agenda+ Gebruiker").take(120),
                studentName = (session.studentName.cleanSchoolText() ?: "").take(120),
                studentId = (session.studentId.cleanSchoolText() ?: "").take(64),
                studentClass = (session.studentClass.cleanSchoolText() ?: "").take(64),
                personId = session.personId.coerceAtLeast(0L),
                schoolId = (cleanSchool?.id ?: "").take(64),
                schoolName = (cleanSchool?.name?.cleanSchoolText() ?: "").take(150),
                schoolCity = (cleanSchool?.city?.cleanSchoolText() ?: "").take(100),
                schoolSubdomain = (cleanSchool?.subdomain ?: "").take(100),
                schoolProvider = SchoolProvider.MAGISTER.name,
                magisterAccessToken = (session.accessToken ?: "").take(8192),
                magisterRefreshToken = (session.refreshToken ?: "").take(8192),
                magisterAuthenticated = session.isAuthenticated && !session.accessToken.isNullOrBlank(),
                settings = settings
            )

            val existingSnapshot = docRef.get().await()
            if (existingSnapshot.exists()) {
                docRef.update(profile.toUpdateMap()).await()
            } else {
                docRef.set(profile.toCreateMap()).await()
            }
            Result.success(profile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }
}
