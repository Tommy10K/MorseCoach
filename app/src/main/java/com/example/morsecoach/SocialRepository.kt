package com.example.morsecoach

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// ── Data classes ────────────────────────────────────────────────────────────

data class FriendUser(
    val uid: String = "",
    val username: String = "",
    val addedAt: Timestamp? = null
)

data class FriendRequest(
    val fromUserId: String = "",
    val fromUsername: String = "",
    val createdAt: Timestamp? = null
)

data class ChatSummary(
    val chatId: String = "",
    val otherUserId: String = "",
    val otherUsername: String = "",
    val lastMessage: String = "",
    val lastMessageTimestamp: Timestamp? = null
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val timestamp: Timestamp? = null
)

data class UserSearchResult(
    val uid: String = "",
    val username: String = "",
    val email: String = ""
)

// ── Repository  ─────────────────────────────────────────────────────────────

class SocialRepository {

    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUid: String?
        get() = auth.currentUser?.uid

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Deterministic chat id so both participants resolve the same document. */
    fun getChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    // ── User search ─────────────────────────────────────────────────────

    /**
     * Prefix search on the `username` field.
     * Fetches up to 20 candidates and filters client-side for case-insensitive match.
     * Returns up to 10 results excluding the current user.
     */
    suspend fun searchUsers(prefix: String): List<UserSearchResult> {
        if (prefix.isBlank()) return emptyList()
        val lower = prefix.lowercase()

        return try {
            // Try usernameLower first (new accounts have it)
            val snap = db.collection("users")
                .orderBy("username")
                .startAt(prefix)
                .endAt(prefix + "\uf8ff")
                .limit(20)
                .get()
                .await()

            snap.documents.mapNotNull { doc ->
                if (doc.id == currentUid) return@mapNotNull null
                val username = doc.getString("username") ?: return@mapNotNull null
                // Case-insensitive client-side filter
                if (!username.lowercase().startsWith(lower)) return@mapNotNull null
                UserSearchResult(
                    uid = doc.id,
                    username = username,
                    email = doc.getString("email") ?: ""
                )
            }.take(10)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Friend requests ─────────────────────────────────────────────────

    /**
     * Send a friend request. Creates an incoming doc on the target and
     * an outgoing marker on the sender to prevent duplicates.
     */
    suspend fun sendFriendRequest(targetUid: String, targetUsername: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        if (uid == targetUid) return Result.failure(Exception("Cannot add yourself"))

        return try {
            // Check if already friends
            val friendDoc = db.collection("users").document(uid)
                .collection("friends").document(targetUid).get().await()
            if (friendDoc.exists()) return Result.failure(Exception("Already friends"))

            // Check if outgoing request already exists
            val outDoc = db.collection("users").document(uid)
                .collection("friend_requests_out").document(targetUid).get().await()
            if (outDoc.exists()) return Result.failure(Exception("Request already sent"))

            // Check if there's an incoming request from that user (auto-accept)
            val inDoc = db.collection("users").document(uid)
                .collection("friend_requests").document(targetUid).get().await()
            if (inDoc.exists()) {
                // They already sent us a request – just accept it
                return acceptFriendRequest(targetUid,
                    inDoc.getString("fromUsername") ?: targetUsername)
            }

            // Get current user's username
            val myDoc = db.collection("users").document(uid).get().await()
            val myUsername = myDoc.getString("username") ?: "Unknown"

            val batch = db.batch()

            // Incoming request on target
            val inRef = db.collection("users").document(targetUid)
                .collection("friend_requests").document(uid)
            batch.set(inRef, hashMapOf(
                "fromUserId" to uid,
                "fromUsername" to myUsername,
                "createdAt" to FieldValue.serverTimestamp()
            ))

            // Outgoing marker on sender
            val outRef = db.collection("users").document(uid)
                .collection("friend_requests_out").document(targetUid)
            batch.set(outRef, hashMapOf(
                "toUserId" to targetUid,
                "toUsername" to targetUsername,
                "createdAt" to FieldValue.serverTimestamp()
            ))

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Accept an incoming friend request.
     * Writes both friend docs and removes request docs atomically.
     */
    suspend fun acceptFriendRequest(fromUid: String, fromUsername: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))

        return try {
            val myDoc = db.collection("users").document(uid).get().await()
            val myUsername = myDoc.getString("username") ?: "Unknown"

            val batch = db.batch()

            // Add friend to both users
            val myFriendRef = db.collection("users").document(uid)
                .collection("friends").document(fromUid)
            batch.set(myFriendRef, hashMapOf(
                "username" to fromUsername,
                "addedAt" to FieldValue.serverTimestamp()
            ))

            val theirFriendRef = db.collection("users").document(fromUid)
                .collection("friends").document(uid)
            batch.set(theirFriendRef, hashMapOf(
                "username" to myUsername,
                "addedAt" to FieldValue.serverTimestamp()
            ))

            // Remove incoming request
            val inRef = db.collection("users").document(uid)
                .collection("friend_requests").document(fromUid)
            batch.delete(inRef)

            // Remove outgoing marker on the other user
            val outRef = db.collection("users").document(fromUid)
                .collection("friend_requests_out").document(uid)
            batch.delete(outRef)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Decline / remove an incoming friend request. */
    suspend fun declineFriendRequest(fromUid: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))

        return try {
            val batch = db.batch()

            val inRef = db.collection("users").document(uid)
                .collection("friend_requests").document(fromUid)
            batch.delete(inRef)

            val outRef = db.collection("users").document(fromUid)
                .collection("friend_requests_out").document(uid)
            batch.delete(outRef)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Cancel an outgoing friend request. */
    suspend fun cancelFriendRequest(targetUid: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))

        return try {
            val batch = db.batch()

            // Remove outgoing marker
            val outRef = db.collection("users").document(uid)
                .collection("friend_requests_out").document(targetUid)
            batch.delete(outRef)

            // Remove incoming request on target
            val inRef = db.collection("users").document(targetUid)
                .collection("friend_requests").document(uid)
            batch.delete(inRef)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Remove a friend (mutual deletion). */
    suspend fun removeFriend(friendUid: String): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))

        return try {
            val batch = db.batch()

            // Remove from my friends list
            val myFriendRef = db.collection("users").document(uid)
                .collection("friends").document(friendUid)
            batch.delete(myFriendRef)

            // Remove from their friends list
            val theirFriendRef = db.collection("users").document(friendUid)
                .collection("friends").document(uid)
            batch.delete(theirFriendRef)

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Friends list (real-time) ────────────────────────────────────────

    fun observeFriends(): Flow<List<FriendUser>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val reg: ListenerRegistration = db.collection("users").document(uid)
            .collection("friends")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { doc ->
                    FriendUser(
                        uid = doc.id,
                        username = doc.getString("username") ?: "",
                        addedAt = doc.getTimestamp("addedAt")
                    )
                }
                trySend(list)
            }

        awaitClose { reg.remove() }
    }

    // ── Incoming friend requests (real-time) ────────────────────────────

    fun observeIncomingRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val reg = db.collection("users").document(uid)
            .collection("friend_requests")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { doc ->
                    FriendRequest(
                        fromUserId = doc.getString("fromUserId") ?: doc.id,
                        fromUsername = doc.getString("fromUsername") ?: "",
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }
                trySend(list)
            }

        awaitClose { reg.remove() }
    }

