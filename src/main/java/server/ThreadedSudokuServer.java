package server;

import buffers.RequestProtos.Request;
import buffers.ResponseProtos;
import buffers.ResponseProtos.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * A multi-threaded Sudoku server that manages client connections to provide a Sudoku gaming
 * experience. This server listens for client connections and handles client requests, such as
 * starting a game, updating the Sudoku board, checking leaderboard scores, and more.
 * It maintains thread-safe in-memory storages for tracking login counts and high scores.
 * The server allows game states to be updated dynamically based on the requests from
 * connected clients and supports concurrent gameplay for multiple users.
 * Features include:
 * - Leaderboard management
 * - Thread-safe handling of user scores and login statistics
 * - Game state management
 * - Dynamic generation of Sudoku puzzles
 * - Multi-threaded client handling
 */
public class ThreadedSudokuServer {
    /**
     * A constant string representing the main menu options for the application.
     * The menu displays the following options:
     * 1 - View the leader board
     * 2 - Enter a game
     * 3 - Quit the game
     */
    public static final String MENU_MAIN = "\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game";
    /**
     * MENU_GAME is a constant string that represents the game menu for the Sudoku server.
     * It provides the user with instructions for available actions during the game.
     * Users can select an action by entering specific inputs based on the provided options:
     * - An integer (1-9) to update a particular row in the Sudoku board.
     * - 'c' to clear a number.
     * - 'r' to generate a new board.
     */
    public static final String MENU_GAME = "\nChoose an action: \n (1-9) - Enter an int to specify the row you want to update \n c - Clear number \n r - New Board";
    /**
     * A logger instance used for recording log messages in the ThreadedSudokuServer class.
     * It facilitates logging activities such as debugging, error reporting, and informational
     * messages to assist in monitoring and troubleshooting the application.
     * The logger is initialized with the logging configurations defined for the class.
     */
    private static final Logger logger = LoggerFactory.getLogger(ThreadedSudokuServer.class);
    /**
     * A thread-safe in-memory leaderboard tracking user login counts.
     * <p>
     * This map associates a username (represented as a String) with an
     * {@link AtomicInteger} that maintains the count of logins for that user.
     * <p>
     * Utilizes a {@link ConcurrentHashMap} to ensure thread-safe operations
     * when accessing or modifying the login counts.
     */
    // ────────────────────────────────────────────────────────────────────────────
    // In‐memory leader‐board state (thread‐safe)
    // ────────────────────────────────────────────────────────────────────────────
    private static final ConcurrentMap<String, AtomicInteger> loginCounts = new ConcurrentHashMap<>();
    /**
     * A thread-safe map to store high scores for a multiplayer Sudoku game.
     * The keys represent player identifiers (e.g., usernames), and the values
     * are atomic integers tracking their respective high scores. This ensures
     * that updates to player scores are performed in a safe manner across
     * multiple threads.
     */
    private static final ConcurrentMap<String, AtomicInteger> highScores = new ConcurrentHashMap<>();
    /**
     * Represents the file used to persist the state of the leaderboard.
     * This file is essential for saving and loading the state of the leaderboard
     * to maintain consistency across server sessions.
     */
    // persistence file
    private static final File STATE_FILE = new File("leaderboard.state");
    /**
     * A flag indicating whether the application is running in grading mode.
     * When set to true, the application enables specific functionality
     * designed for grading or evaluation purposes. This may impact the way
     * certain features are executed during runtime.
     */
    private static volatile boolean gradingMode = true;

