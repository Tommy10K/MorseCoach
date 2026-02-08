MorseCoach Social Plan (Friends + Chats)

0. Quick review of social_update.md (tweaks)

- The existing plan covers friends + chat metadata/messages, but it skips friend requests, user search, and chat list queries.
- Add a friend request flow (pending/accepted) to avoid instant mutual additions.
- Add a user search strategy and indexing notes (by username, and optionally email if permitted).
- Add chat list queries (recent chats) and message constraints for Morse-only input.
- Add translation request UX and minimal data handling.

1. Requirements (final)

- New top-level section in the app: Social.
- Social screen includes two tabs: Chats and Friends.
- Friends tab:
  - Search users and send friend requests.
  - Show incoming friend requests and allow accept/decline.
  - Show current friends list.
- Chats tab:
  - List most recent chats (like WhatsApp/Instagram).
  - Tap a chat to open.
  - Chat view shows last 100 messages between two users.
  - Message text limited to 200 Morse characters (., -, / and space only).
  - Input uses Dit, Dah, Letter space, Word space (no free typing).
  - Tap a message to request translation into natural language (display locally; no server write required).
- UI: modern, consistent with "Modern Military" theme.

2. Firestore data model (revised)

2.1. Users

Existing users collection remains unchanged, but we will rely on indexed fields for search:
- users/{uid}
  - username: String
  - email: String (already exists)

2.2. Friends (accepted)

- Path: users/{userId}/friends/{friendId}
- Schema:
  - username: String
  - addedAt: Timestamp

2.3. Friend requests (new)

Use a subcollection for incoming requests to the target user:
- Path: users/{targetUid}/friend_requests/{requestId}
- requestId: deterministic or auto ID, include senderId in data
- Schema:
  - fromUserId: String
  - fromUsername: String
  - createdAt: Timestamp
  - status: String ("pending")

Optionally also store an outgoing request marker to prevent double-sends:
- Path: users/{senderUid}/friend_requests_out/{targetUid}
- Schema:
  - toUserId: String
  - toUsername: String
  - createdAt: Timestamp
  - status: String ("pending")

2.4. Chats

- Path: chats/{chatId}
- chatId: min(uid1, uid2) + "_" + max(uid1, uid2)
- Schema:
  - participants: [uid1, uid2]
  - lastMessage: String
  - lastMessageTimestamp: Timestamp

2.5. Messages

- Path: chats/{chatId}/messages/{messageId}
- Schema:
  - senderId: String
  - text: String (max 200 chars, Morse-only)
  - timestamp: ServerTimestamp

3. Security rules (additions)

3.1. Friends

- Allow only authenticated users.
- Prefer restricting to path userId in production:
  - allow read, write: if request.auth != null && request.auth.uid == userId;

3.2. Friend requests

- Incoming requests:
  - allow read, write: if request.auth != null && request.auth.uid == userId;
- Outgoing requests (if used):
  - allow read, write: if request.auth != null && request.auth.uid == senderUid;

3.3. Chats and messages

- allow read/write only for participants.
- Pseudocode:
  - allow read, write: if request.auth != null
    && request.auth.uid in resource.data.participants;
- For message create, check membership against parent chat.

4. Query and indexing plan

4.1. User search

- Search by username prefix (case-insensitive recommended):
  - Store a normalized "usernameLower" in users documents.
  - Query: where("usernameLower", ">=", prefix)
           .where("usernameLower", "<", prefix + "\uf8ff")
- Email search should be optional or limited to exact match to avoid privacy issues.

4.2. Chat list

- Query chats collection filtered by participant uid:
  - whereArrayContains("participants", currentUid)
  - orderBy("lastMessageTimestamp", DESC)
  - limit(50)
- Ensure composite index if needed (Firestore console will prompt).

4.3. Messages

- Query last 100 messages:
  - orderBy("timestamp", DESC)
  - limit(100)
- Reverse in UI for chronological display.

5. Kotlin structure (new files and responsibilities)

5.1. Data models

- FriendUser
  - uid: String
  - username: String
  - addedAt: Timestamp

