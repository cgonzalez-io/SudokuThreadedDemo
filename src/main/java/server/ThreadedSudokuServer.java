package server;

import buffers.RequestProtos.Logs;
import buffers.RequestProtos.Message;
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
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * The ThreadedSudokuServer class implements a multi-threaded server for managing
 * Sudoku games. This server handles multiple client connections and assigns each
 * connection to a distinct thread. It supports interactive gameplay, a leaderboard,
 * and other utilities for a Sudoku game environment.*
 * Key Features:
 * 1. Multi-threaded management of client connections using an executor pool.
 * 2. Isolation of game state per client.
 * 3. Parsing and handling of client requests to interact with the game.
 * 4. Logging of client actions and leaderboard aggregation for user scoring.
 * Thread Safety:
 * - Shared state changes, such as logging, are guarded by proper synchronization mechanisms.
 * - Each client gets its own isolated game instance to prevent state conflicts.
 * Error Handling:
 * - Ensures correct command input and checks boundary rules for ports, operations, and in-game actions.
 * Configuration:
 * - Can be run with a customizable network configuration for port and bind host.
 * - Supports enabling or disabling grading mode through command line arguments.
 * Logging:
 * - Logs key events such as client connections, disconnections, and actions to a file.
 * Usage:
 * Intended to be run as a standalone server application. The main method initializes
 * the server, sets up the thread pool for client management, and listens for incoming
 * connections.
 * Components:
 * - The main method initializes server configuration and client thread pooling.
 * - The ClientTask inner runnable handles client-side operations.
 * - Various helper methods generate appropriate responses to client requests, such as
 */
public class ThreadedSudokuServer {
    public static final String MENU_MAIN = "\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game";
    public static final String MENU_GAME = "\nChoose an action: \n (1-9) - Enter an int to specify the row you want to update \n c - Clear number \n r - New Board";
    private static final Logger logger = LoggerFactory.getLogger(ThreadedSudokuServer.class);
    // ────────────────────────────────────────────────────────────────────────────
    // Static configuration / shared state
    // ────────────────────────────────────────────────────────────────────────────
    private static final String LOG_FILENAME = "logs.txt";
    /**
     * single log‑file write lock
     */
    private static final Object LOG_LOCK = new Object();
    private static volatile boolean gradingMode = true;

