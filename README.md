# Sudoku Threads & Protobuf – Assignment 4

A multithreaded Java / gRPC‑style (protobuf) Sudoku server and CLI client. Each connected client gets its **own game board**, but everyone shares a **persistent, thread‑safe leaderboard** stored in `logs.txt`.  The server fully implements the protocol in `/` and passes the provided JUnit tests.

Players can type `exit` at almost any prompt to quit gracefully.

---

## Quick‑start

### 1 – Clone & build

```bash
# clone your fork and cd into it
$ gradle generateProto   # compiles *.proto → Java sources (done automatically by other tasks)
$ gradle build           # full build & unit tests
```

### 2 – Run the threaded server

```bash
# defaults: port=8000, grading board=true, bindHost=0.0.0.0 (all interfaces)
$ gradle runThreadedSudoku

# custom examples
$ gradle runThreadedSudoku -Pport=9000                    # same board, diff port
$ gradle runThreadedSudoku -Pgrading=false                # random board each game
$ gradle runThreadedSudoku -Phost=127.0.0.1 -Pport=7777   # bind only to localhost
$ gradle runThreadedSudoku -DmaxThreads=20                # cap thread‑pool at 20
```

All three arguments are forwarded to `server.ThreadedSudokuServer  <port> <gradingMode> [bindHost]`.

### 3 – Run the reference CLI client

```bash
# same defaults as above (host=localhost, port=8000)
$ gradle runClient

# supply server details explicitly
$ gradle runClient -Phost=192.168.1.50 -Pport=9000
```

> **Tip:** append `-q --console=plain` for a cleaner gameplay display.

---

## Gradle tasks summary

| Task                | Purpose                                                          |
| ------------------- | ---------------------------------------------------------------- |
| `generateProto`     | Compile Protocol Buffers definitions → Java source               |
| `build`             | Compile & run unit tests                                         |
| `runClient`         | Start CLI client (uses `-Phost` & `-Pport` props)                |
| `runThreadedSudoku` | Multithreaded server (`-Pport`, `-Pgrading`, `-Phost`)           |
| `runServerGrading`  | **Single‑thread** grading server – always serves the fixed board |

---

## Protocol compliance