- FriendRequest
  - fromUserId: String
  - fromUsername: String
  - createdAt: Timestamp

- ChatSummary
  - chatId: String
  - otherUserId: String
  - otherUsername: String
  - lastMessage: String
  - lastMessageTimestamp: Timestamp

- ChatMessage
  - senderId: String
  - text: String
  - timestamp: Timestamp

5.2. SocialRepository

Core functions:
- getChatId(uid1, uid2): String
- searchUsersByUsername(prefix): Flow<List<UserSummary>>
- sendFriendRequest(currentUid, targetUid, targetUsername)
- acceptFriendRequest(currentUid, fromUid, fromUsername)
- declineFriendRequest(currentUid, requestId)
- getIncomingRequests(currentUid): Flow<List<FriendRequest>>
- getFriends(currentUid): Flow<List<FriendUser>>
- getChatSummaries(currentUid): Flow<List<ChatSummary>>
- sendMessage(chatId, senderId, text)
- getMessages(chatId): Flow<List<ChatMessage>>

Notes:
- Use batch writes for acceptFriendRequest to write both friends entries and remove request docs.
- Validate Morse-only input and max length 200 before send.

5.3. SocialViewModel

State:
- tabs: Chats, Friends
- searchQuery
- searchResults
- incomingRequests
- friendsList
- chatSummaries
- activeChatMessages
- uiEvents (errors, toasts)

6. UI and navigation

6.1. Navigation

- Add new route: social
- Add child routes:
  - social (tabs)
  - chat/{chatId}/{otherUserId}

6.2. Social screen layout

- Top app bar: Social
- Tabs: Chats | Friends
- Background and cards consistent with Modern Military theme.

6.3. Chats tab

- LazyColumn of chat summaries (avatar placeholder, username, last message, time).
- Empty state: "No chats yet" with CTA to add friends.
- Tap item -> ChatScreen.

6.4. Friends tab

- Search bar + results list
- "Add" button to send request
- Incoming requests section at top
  - Accept / Decline buttons
- Friends list section

6.5. Chat screen

- Message list (LazyColumn reverse layout)
- Each message bubble uses Morse font style (existing typography or new style).
- Input area with Dit, Dah, Letter space, Word space buttons.
- Send button disabled if empty.
- Tap a message to toggle translation display.
  - Translate Morse to text using local MorseData map.
  - Show translation under the message bubble (no server write).

7. Morse constraints and translation logic

- Allowed characters: '.', '-', ' ' (letter space), '/' (word space)
- Enforce max length 200 on the input buffer.
- If user tries to send invalid text, show a snackbar and keep input.
- Translation logic:
  - Split by '/' for words.
  - Split words by spaces into letters.
  - Use codeToLetter map, fallback to '?' for unknown.

8. Firestore rules update steps

- Update rules in Firebase console for:
  - users/{userId}/friends
  - users/{userId}/friend_requests
  - users/{userId}/friend_requests_out (if used)
  - chats/{chatId} and chats/{chatId}/messages
- Test with a non-admin account to avoid hidden permission issues.

9. Build and integration steps

- Add Social entry to Home screen.
- Add Social menu item to the main navigation cards.
- Wire NavHost routes in MainActivity.
- Create new screens:
  - SocialScreen (tabs)
  - ChatScreen
- Add SocialRepository and SocialViewModel to DI pattern used in the app.
- Add Firestore indexes as prompted.

10. Testing checklist

- Search:
  - Exact match, prefix, no results
- Friend requests:
  - Send, accept, decline, duplicate prevention
- Friends list:
  - Both users see each other after accept
- Chats:
  - Chat list updates when new message is sent
  - Order by last message time
- Messages:
  - Max length 200 enforced
  - Only Morse symbols allowed
  - Last 100 messages loaded
  - Translation toggle works and persists during scroll
- Security:
  - Verify users cannot read/write other users' requests
  - Verify chat access only for participants

11. Risks and decisions

- Username search requires a normalized field for prefix queries.
- Email search may be limited for privacy; prefer username.
- Deterministic chatId prevents duplicate chats.
- Message translation is local-only to avoid extra writes.
