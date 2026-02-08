package com.example.morsecoach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SocialViewModel : ViewModel() {

    private val repo = SocialRepository()

    // ── Tab state ───────────────────────────────────────────────────────
    enum class SocialTab { CHATS, FRIENDS }

    private val _selectedTab = MutableStateFlow(SocialTab.CHATS)
    val selectedTab: StateFlow<SocialTab> = _selectedTab.asStateFlow()

    fun selectTab(tab: SocialTab) { _selectedTab.value = tab }

    // ── Friends ─────────────────────────────────────────────────────────
    private val _friends = MutableStateFlow<List<FriendUser>>(emptyList())
    val friends: StateFlow<List<FriendUser>> = _friends.asStateFlow()

    // ── Incoming requests ───────────────────────────────────────────────
    private val _incomingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val incomingRequests: StateFlow<List<FriendRequest>> = _incomingRequests.asStateFlow()

    // ── Outgoing requests ───────────────────────────────────────────────
    private val _outgoingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val outgoingRequests: StateFlow<List<FriendRequest>> = _outgoingRequests.asStateFlow()

    // ── User search ─────────────────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<UserSearchResult>>(emptyList())
    val searchResults: StateFlow<List<UserSearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // ── Chat summaries ──────────────────────────────────────────────────
    private val _chatSummaries = MutableStateFlow<List<ChatSummary>>(emptyList())
    val chatSummaries: StateFlow<List<ChatSummary>> = _chatSummaries.asStateFlow()

    // ── Username cache for chat summaries ────────────────────────────────
    private val _usernameCache = MutableStateFlow<Map<String, String>>(emptyMap())
    val usernameCache: StateFlow<Map<String, String>> = _usernameCache.asStateFlow()

    // ── Snackbar / toast messages ───────────────────────────────────────
    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()
    fun clearMessage() { _uiMessage.value = null }

    // ── Active chat messages ────────────────────────────────────────────
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // ── Chat input buffer ───────────────────────────────────────────────
    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    fun updateChatInput(value: String) { _chatInput.value = value }

    // ── Listener jobs (cancelled and restarted on refresh) ──────────────
    private var friendsJob: Job? = null
    private var incomingJob: Job? = null
    private var outgoingJob: Job? = null
    private var chatSummariesJob: Job? = null
    private var lastUid: String? = null

    // ── Init ────────────────────────────────────────────────────────────

    init {
        startListeners()
    }

    /**
     * Call this when entering the social screen to pick up auth changes.
     * If the uid changed (account switch), all listeners restart.
     */
    fun refresh() {
        val uid = repo.currentUid
        if (uid != lastUid) {
            // Auth changed – clear stale state and restart
            _friends.value = emptyList()
            _incomingRequests.value = emptyList()
            _outgoingRequests.value = emptyList()
            _chatSummaries.value = emptyList()
            _usernameCache.value = emptyMap()
            _searchQuery.value = ""
            _searchResults.value = emptyList()
            _chatMessages.value = emptyList()
            _chatInput.value = ""
            startListeners()
        }
    }

    private fun startListeners() {
        // Cancel any previous listener jobs
        friendsJob?.cancel()
        incomingJob?.cancel()
        outgoingJob?.cancel()
        chatSummariesJob?.cancel()

        lastUid = repo.currentUid

        friendsJob = viewModelScope.launch {
            repo.observeFriends().collect { list ->
                _friends.value = list
            }
        }
        incomingJob = viewModelScope.launch {
            repo.observeIncomingRequests().collect { list ->
                _incomingRequests.value = list
            }
        }
        outgoingJob = viewModelScope.launch {
            repo.observeOutgoingRequests().collect { list ->
                _outgoingRequests.value = list
            }
        }
        chatSummariesJob = viewModelScope.launch {
            repo.observeChatSummaries().collect { list ->
                _chatSummaries.value = list
                // Resolve missing usernames
                list.forEach { summary ->
                    if (summary.otherUsername.isBlank() &&
                        _usernameCache.value[summary.otherUserId] == null
                    ) {
                        launch {
                            val name = repo.fetchUsername(summary.otherUserId)
                            _usernameCache.value = _usernameCache.value + (summary.otherUserId to name)
                        }
                    }
                }
            }
        }
    }

    // ── Search users ────────────────────────────────────────────────────

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.length >= 2) {
            performSearch(query)
        } else {
            _searchResults.value = emptyList()
        }
    }

    private fun performSearch(prefix: String) {
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = repo.searchUsers(prefix)
            _isSearching.value = false
        }
    }

    // ── Send friend request ─────────────────────────────────────────────

    fun sendFriendRequest(targetUid: String, targetUsername: String) {
        viewModelScope.launch {
            val result = repo.sendFriendRequest(targetUid, targetUsername)
            result.fold(
                onSuccess = { _uiMessage.value = "Friend request sent!" },
                onFailure = { _uiMessage.value = it.message ?: "Failed to send request" }
            )
        }
    }

    // ── Accept friend request ───────────────────────────────────────────

    fun acceptRequest(fromUid: String, fromUsername: String) {
        viewModelScope.launch {
            val result = repo.acceptFriendRequest(fromUid, fromUsername)
            result.fold(
                onSuccess = { _uiMessage.value = "Friend added!" },
                onFailure = { _uiMessage.value = it.message ?: "Failed to accept" }
            )
        }
    }

    // ── Decline friend request ──────────────────────────────────────────

    fun declineRequest(fromUid: String) {
        viewModelScope.launch {
            val result = repo.declineFriendRequest(fromUid)
            result.fold(
                onSuccess = { _uiMessage.value = "Request declined" },
                onFailure = { _uiMessage.value = it.message ?: "Failed to decline" }
            )
        }
    }

    // ── Cancel friend request ───────────────────────────────────────────

    fun cancelRequest(targetUid: String) {
        viewModelScope.launch {
            val result = repo.cancelFriendRequest(targetUid)
            result.fold(
                onSuccess = { _uiMessage.value = "Request cancelled" },
                onFailure = { _uiMessage.value = it.message ?: "Failed to cancel" }
            )
        }
    }

    // ── Remove friend ───────────────────────────────────────────────────

    fun removeFriend(friendUid: String) {
        viewModelScope.launch {
            val result = repo.removeFriend(friendUid)
            result.fold(
                onSuccess = { _uiMessage.value = "Friend removed" },
                onFailure = { _uiMessage.value = it.message ?: "Failed to remove friend" }
            )
        }
    }

    // ── Start observing messages for a chat ─────────────────────────────

    fun openChat(chatId: String) {
        _chatMessages.value = emptyList()
        _chatInput.value = ""
        viewModelScope.launch {
            repo.observeMessages(chatId).collect { msgs ->
                _chatMessages.value = msgs
            }
        }
    }

    // ── Send message ────────────────────────────────────────────────────

    fun sendMessage(otherUid: String) {
        val text = _chatInput.value
        if (text.isBlank()) return
        if (text.length > 200) {
            _uiMessage.value = "Message too long (max 200 characters)"
            return
        }
        viewModelScope.launch {
            val result = repo.sendMessage(otherUid, text)
            result.fold(
                onSuccess = { _chatInput.value = "" },
                onFailure = { _uiMessage.value = it.message ?: "Failed to send" }
            )
        }
    }

    // ── Open chat with a friend ─────────────────────────────────────────

    /**
     * Returns chatId that can be used for navigation.
     */
    suspend fun getOrCreateChat(friendUid: String): String {
        return repo.getOrCreateChat(friendUid)
    }

    // ── Delete chat ─────────────────────────────────────────────────────

    fun deleteChat(chatId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = repo.deleteChat(chatId)
            result.fold(
                onSuccess = {
                    _uiMessage.value = "Chat deleted"
                    onDeleted()
                },
                onFailure = { _uiMessage.value = it.message ?: "Failed to delete chat" }
            )
        }
    }

    val currentUid: String?
        get() = repo.currentUid

    // ── Check user relationship status ─────────────────────────────────

    fun getUserStatus(uid: String): UserStatus {
        return when {
            _friends.value.any { it.uid == uid } -> UserStatus.FRIEND
            _outgoingRequests.value.any { it.fromUserId == uid } -> UserStatus.REQUEST_SENT
            _incomingRequests.value.any { it.fromUserId == uid } -> UserStatus.REQUEST_RECEIVED
            else -> UserStatus.NONE
        }
    }

    enum class UserStatus {
        NONE, REQUEST_SENT, REQUEST_RECEIVED, FRIEND
    }
}