    // ────────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            logger.error("Usage: java server.ThreadedSudokuServer <port> <gradingMode(true|false)> [bindHost]");
            System.exit(1);
        }

        /* ───── parse CLI ───── */
        int port;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            logger.error("<port> must be an integer");
            return;
        }
        gradingMode = Boolean.parseBoolean(args[1]);
        final String bindHost = (args.length == 3) ? args[2] : "0.0.0.0";
        final InetAddress bindAddr = InetAddress.getByName(bindHost);

        /* ───── executor for client threads ───── */
        int maxThreads = Integer.getInteger("maxThreads", -1);
        try (ExecutorService pool = (maxThreads > 0)
                ? Executors.newFixedThreadPool(maxThreads)
                : Executors.newCachedThreadPool()) {

            /* ───── accept loop ───── */
            try (ServerSocket server = new ServerSocket()) {
                server.bind(new InetSocketAddress(bindAddr, port));
                logger.info("ThreadedSudokuServer listening on {}:{}  | grading={}", bindHost, port, gradingMode);

                int clientId = 1;
                while (true) {
                    Socket clientSock = server.accept();
                    logger.debug("Accepted connection #{} from {}", clientId, clientSock.getRemoteSocketAddress());

                    // Each client gets its own Game instance (isolated state)
                    Game g = new Game();
                    pool.execute(new ClientTask(clientSock, g, clientId++));
                }
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────────
    // ClientTask runnable – mostly your previous handler, but isolated per thread
    // ────────────────────────────────────────────────────────────────────────────
    private record ClientTask(Socket sock, Game game, int id) implements Runnable {
        @Override
        public void run() {
            try (sock; InputStream in = sock.getInputStream(); OutputStream out = sock.getOutputStream()) {
                int state = 1;            // 1=name, 2=menu, 3=game
                String name = "";
                boolean inGame = false;
                while (true) {
                    Request req = Request.parseDelimitedFrom(in);
                    if (req == null) break; // client hung up

                    Response resp;
                    switch (req.getOperationType()) {
                        case NAME -> {
                            if (req.getName().isBlank()) {
                                resp = errorResp(1, state);
                            } else {
                                name = req.getName();
                                writeLog(name, Message.CONNECT);
                                state = 2;
                                resp = Response.newBuilder()
                                        .setResponseType(Response.ResponseType.GREETING)
                                        .setMessage("Hello " + name + " and welcome to a simple game of Sudoku.")
                                        .setMenuoptions(MENU_MAIN)
                                        .setNext(state)
                                        .build();
                            }
                        }
                        case LEADERBOARD -> resp = leaderboardResp();
                        case START -> {
                            if (req.getDifficulty() < 1 || req.getDifficulty() > 20) {
                                resp = errorResp(5, state);
                            } else {
                                game.newGame(gradingMode, req.getDifficulty());
                                state = 3;
                                inGame = true;
                                resp = Response.newBuilder()
                                        .setResponseType(Response.ResponseType.START)
                                        .setBoard(game.getDisplayBoard())
                                        .setMessage("\nStarting new game.")
                                        .setMenuoptions(MENU_GAME)
                                        .setNext(state)
                                        .build();
                            }
                        }
                        case UPDATE -> resp = updateResp(req);
                        case CLEAR -> resp = clearResp(req);
                        case QUIT -> {
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

        /**
         * Generates a leaderboard response by aggregating player logs and constructing a response
         * with player entries including their names, points, and login counts.
         *
         * @return a Response object containing the leaderboard data along with menu options and navigation information.
         * @throws IOException if there is an error in accessing or processing the required resources.
         */
        /* -------------------------------------------------- helper responses */
        private Response leaderboardResp() throws IOException {
            Map<String, int[]> agg = aggregateLogs();
            Response.Builder rb = Response.newBuilder()
                    .setResponseType(Response.ResponseType.LEADERBOARD)
                    .setMenuoptions(MENU_MAIN)
                    .setNext(2);
            for (Map.Entry<String, int[]> e : agg.entrySet()) {
                rb.addLeader(ResponseProtos.Entry.newBuilder()
                        .setName(e.getKey()).setPoints(e.getValue()[1]).setLogins(e.getValue()[0]).build());
            }
            logger.debug("Leaderboard: {}", agg);
            return rb.build();
        }

        /**
         * Updates the game board based on the input request and evaluates the result of the operation.
         *
         * @param req the request object containing the row, column, and value to update in the game board
         * @return a Response object representing the outcome of the update, including the current state of the board,
         * game status, evaluation result type, and other relevant details
         */
        private Response updateResp(Request req) {
            int row = req.getRow(), col = req.getColumn(), val = req.getValue();
            if (row < 1 || row > 9 || col < 1 || col > 9 || val < 1 || val > 9) return errorResp(3, 3);
            int res = game.updateBoard(row - 1, col - 1, val, 0);
            Response.EvalType et = switch (res) {
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
                game.setPoints(20);
                return Response.newBuilder().setResponseType(Response.ResponseType.WON)
                        .setBoard(game.getDisplayBoard()).setType(et).setMenuoptions(MENU_MAIN)
                        .setMessage("You solved the current puzzle, good job!")
                        .setPoints(game.getPoints()).setNext(2).build();
            }
            return Response.newBuilder().setResponseType(Response.ResponseType.PLAY)
                    .setBoard(game.getDisplayBoard()).setType(et).setMenuoptions(MENU_GAME)
                    .setPoints(game.getPoints()).setNext(3).build();
        }

        /**
         * Processes a request to clear specific elements or reset the game board based
         * on the provided input. It validates the row and column indices, determines the
         * clear operation type, and updates the game state accordingly.
         *
         * @param req The request object containing the row, column, and clear operation values.
         *            The row and column indices must be within the valid range, and the
         *            clear operation type must be a valid enumeration.
         * @return A Response object that includes the updated board, response type,
         **/
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
            game.setPoints(-5);
            game.updateBoard(r, c, t, t);
            return Response.newBuilder().setResponseType(Response.ResponseType.PLAY)
                    .setBoard(game.getDisplayBoard()).setType(et).setMenuoptions(MENU_GAME)
                    .setPoints(game.getPoints()).setNext(3).build();
        }

        /**
         * Constructs an error response based on the provided error code and next state.
         *
         * @param code The error code indicating the type of error.
         *             Valid values include:
         *             1 - required field missing or empty
         *             2 - request not supported
         *             3 - row or column out of bounds
         *             5 - difficulty is out of range
         *             Any other value results in a generic error message.
         * @param next The next state to be included in the response.
         * @return A Response object configured with the error type, message, and next state
         */
        private Response errorResp(int code, int next) {
            String msg = switch (code) {
                case 1 -> "\nError: required field missing or empty";
                case 2 -> "\nError: request not supported";
                case 3 -> "\nError: row or col out of bounds";
                case 5 -> "\nError: difficulty is out of range";
                default -> "\nError: cannot process your request";
            };
            return Response.newBuilder().setResponseType(Response.ResponseType.ERROR)
                    .setErrorType(code).setMessage(msg).setNext(next).build();
        }

        /**
         * Aggregates login log entries from a log file into a structured map.
         * Each key in the map represents a unique player name, and the associated value
         * is an array where the first element indicates the number of logins.
         * The log is expected to be in a specific format, where each entry contains a player name
         * preceded by a colon (:) and followed by a dash (-). If an entry does not conform to this
         * format, it is skipped.
         * The method reads the log file in a thread-safe manner using a synchronized block on the LOG_LOCK
         */
        /* ------------------------------ leaderboard aggregation helpers */
        private Map<String, int[]> aggregateLogs() {
            Map<String, int[]> agg = new LinkedHashMap<>();
            synchronized (LOG_LOCK) {
                try (var fis = new FileInputStream(LOG_FILENAME)) {
                    Logs logs = Logs.parseFrom(fis);
                    for (String line : logs.getLogList()) {
                        int dash = line.indexOf(" - ");
                        if (dash < 0) continue;
                        int lastCol = line.lastIndexOf(':', dash);
                        if (lastCol < 0) continue;
                        String n = line.substring(lastCol + 1, dash).trim();
                        agg.computeIfAbsent(n, k -> new int[]{0, 0})[0]++; // ++logins
                    }
                } catch (Exception ignored) {
                    logger.warn("Log read failed: {}", ignored.getMessage());
                }
            }
            return agg;
        }

        /**
         * Writes a log entry related to a specific player and message to a persistent log file.
         * The method ensures thread-safe access to the log file and appends a new log entry
         * containing the date, player name, and the associated message.
         *
         * @param playerName the name of the player for whom the log entry is being created
         * @param m          the message associated with the player's action
         */
        private void writeLog(String playerName, Message m) {
            synchronized (LOG_LOCK) {
                try {
                    Logs.Builder lb = Logs.newBuilder();
                    try (var fis = new FileInputStream(LOG_FILENAME)) {
                        lb.mergeFrom(fis);
                    } catch (FileNotFoundException ignored) {
                    }
                    lb.addLog(new Date() + ": " + playerName + " - " + m);
                    try (var fos = new FileOutputStream(LOG_FILENAME)) {
                        lb.build().writeTo(fos);
                    }
                } catch (Exception ex) {
                    logger.warn("Log write failed: {}", ex.getMessage());
                }
            }
        }
    }
}
