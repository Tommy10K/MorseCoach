package com.example.morsecoach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.morsecoach.ui.theme.MilitaryGreen
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialScreen(
    viewModel: SocialViewModel,
    onBackClick: () -> Unit,
    onOpenChat: (chatId: String, otherUid: String, otherUsername: String) -> Unit
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val uiMessage by viewModel.uiMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Refresh listeners when entering social screen (picks up auth changes)
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Show snackbar messages
    LaunchedEffect(uiMessage) {
        uiMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Social") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Tabs ────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = if (selectedTab == SocialViewModel.SocialTab.CHATS) 0 else 1,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                Tab(
                    selected = selectedTab == SocialViewModel.SocialTab.CHATS,
                    onClick = { viewModel.selectTab(SocialViewModel.SocialTab.CHATS) },
                    text = { Text("Chats", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == SocialViewModel.SocialTab.FRIENDS,
                    onClick = { viewModel.selectTab(SocialViewModel.SocialTab.FRIENDS) },
                    text = { Text("Friends", fontWeight = FontWeight.Bold) },
                    icon = { Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            // ── Tab content ─────────────────────────────────────────────
            when (selectedTab) {
                SocialViewModel.SocialTab.CHATS -> ChatsTab(
                    viewModel = viewModel,
                    onOpenChat = onOpenChat
                )
                SocialViewModel.SocialTab.FRIENDS -> FriendsTab(
                    viewModel = viewModel,
                    onOpenChat = onOpenChat
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// CHATS TAB
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun ChatsTab(
    viewModel: SocialViewModel,
    onOpenChat: (chatId: String, otherUid: String, otherUsername: String) -> Unit
) {
    val chatSummaries by viewModel.chatSummaries.collectAsState()
    val usernameCache by viewModel.usernameCache.collectAsState()
    val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

    if (chatSummaries.isEmpty()) {
        // Empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No chats yet",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                )
                Text(
                    "Add friends and start chatting in Morse!",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp)
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(chatSummaries, key = { it.chatId }) { chat ->
                val displayName = chat.otherUsername.ifBlank {
                    usernameCache[chat.otherUserId] ?: "..."
                }
                ChatSummaryItem(
                    username = displayName,
                    lastMessage = chat.lastMessage,
                    timestamp = chat.lastMessageTimestamp?.toDate()?.let { dateFormat.format(it) } ?: "",
                    onClick = { onOpenChat(chat.chatId, chat.otherUserId, displayName) }
                )
            }
        }
    }
}

@Composable
private fun ChatSummaryItem(
    username: String,
    lastMessage: String,
    timestamp: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar placeholder
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MilitaryGreen.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = username.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = username,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lastMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = lastMessage,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            if (timestamp.isNotBlank()) {
                Text(
                    text = timestamp,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// FRIENDS TAB
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun FriendsTab(
    viewModel: SocialViewModel,
    onOpenChat: (chatId: String, otherUid: String, otherUsername: String) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val incomingRequests by viewModel.incomingRequests.collectAsState()
    val outgoingRequests by viewModel.outgoingRequests.collectAsState()
    val friends by viewModel.friends.collectAsState()
    val scope = rememberCoroutineScope()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // ── Search bar ──────────────────────────────────────────────────
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                placeholder = { Text("Search users by username...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MilitaryGreen,
                    cursorColor = MilitaryGreen
                )
            )
        }

        // ── Search results ──────────────────────────────────────────────
        if (searchQuery.length >= 2) {
            if (isSearching) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MilitaryGreen,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            } else if (searchResults.isEmpty()) {
                item {
                    Text(
                        "No users found",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }
            } else {
                item {
                    SectionHeader("Search Results")
                }
                items(searchResults, key = { "search_${it.uid}" }) { user ->
                    val status = remember(friends, outgoingRequests, incomingRequests, user.uid) {
                        when {
                            friends.any { it.uid == user.uid } -> SocialViewModel.UserStatus.FRIEND
                            outgoingRequests.any { it.fromUserId == user.uid } -> SocialViewModel.UserStatus.REQUEST_SENT
                            incomingRequests.any { it.fromUserId == user.uid } -> SocialViewModel.UserStatus.REQUEST_RECEIVED
                            else -> SocialViewModel.UserStatus.NONE
                        }
                    }
                    SearchResultItem(
                        username = user.username,
                        status = status,
                        onAddClick = {
                            viewModel.sendFriendRequest(user.uid, user.username)
                        },
                        onAcceptClick = {
                            viewModel.acceptRequest(user.uid, user.username)
                        },
                        onCancelClick = {
                            viewModel.cancelRequest(user.uid)
                        },
                        onRemoveClick = {
                            viewModel.removeFriend(user.uid)
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }

        // ── Incoming requests ───────────────────────────────────────────
        if (incomingRequests.isNotEmpty()) {
            item {
                SectionHeader("Friend Requests (${incomingRequests.size})")
            }
            items(incomingRequests, key = { "req_${it.fromUserId}" }) { req ->
                FriendRequestItem(
                    username = req.fromUsername,
                    onAccept = { viewModel.acceptRequest(req.fromUserId, req.fromUsername) },
                    onDecline = { viewModel.declineRequest(req.fromUserId) }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }

        // ── Friends list ────────────────────────────────────────────────
        item {
            SectionHeader("Friends (${friends.size})")
        }

        if (friends.isEmpty()) {
            item {
                Text(
                    "No friends yet. Search for users above!",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        } else {
            items(friends, key = { "friend_${it.uid}" }) { friend ->
                FriendItem(
                    username = friend.username,
                    onChatClick = {
                        scope.launch {
                            val chatId = viewModel.getOrCreateChat(friend.uid)
                            onOpenChat(chatId, friend.uid, friend.username)
                        }
                    },
                    onRemoveClick = {
                        viewModel.removeFriend(friend.uid)
                    }
                )
            }
        }
    }
}

// ── Reusable components ─────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = MilitaryGreen,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SearchResultItem(
    username: String,
    status: SocialViewModel.UserStatus,
    onAddClick: () -> Unit,
    onAcceptClick: () -> Unit,
    onCancelClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(username)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = username,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            
            when (status) {
                SocialViewModel.UserStatus.NONE -> {
                    IconButton(onClick = onAddClick) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Send request",
                            tint = MilitaryGreen
                        )
                    }
                }
                SocialViewModel.UserStatus.REQUEST_SENT -> {
                    Button(
                        onClick = onCancelClick,
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Cancel Request", fontSize = 12.sp)
                    }
                }
                SocialViewModel.UserStatus.REQUEST_RECEIVED -> {
                    Row {
                        IconButton(onClick = onAcceptClick) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Accept",
                                tint = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
                SocialViewModel.UserStatus.FRIEND -> {
                    Row {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Friends",
                            tint = MilitaryGreen,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        IconButton(
                            onClick = onRemoveClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove friend",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRequestItem(
    username: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(username)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = username,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            IconButton(onClick = onAccept) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Accept",
                    tint = Color(0xFF4CAF50)
                )
            }
            IconButton(onClick = onDecline) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Decline",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun FriendItem(
    username: String,
    onChatClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChatClick)
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(username)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = username,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Icon(
                Icons.Default.Email,
                contentDescription = "Chat",
                tint = MilitaryGreen,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Remove friend",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AvatarCircle(username: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MilitaryGreen.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = username.firstOrNull()?.uppercase() ?: "?",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}