    /**
     * Loads the leaderboard state from a persisted file, if it exists.
     * <p>
     * This method reads two serialized Maps from a predefined file and populates the
     * `loginCounts` and `highScores` static fields. The file is expected to contain login counts
     * and high scores, which will be deserialized and converted into thread-safe structures.
     * <p>
     * If the file does not exist, the method will exit without performing any operations. If an
     * exception occurs during the deserialization process, a warning will be logged via the `logger`.
     * <p>
     * Throws unchecked exceptions in case of critical I/O or deserialization issues.
     */
    private static void loadState() {
        if (!STATE_FILE.exists()) return;
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(STATE_FILE))) {
            @SuppressWarnings("unchecked")
            Map<String, Integer> savedLogins = (Map<String, Integer>) in.readObject();
            @SuppressWarnings("unchecked")
            Map<String, Integer> savedScores = (Map<String, Integer>) in.readObject();
            savedLogins.forEach((name, cnt) -> loginCounts.put(name, new AtomicInteger(cnt)));
            savedScores.forEach((name, pts) -> highScores.put(name, new AtomicInteger(pts)));
            logger.info("Loaded leaderboard state: {} players", loginCounts.size());
        } catch (Exception e) {
            logger.warn("Failed to load leaderboard state", e);
        }
    }

    /**
     * Saves the current state of the leaderboard, including login counts and high scores,
     * to a file specified by the constant STATE_FILE.
     * <p>
     * The method serializes two maps:
     * - A map of player names to their login counts.
     * - A map of player names to their high scores.
     * <p>
     * If the state is successfully saved, a log message is recorded indicating the number
     * of players whose data was saved. If an exception occurs during the save operation
     * (e.g., issues with file writing), a warning message is logged with the exception details.
     * <p>
     * The method ensures the output stream is properly closed after the operation,
     * using a try-with-resources block for safe resource management.
     */
    private static void saveState() {
        Map<String, Integer> toSaveLogins = new HashMap<>();
        loginCounts.forEach((name, ai) -> toSaveLogins.put(name, ai.get()));
        Map<String, Integer> toSaveScores = new HashMap<>();
        highScores.forEach((name, ai) -> toSaveScores.put(name, ai.get()));
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(STATE_FILE))) {
            out.writeObject(toSaveLogins);
            out.writeObject(toSaveScores);
            logger.info("Saved leaderboard state: {} players", toSaveLogins.size());
        } catch (IOException e) {
            logger.warn("Failed to save leaderboard state", e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            logger.error("Usage: java server.ThreadedSudokuServer <port> <gradingMode(true|false)> [bindHost]");
            System.exit(1);
        }

        // load persisted leaderboard on startup
        loadState();
        Runtime.getRuntime().addShutdownHook(new Thread(ThreadedSudokuServer::saveState));

        int port = Integer.parseInt(args[0]);
        gradingMode = Boolean.parseBoolean(args[1]);
        InetAddress bindAddr = InetAddress.getByName(args.length == 3 ? args[2] : "0.0.0.0");

        int maxThreads = Integer.getInteger("maxThreads", -1);
        ExecutorService pool = (maxThreads > 0)
                ? Executors.newFixedThreadPool(maxThreads)
                : Executors.newCachedThreadPool();

        try (ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(bindAddr, port));
            logger.info("ThreadedSudokuServer listening on {}:{}  | grading={}", bindAddr, port, gradingMode);

            int clientId = 1;
            while (true) {
                Socket clientSock = server.accept();
                logger.info("Accepted connection #{} from {}", clientId, clientSock.getRemoteSocketAddress());
                pool.execute(new ClientTask(clientSock, new Game(), clientId++));
            }
        }
    }

    /**
     * Represents a client task that handles communication between a connected client and the server.
     * Each instance of this class is associated with a specific client connection, identified by a
     * unique socket and an identifier. The main functionality is implemented within the {@code run}
     * method, which processes client requests, maintains the game state, and generates corresponding
     * responses.
     * <p>
     * The ClientTask class is designed to handle operations such as client authentication,
     * leaderboard retrieval, starting a new game, updating the game board, clearing values,
     * and quitting the session. It manages the interaction flow with the following core states:
     * - State 1: Client authentication (name input phase).
     * - State 2: Main menu phase.
     * - State 3: In-game phase.
     * <p>
     * This class uses helper methods to handle specific client requests such as game updates
     * and board clearing. It also supports error handling and provides appropriate responses
     * when client requests are invalid or cannot be processed.
     * <p>
     * Implements the {@link Runnable} interface to allow execution of this task inside a separate
     * thread.
     */
    private record ClientTask(Socket sock, Game game, int id) implements Runnable {
        @Override
        public void run() {
            try (sock; InputStream in = sock.getInputStream(); OutputStream out = sock.getOutputStream()) {
                int state = 1;            // 1 = name, 2 = main menu, 3 = in‑game
                String name = "";
                boolean inGame = false;
                while (true) {
                    Request req = Request.parseDelimitedFrom(in);
                    if (req == null) break; // client hung up

                    Response resp;
                    switch (req.getOperationType()) {
                        case NAME -> {
                            if (req.getName().isBlank()) {
                                logger.info("Client {} sent empty name", id);
                                resp = errorResp(1, state);
                            } else {
                                name = req.getName();
                                loginCounts.computeIfAbsent(name, k -> new AtomicInteger(0)).incrementAndGet();
                                saveState();  // persist after login
                                state = 2;
                                logger.info("Client {} logged in as {}", id, name);
                                resp = Response.newBuilder()
                                        .setResponseType(Response.ResponseType.GREETING)
                                        .setMessage("Hello " + name + " and welcome to a simple game of Sudoku.")
                                        .setMenuoptions(MENU_MAIN)
                                        .setNext(state)
                                        .build();
                            }
                        }

                        case LEADERBOARD -> {
                            // build from in‐memory maps
                            Response.Builder lb = Response.newBuilder()
                                    .setResponseType(Response.ResponseType.LEADERBOARD)
                                    .setMenuoptions(MENU_MAIN)
                                    .setNext(2);

                            loginCounts.forEach((player, logins) -> {
                                int best = highScores.getOrDefault(player, new AtomicInteger(0)).get();
                                lb.addLeader(
                                        ResponseProtos.Entry.newBuilder()
                                                .setName(player)
                                                .setLogins(logins.get())
                                                .setPoints(best)
                                                .build()
                                );
                            });
                            logger.info("Client {} requested leaderboard", id);
                            resp = lb.build();
                        }

                        case START -> {
                            logger.info("Client {} starting game", id);
                            int diff = req.getDifficulty();
                            if (diff < 1 || diff > 20) {
                                logger.info("Client {} requested invalid difficulty {}", id, diff);
                                resp = errorResp(5, state);
                            } else {
                                game.newGame(gradingMode, diff);
                                inGame = true;
                                state = 3;
                                logger.info("Client {} starting game with difficulty {}", id, diff);
                                logger.info("Game solution: {}", game.getSolutionBoard());
                                resp = Response.newBuilder()
                                        .setResponseType(Response.ResponseType.START)
                                        .setBoard(game.getDisplayBoard())
                                        .setMessage("\nStarting new game.")
                                        .setMenuoptions(MENU_GAME)
                                        .setNext(state)
                                        .build();
                            }
                        }
                        case UPDATE -> {
                            if (!inGame) {
                                logger.info("Client {} updating game without starting a game", id);
                                resp = errorResp(2, state);
                            } else {
                                resp = updateResp(req, name);
                            }
                        }

                        case CLEAR -> {
                            if (!inGame) {
                                logger.info("Client {} clearing game without starting a game", id);
                                resp = errorResp(2, state);
                            } else {
                                resp = clearResp(req);
                            }
                        }

                        case QUIT -> {
                            logger.info("Client {} quitting game", id);
                            resp = Response.newBuilder()
                                    .setResponseType(Response.ResponseType.BYE)
                                    .setMessage("Thank you for playing! goodbye.")
                                    .build();
                            resp.writeDelimitedTo(out);
                            return;
                        }
                        default -> resp = errorResp(2, state);
                    }
                    resp.writeDelimitedTo(out);
                }
            } catch (IOException ex) {
                logger.warn("Client {} disconnected: {}", id, ex.getMessage());
            }
        }

        // ────────────────────────────────────────────────────────────────────
        // Helpers
        // ────────────────────────────────────────────────────────────────────

        /**
         * Updates the game state based on the input request and player information while handling validation and scoring.
         *
         * @param req    the request object containing the row, column, and value to update the board
         * @param player the name of the player performing the action, used for managing high scores
         * @return a Response object that represents the updated game state, including the outcome of the update,
         * points, menu options, and any relevant messages
         */
        private Response updateResp(Request req, String player) {
            int row = req.getRow(), col = req.getColumn(), val = req.getValue();
            if (row < 1 || row > 9 || col < 1 || col > 9 || val < 1 || val > 9) return errorResp(3, 3);
            logger.info("Updating row={}, col={}, val={}", row, col, val);
            int result = game.updateBoard(row - 1, col - 1, val, 0);
            Response.EvalType et = switch (result) {
                case 0 -> Response.EvalType.UPDATE;
                case 1 -> {
                    game.setPoints(-2);
                    yield Response.EvalType.PRESET_VALUE;
                }
                case 2 -> {
                    game.setPoints(-2);
                    yield Response.EvalType.DUP_ROW;
                }
                case 3 -> {
                    game.setPoints(-2);
                    yield Response.EvalType.DUP_COL;
                }
                case 4 -> {
                    game.setPoints(-2);
                    yield Response.EvalType.DUP_GRID;
                }
                default -> Response.EvalType.UPDATE;
            };
            if (game.getWon()) {
                // +20 for win
                game.setPoints(20);
                int finalPts = game.getPoints();
                // update high‐score
                highScores
                        .computeIfAbsent(player, k -> new AtomicInteger(0))
                        .updateAndGet(old -> Math.max(old, finalPts));

                logger.info("Player {} won with {} points", player, finalPts);

                return Response.newBuilder().setResponseType(Response.ResponseType.WON)
                        .setBoard(game.getDisplayBoard()).setType(et).setMenuoptions(MENU_MAIN)
                        .setMessage("You solved the current puzzle, good job!")
                        .setPoints(finalPts)
                        .setNext(2)
                        .build();
            }
            return Response.newBuilder().setResponseType(Response.ResponseType.PLAY)
                    .setBoard(game.getDisplayBoard())
                    .setPoints(game.getPoints())
                    .setType(et)
                    .setMenuoptions(MENU_GAME)
                    .setNext(3)
                    .build();
        }

        /**
         * Processes the provided request to clear specific sections of the game board
         * based on the request's parameters. Updates the game state and deducts points
         * from the player's score if applicable.
         *
         * @param req The request containing the parameters for the clear operation,
         *            including the target row, column, and the type of clearing action.
         * @return A Response object containing the updated game state, board display,
         * evaluation type, updated points, and additional metadata. If the
         * request is invalid, an error response is returned.
         */
        private Response clearResp(Request req) {
            int r = req.getRow(), c = req.getColumn(), t = req.getValue();
            if ((r != -1 && (r < 1 || r > 9)) || (c != -1 && (c < 1 || c > 9))) return errorResp(3, 3);
            if (r != -1) r--;
            if (c != -1) c--;
            Response.EvalType et = switch (t) {
                case 1 -> Response.EvalType.CLEAR_VALUE;
                case 2 -> Response.EvalType.CLEAR_ROW;
                case 3 -> Response.EvalType.CLEAR_COL;
                case 4 -> Response.EvalType.CLEAR_GRID;
                case 5 -> Response.EvalType.CLEAR_BOARD;
                case 6 -> Response.EvalType.RESET_BOARD;
                default -> null;
            };
            if (et == null) return errorResp(2, 3);
            logger.info("Clearing row={}, col={}, type={}", r, c, t);
            game.setPoints(-5);
            game.updateBoard(r, c, t, t);
            return Response.newBuilder().setResponseType(Response.ResponseType.PLAY)
                    .setBoard(game.getDisplayBoard()).setType(et).setMenuoptions(MENU_GAME)
                    .setPoints(game.getPoints()).setNext(3).build();
        }

        /**
         * Constructs an error response based on the provided error code and next state.
         *
         * @param code the error code indicating the type of error
         * @param next the next state or menu option to be used in the response
         * @return a Response object configured as an error type response with a corresponding
         * message, error code, next state, and menu options
         */
        private Response errorResp(int code, int next) {
            logger.info("Error: code={}, next={}", code, next);
            String msg = switch (code) {
                case 1 -> "\nError: required field missing or empty";
                case 2 -> "\nError: request not supported";
                case 3 -> "\nError: row or col out of bounds";
                case 5 -> "\nError: difficulty is out of range";
                default -> "\nError: cannot process your request";
            };
            return Response.newBuilder().setResponseType(Response.ResponseType.ERROR)
                    .setErrorType(code)
                    .setMessage(msg)
                    .setNext(next)
                    .setMenuoptions(next == 2 ? MENU_MAIN : MENU_GAME)
                    .build();
        }
    }
}
