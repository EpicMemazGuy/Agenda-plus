package com.example.services

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import com.example.R
import com.example.model.AuthSession

data class CloudSyncProfile(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val schoolName: String = "",
    val tenantId: String = "",
    val accessToken: String = "",
    val refreshToken: String = "",
    val updatedAt: Any? = null
)

class FirebaseSyncService(private val context: Context) {
    private val db: FirebaseFirestore by lazy {
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    private val auth = Firebase.auth

    val currentUser get() = auth.currentUser

    fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google for Cloud Sync.")
    }

    suspend fun syncSessionToCloud(session: AuthSession) {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        val profile = mapOf(
            "uid" to uid,
            "email" to (auth.currentUser?.email ?: ""),
            "displayName" to (auth.currentUser?.displayName ?: ""),
            "schoolName" to (session.school?.name ?: ""),
            "tenantId" to (session.school?.id ?: ""),
            "accessToken" to (session.accessToken ?: ""),
            "refreshToken" to (session.refreshToken ?: ""),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        userRef.set(profile, SetOptions.merge()).await()
    }

    suspend fun fetchCloudProfile(): CloudSyncProfile? {
        val uid = requireUserId()
        val doc = db.collection("users").document(uid).get().await()
        return doc.toObject(CloudSyncProfile::class.java)
    }

    suspend fun signInWithGoogle(
        activity: Activity,
        credentialManager: CredentialManager
    ): Result<Unit> {
        return try {
            val clientId = context.getString(R.string.default_web_client_id)
            val signInOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(serverClientId = clientId)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInOption)
                .build()

            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                auth.signInWithCredential(authCredential).await()
                Result.success(Unit)
            } else {
                Result.failure(Exception("Onverwacht type inloggegevens."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception("Google Inloggen geannuleerd."))
        } catch (e: Exception) {
            Log.e("FirebaseSyncService", "Google Sign-In failed", e)
            Result.failure(e)
        }
    }

    suspend fun signOut(credentialManager: CredentialManager) {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("FirebaseSyncService", "Failed to clear credential state: ${e.message}")
            } finally {
                auth.signOut()
            }
        }
    }
}