    // ── Outgoing friend requests (real-time) ────────────────────────────

    fun observeOutgoingRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val reg = db.collection("users").document(uid)
            .collection("friend_requests_out")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { doc ->
                    FriendRequest(
                        fromUserId = doc.getString("toUserId") ?: doc.id,
                        fromUsername = doc.getString("toUsername") ?: "",
                        createdAt = doc.getTimestamp("createdAt")
                    )
                }
                trySend(list)
            }

        awaitClose { reg.remove() }
    }

    // ── Chat summaries (real-time) ──────────────────────────────────────

    fun observeChatSummaries(): Flow<List<ChatSummary>> = callbackFlow {
        val uid = currentUid ?: run { trySend(emptyList()); close(); return@callbackFlow }

        val reg = db.collection("chats")
            .whereArrayContains("participants", uid)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { doc ->
                    val participants = doc.get("participants") as? List<*> ?: return@mapNotNull null
                    val otherId = participants.firstOrNull { it != uid } as? String ?: return@mapNotNull null
                    ChatSummary(
                        chatId = doc.id,
                        otherUserId = otherId,
                        otherUsername = doc.getString("otherUsername_$otherId")
                            ?: doc.getString("participantNames.$otherId") ?: "",
                        lastMessage = doc.getString("lastMessage") ?: "",
                        lastMessageTimestamp = doc.getTimestamp("lastMessageTimestamp")
                    )
                }.sortedByDescending { it.lastMessageTimestamp }
                trySend(list)
            }

        awaitClose { reg.remove() }
    }

    /**
     * Fetch the username for a uid so we can display it in chat summaries.
     */
    suspend fun fetchUsername(uid: String): String {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            doc.getString("username") ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    // ── Messages (real-time, last 100) ──────────────────────────────────

    fun observeMessages(chatId: String): Flow<List<ChatMessage>> = callbackFlow {
        val reg = db.collection("chats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap.documents.mapNotNull { doc ->
                    ChatMessage(
                        id = doc.id,
                        senderId = doc.getString("senderId") ?: "",
                        text = doc.getString("text") ?: "",
                        timestamp = doc.getTimestamp("timestamp")
                    )
                }.reversed() // chronological order for UI
                trySend(list)
            }

        awaitClose { reg.remove() }
    }

    // ── Send message ────────────────────────────────────────────────────

    suspend fun sendMessage(
        otherUid: String,
        text: String
    ): Result<Unit> {
        val uid = currentUid ?: return Result.failure(Exception("Not logged in"))
        if (text.isBlank()) return Result.failure(Exception("Empty message"))
        if (text.length > 200) return Result.failure(Exception("Message too long (max 200)"))

        // Validate Morse-only characters
        val allowed = setOf('.', '-', ' ', '/')
        if (text.any { it !in allowed }) {
            return Result.failure(Exception("Only Morse characters allowed"))
        }

        val chatId = getChatId(uid, otherUid)

        return try {
            // Ensure chat doc exists with participants and username mapping
            val chatRef = db.collection("chats").document(chatId)
            val chatSnap = chatRef.get().await()
            if (!chatSnap.exists()) {
                // Fetch both usernames
                val myUsername = fetchUsername(uid)
                val otherUsername = fetchUsername(otherUid)
                chatRef.set(hashMapOf(
                    "participants" to listOf(uid, otherUid),
                    "lastMessage" to text,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "otherUsername_$uid" to myUsername,
                    "otherUsername_$otherUid" to otherUsername
                )).await()
            } else {
                chatRef.update(
                    "lastMessage", text,
                    "lastMessageTimestamp", FieldValue.serverTimestamp()
                ).await()
            }

            // Add message
            chatRef.collection("messages").add(hashMapOf(
                "senderId" to uid,
                "text" to text,
                "timestamp" to FieldValue.serverTimestamp()
            )).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Start a chat with a friend (navigate to chat) ───────────────────

    /**
     * Ensure a chat document exists between currentUser and friendUid,
     * then return the chatId for navigation.
     */
    suspend fun getOrCreateChat(friendUid: String): String {
        val uid = currentUid ?: throw Exception("Not logged in")
        val chatId = getChatId(uid, friendUid)

        val chatRef = db.collection("chats").document(chatId)
        val snap = chatRef.get().await()
        if (!snap.exists()) {
            val myUsername = fetchUsername(uid)
            val otherUsername = fetchUsername(friendUid)
            chatRef.set(hashMapOf(
                "participants" to listOf(uid, friendUid),
                "lastMessage" to "",
                "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                "otherUsername_$uid" to myUsername,
                "otherUsername_$friendUid" to otherUsername
            )).await()
        }
        return chatId
    }

    // ── Delete a chat ───────────────────────────────────────────────────

    /**
     * Delete chat document and all its messages.
     * Firestore doesn't support recursive deletes client-side efficiently,
     * so we delete messages in batches then the chat doc.
     */
    suspend fun deleteChat(chatId: String): Result<Unit> {
        return try {
            val messagesRef = db.collection("chats").document(chatId).collection("messages")
            // Delete messages in batches of 50
            var snapshot = messagesRef.limit(50).get().await()
            while (snapshot.documents.isNotEmpty()) {
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
                snapshot = messagesRef.limit(50).get().await()
            }
            // Delete the chat document itself
            db.collection("chats").document(chatId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