- Fully adheres to \`\` – every request/response type is implemented.
- Unit tests (`ServerTest`) supplied by the instructor all pass.
- Extra validation: row/col/value range checking, duplicate detection, preset‑value protection.

---

## Thread‑safety & Leaderboard

- `ThreadedSudokuServer` uses a cached thread pool (or fixed pool via `-DmaxThreads`).
- **Leaderboard log writes are synchronised** on a single `LOG_LOCK` object so multiple clients can update concurrently without corruption.
- Each client runs with an isolated `Game` instance, so game state never leaks between players.

---

## Screencast
Video Link: [YouTube](https://www.youtube.com/playlist?list=PLNJf3PhE4U6D9uje1Qz3t28TqqqIoDkZQ)

*Link here once recorded – ≤ 4 minutes demonstrating build, multiple clients, leaderboard & graceful quit.*

---

## Requirements Checklist

### Build / Protocol
- [x] Single **Gradle** build with tasks: `runClient`, `runServer`, `runServerGrading`, `runThreadedSudoku`
- [x] `generateProto` compiles the provided `.proto` files **without modification**
- [x] All enums/fields from `Request.proto` / `Response.proto` used exactly as defined
- [x] `gradle test` – instructor JUnit suite passes

### Core Gameplay
- [x] Server sends **GREETING** with `menuoptions`, `next = 2`
- [x] Main‑menu requests handled: **LEADERBOARD**, **START**, **QUIT**
- [x] In‑game requests handled: **UPDATE**, **CLEAR**, **QUIT**
- [x] Difficulty 1‑20 validated; out‑of‑range ⇒ `ERROR` (5)
- [x] Duplicate / preset / grid / row / col detection ⇒ correct `EvalType`
- [x] Win detection (no `X` left) ⇒ **WON** response, +20 points
- [x] Point system: ‑2 duplicates/preset, ‑5 any clear/new board, +20 win
- [x] `exit` anywhere in game ⇒ graceful **BYE**

### Threading / Concurrency
- [x] Each client handled in its **own thread** (ExecutorService or manual)
- [x] Shared **leaderboard** map & log file updates are **synchronised**
- [x] Optional `-DmaxThreads=` JVM prop caps the thread‑pool size

### Leaderboard & Persistence
- [x] Leaderboard survives server restarts (`logs.txt`)
- [x] Entry auto‑created on first login; `logins` counter increments thereafter
- [x] Points aggregated per player name
- [x] Leaderboard returned via `LEADERBOARD` response (repeated `Entry`)

### Robustness / UX
- [x] Malformed or unexpected requests ⇒ `ERROR` with correct `errorType`
- [x] Server prints the **full (un‑hidden) board** to console for graders
- [x] Handles abrupt client disconnects without crashing
- [x] Command‑line flags:  
  &nbsp;&nbsp;`-Pport` (server & client) `-Phost` (client) `-Pgrading` (server fixed board)  
  &nbsp;&nbsp;`[bindHost]` third arg on server for custom NIC/IP
- [ ] Screencast ≤ 4 min linked in README


---

© 2025 Your Name – CS ### Module 4

---

# Activity 2: Threads and Protobuf (Sudoku Game) — Requirement Document

## Overview

Create a Sudoku game system using **Protobuf** messages over TCP sockets. The server owns all game logic, state, and the global leaderboard, while many clients can connect and play concurrently.

---

## System Requirements

### Functional

- **Protocol compliance** – implement the provided `.proto` file and follow `PROTOCOL.md` *exactly*; do **not** modify field names, numbers, or enum values.
- **Per‑client game state** – each connected client gets an independent Sudoku board at its chosen difficulty.
- **Shared leaderboard** – a single leaderboard object is visible to all clients and persists across server restarts.
- **Persistent storage** – leaderboard written to `logs.txt` (or similar) on every update and re‑loaded at start‑up.
- **Graceful commands** – clients can choose difficulty, make guesses, clear board sections, request a new board, view the leaderboard, or quit.

### Non‑Functional

- **Thread‑safe** – every client runs in its own thread; shared data (leaderboard & log file) protected by synchronization.
- **Robust** – malformed requests or network drops must not crash the server; respond with `ERROR` messages where appropriate.
- **Scalable** – the server must keep accepting new clients (no global blocking) and cleanly reap finished threads.

---

## Protocol & Core Logic

Follow the message flow described in `PROTOCOL.md`.

| Stage     | Client → Server (`Request`)      | Server → Client (`Response`)                  |
| --------- | -------------------------------- | --------------------------------------------- |
| Login     | `NAME` (name)                    | `GREETING` (message, `menuoptions`, `next=2`) |
| Main Menu | `LEADERBOARD` • `START` • `QUIT` | `LEADERBOARD` • `START` • `BYE` • `ERROR`     |
| In Game   | `UPDATE` • `CLEAR` • `QUIT`      | `PLAY` • `WON` • `BYE` • `ERROR`              |

*Note:* **All numeric coordinates are 1‑based** for the client UI and converted to zero‑based indices on the server.

---

## Game Flow & Features

### Menu

1. **Leaderboard** – prints an unsorted list of `name / points / logins`.
2. **Play Game** – prompts for difficulty `1‑20` and starts a new board.
3. **Quit** – closes the connection politely.

### Leaderboard

- Backed by an in‑memory `LinkedHashMap<String, PlayerStats>`.
- Synchronized block (or `ReentrantReadWriteLock`) around all read/write operations.
- Automatically creates an entry on **first login**, increments `logins` counter every subsequent connection, and adds `points` after each finished game.

### Game Rules & Scoring

| Action                                | Points  |
| ------------------------------------- | ------- |
| Win puzzle                            | **+20** |
| Duplicate row/col/grid OR preset cell | **‑2**  |
| Any `CLEAR` or new board              | **‑5**  |

A **win** is detected when the board contains *no* `X` characters.

---

## Threading & Concurrency

- `SockBaseServer` accepts each socket and immediately hands it to `ClientWorker` (implements `Runnable`).
- `ExecutorService` (cached or fixed) may be used for pooling; otherwise spawn a new `Thread` per client.
- **Shared resources:**
  - Leaderboard map
  - Log file handle Use `synchronized` blocks or higher‑level locks to avoid races.

---

## Build, Test & Deployment

### Gradle Tasks

| Task               | Description                           | Default Params                 |
| ------------------ | ------------------------------------- | ------------------------------ |
| `generateProto`    | Compile protobuf definitions.         | –                              |
| `runServer`        | Start random‑board server.            | `-Pport=8000` `-Phost=0.0.0.0` |
| `runServerGrading` | Start fixed‑board server for graders. | same                           |
| `runClient`        | Launch CLI client.                    | `-Phost=localhost -Pport=8000` |

### Testing & Peer Review

- Publish your public endpoint in **#servers** Slack channel.
- Verify at least **two** other classmates’ servers and leave constructive feedback.

### Screencast

Provide a ≤4 min video demonstrating:

1. Starting server & two clients.
2. Showing independent boards but a shared leaderboard.
3. Demonstrating error handling (duplicate, preset, clear row, etc.).

---

