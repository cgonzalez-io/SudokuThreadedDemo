# Activity 2: Threads and Protobuf (Sudoku Game) — Requirement Document

## Overview
Create a Sudoku game system using Protobuf-based communication. The server manages logic, state, and leaderboard, while multiple clients interact concurrently.

---

## System Requirements

### Functional
- Implement protocol exactly as per `PROTOBUF.proto` and `PROTOCOL.md`.
- Server supports:
  - Game state management per client.
  - Leaderboard shared across all clients.
  - Persistent leaderboard storage.
- Clients:
  - Communicate via defined Protobuf messages.
  - Select difficulty, input guesses, clear board, and quit game gracefully.

### Non-Functional
- Server handles client crashes or disconnections without failure.
- Robust error handling for malformed or invalid input.
- Threads safely manage client sessions and shared data (leaderboard).

---

## Protocol & Core Logic

### Requirements
- Use the provided `.proto` file without modifications.
- Correctly serialize/deserialize messages between client and server.
- Follow structure for game state responses, user actions, leaderboard updates.

---

## Game Flow & Features

### Menu
- Menu shown upon login via greeting message.
- Options:
  1. View Leaderboard
  2. Play Game
  3. Quit

### Leaderboard
- Shared, persistent data structure.
- Thread-safe updates.
- Displays:
  - Player name
  - Score
  - Login count

### Game Logic
- Server manages:
  - New board generation based on difficulty.
  - In-game actions (guess, clear, new board).
  - Win detection (no Xs left).
  - Point system:
    - +20 for win
    - -2 for invalid/pre-set value
    - -5 for clear/new board
- Client displays and formats:
  - Current board state
  - Input instructions (row, col, value)
  - End-game and return to main menu
- Game ends:
  - With main menu quit option
  - Or user inputs `exit` in-game (sends QUIT to server)

---

## Threading and Concurrency

### Requirements
- Each client handled in its own thread.
- Synchronize access to leaderboard and persistent storage.
- Non-blocking interaction among multiple clients.

---

## Testing & Deployment

### Requirements
- Use `runServerGrading` task to ensure correct fixed board.
- Publish server endpoint (IP:port) on Slack `#servers`.
- Test and comment on at least 2 peer servers.
- Server must print full board (not hidden) for grading.

---

## Deliverables
- Working server and client matching the protocol.
- Thread-safe and persistent leaderboard.
- Game logic implementation with all core features.
- Gradle build and run instructions.
- Screencast (≤ 4 minutes).
- Final upload to GitHub and Canvas.
