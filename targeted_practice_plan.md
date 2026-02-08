Targeted Practice Implementation Plan

Goal
Add a Targeted Practice option in Standard Practice that focuses on the 3 weakest characters, using data from both Racer challenges and Standard Practice. "Weakest" means most mistyped; if none are mistyped, fall back to slowest typed.

Step 1 - Data Model (Firestore)
1) Add a new map to the user document:
   - users/{uid}/charStats (Map<String, Map>)
   - Each entry key is a character ("A", "B", ...)
   - Each entry value holds:
     - attempts: Long
     - mistakes: Long
     - totalTimeMs: Long

Notes
- attempts increments on every typed character attempt.
- mistakes increments when the user types the wrong character.
- totalTimeMs accumulates the time spent to answer each prompt for that character.

Step 2 - Capture Standard Practice Data
1) In PracticeQuizScreen (standard practice), record per-character stats:
   - On prompt start: store start timestamp.
   - On answer submit: compute elapsed time.
   - If correct: attempts +1, totalTimeMs + elapsed.
   - If incorrect: attempts +1, mistakes +1, totalTimeMs + elapsed.
2) Write updates to Firestore using a batched update to the charStats map.

Step 3 - Capture Racer Challenge Data
1) In ChallengeViewModel (Racer mode), identify per-character errors.
2) For each character in the race prompt:
   - Track the final typed character vs expected.
   - If wrong, record a mistake for the expected character.
   - For timing, use per-character timing if available; otherwise approximate by dividing total time across characters.
3) Update the same charStats map in Firestore.

Step 4 - Targeted Character Selection Logic
1) Read charStats from Firestore on entering Standard Practice.
2) Build a list of candidate characters:
   - Sort by mistake rate = mistakes / attempts (descending).
   - If all mistakes == 0, sort by average time = totalTimeMs / attempts (descending).
3) Take the top 3 characters as the targeted set.
4) If fewer than 3 exist, fill remaining slots from the alphabet (or the user current lesson set).

Step 5 - Standard Practice Menu UI
1) In PracticeMenuScreen, add a third button in Standard Practice section:
   - "Targeted Practice"
2) When selected:
   - Navigate to practice_quiz with isRandom=false, and pass a flag like isTargeted=true.

Step 6 - Standard Practice Quiz Flow
1) When isTargeted=true:
   - Use the 3-character targeted set to create prompts.
   - No fixed sequence: each prompt randomly picks from the targeted set.
   - This behaves like a lesson but limited to 3 letters.
2) Keep existing UI; just adjust prompt source.

Step 7 - Empty Data Handling
1) If there are no charStats yet:
   - Fallback to random practice.
   - Show a short message: "No targeted data yet. Try a few practice or racer runs first."

Step 8 - Stats Visibility (Optional)
1) In StatsScreen, add a short "Weakest Characters" row:
   - Show top 3 characters and their mistake rate.

Step 9 - Testing Checklist
1) Standard practice updates charStats for correct and incorrect answers.
2) Racer challenge updates charStats for mistakes and timing.
3) Targeted selection returns 3 characters with expected ranking.
4) Targeted practice only uses those 3 characters.
5) Empty data fallback works without crashes.

Open Questions to Confirm
- Should the targeted set be limited to characters the user has already seen in lessons?
- For timing in Racer mode, is per-character timing available or should we approximate evenly?
