package server;

import buffers.RequestProtos.Logs;
import buffers.RequestProtos.Message;
import buffers.RequestProtos.Request;
import buffers.ResponseProtos;
import buffers.ResponseProtos.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A SockBaseServer that:
 * - Uses response.proto properly for LEADERBOARD, UPDATE, CLEAR
 * - Returns the correct EvalType for duplicates, preset-value, clearing, etc.
 * - Manages row/col bounds and game points
 */
public class SockBaseServer {
    private static final Logger logger = LoggerFactory.getLogger(SockBaseServer.class); // for logging
    // For your logs
    static String logFilename = "logs.txt";
    // Provided constants for menus:
    static String menuOptions = "\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game";
    static String gameOptions = "\nChoose an action: \n (1-9) - Enter an int to specify the row you want to update \n c - Clear number \n r - New Board";
    // If we should load the "grading" puzzle or random puzzle
    private static boolean grading = true;
    private final int id;      // client ID for logging
    Socket clientSocket;
    InputStream in;
    OutputStream out;
    Game game; // current Sudoku game
    private String name;       // player's name
    private int currentState = 1;  // 1 = name, 2 = main menu, 3 = in-game
    private boolean inGame = false;

    /**
     * Constructs a new SockBaseServer instance with the provided client socket, game instance, and client identifier.
     * Initializes input and output streams associated with the client socket.
     * Logs an error to the console if the initialization fails.
     *
     * @param sock The {@code Socket} object representing the connection to the client.
     * @param game The {@code Game} instance associated with this server-client interaction.
     * @param id   The unique identifier for the client connection.
     */
    public SockBaseServer(Socket sock, Game game, int id) {
        this.clientSocket = sock;
        this.game = game;
        this.id = id;
        try {
            in = clientSocket.getInputStream();
            out = clientSocket.getOutputStream();
        } catch (Exception e) {
            logger.error("Error in constructor: {}", String.valueOf(e));
        }
    }

    /**
     * MAIN - sets up the server socket, spawns SockBaseServer for each client
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            logger.error("Expected arguments: <port(int)> <gradingMode(true|false)>");
            System.exit(1);
        }

        int port;
        try {
            // parse port
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            logger.error("[Port must be an integer]");
            return;
        }
        try {
            grading = Boolean.parseBoolean(args[1]);
        } catch (Exception e) {
            logger.error("Grading mode must be 'true' or 'false'");
            return;
        }

        try (ServerSocket server = new ServerSocket(port)) {
            logger.debug("Server started on port {} with grading={}", port, grading);
            int id = 1;
            while (true) {
                Socket client = server.accept();
                logger.debug("Attempting to connect to client-{}", id);
                // Each client has its own game instance
                Game game = new Game();
                SockBaseServer session = new SockBaseServer(client, game, id++);
                session.startGame();
            }
        } catch (IOException e) {
            logger.error("Error in main: {}", String.valueOf(e));
            System.exit(2);
        }
    }

    /**
     * Main loop to read requests and dispatch them to handlers
     */
    public void startGame() throws IOException {
        try {
            while (true) {
                Request op = Request.parseDelimitedFrom(in);
                if (op == null) {
                    logger.error("Request is null");
                    // Client disconnected unexpectedly
                    break;
                }
                logger.debug("Got request: {}", op);
                Response response;

                boolean quit = false;

                switch (op.getOperationType()) {
                    case NAME:
                        response = handleName(op);
                        break;
                    case START:
                        response = handleStart(op);
                        break;
                    case LEADERBOARD:
                        response = handleLeaderboard();
                        break;

                    case UPDATE:
                        response = handleUpdate(op);
                        break;

                    case CLEAR:
                        response = handleClear(op);
                        break;

                    case QUIT:
                        response = handleQuit();
                        quit = true;
                        break;
                    default:
                        response = error(2, "Unsupported operation");
                        break;
                }
                //print state of board
                logger.debug("Current state: {} client: {}", currentState, id);

                // Send response
                response.writeDelimitedTo(out);

                if (quit) {
                    return; // break from loop
                }
            }
        } catch (SocketException se) {
            logger.error("Client disconnected => {}", se.getMessage());
        } catch (Exception ex) {
            Response errResp = error(0, "Unexpected error: " + ex.getMessage());
            errResp.writeDelimitedTo(out);
        } finally {
            logger.error("Client ID {} disconnected", id);
            inGame = false;
            exitAndClose(in, out, clientSocket);
        }
    }

    //--------------------------------------------------------------------------------
    // REQUEST HANDLERS
    //--------------------------------------------------------------------------------

    /**
     * Writes a log entry for a specific player action to the log file. The log entry includes
     * the timestamp, player's name, and the message specifying the action.
     *
     * @param playerName The name of the player performing the action.
     * @param message    An enum value of type {@code Message} that defines the type of action
     *                   performed by the player (e.g., CONNECT, START, WIN).
     */
    public void writeToLog(String playerName, Message message) {
        try {
            Logs.Builder logs = readLogFile();
            Date now = java.util.Calendar.getInstance().getTime();
            logs.addLog(now + ": " + playerName + " - " + message);

            try (FileOutputStream fos = new FileOutputStream(logFilename)) {
                logs.build().writeTo(fos);
            }
        } catch (Exception e) {
            logger.error("Issue while writing logs => {}", e.getMessage());
        }
    }

    /**
     * Reads the log file and builds a {@code Logs.Builder} object by merging data from the file.
     * If the log file is not found, a new {@code Logs.Builder} is returned, and a message is printed
     * indicating the absence of the file.
     *
     * @return A {@code Logs.Builder} object that contains the parsed data from the log file
     * or an empty builder if the file does not exist.
     * @throws Exception If an error occurs during the log file reading or merging process.
     */
    public Logs.Builder readLogFile() throws Exception {
        Logs.Builder logs = Logs.newBuilder();
        try {
            return logs.mergeFrom(new FileInputStream(logFilename));
        } catch (FileNotFoundException e) {
            logger.error("{} not found. Creating new file.", logFilename);
            return logs;
        }
    }

    /**
     * Logs the score of a player to the log file, including the player's name, score, and a timestamp.
     * The method reads the current log file, appends the new score entry, and writes it back to the
     * log file. Handles exceptions that may occur during the process and logs errors if any issues arise.
     *
     * @param player The name of the player whose score is being logged.
     * @param points The score value associated with the player's action.
     */
    public void writeScore(String player, int points) {
        try {
            Logs.Builder logs = readLogFile();
            Date now = java.util.Calendar.getInstance().getTime();
            // we’ll use a very easy‑to‑parse marker
            logs.addLog(now + ": " + player + " - SCORE=" + points);
            try (FileOutputStream fos = new FileOutputStream(logFilename)) {
                logs.build().writeTo(fos);
            }
        } catch (Exception e) {
            logger.error("Could not write score entry", e);
        }
    }

    /**
     * Handles the name input from the client. If the name is blank, an error response is returned.
     * Otherwise, the name is set, logged, and the state transitions to the main menu.
     * A greeting response is then constructed and returned to the client.
     *
     * @param op The {@code Request} object containing the operation details, including the user's name.
     * @return A {@code Response} object. If the name is blank, the response contains an error message
     * with a response type of {@code ResponseType.ERROR}. If the name is valid, the response
     * includes a greeting message, the next menu state, and menu options.
     * @throws IOException If an I/O error occurs during logging or response construction.
     */
    private Response handleName(Request op) throws IOException {
        if (op.getName().isBlank()) {
            return error(1, "Empty name");
        }
        name = op.getName();

        writeToLog(name, Message.CONNECT);
        currentState = 2; // go to main menu

        logger.info("Client ID {} connected with name: {}", id, name);

        return Response.newBuilder()
                .setResponseType(Response.ResponseType.GREETING)
                .setMessage("Hello " + name + " and welcome to a simple game of Sudoku.")
                .setMenuoptions(menuOptions)
                .setNext(currentState)
                .build();
    }

    /**
     * Handles the "START" request by validating the difficulty level and starting a new game session.
     * If the difficulty level is out of range, an error response is returned. Otherwise, a new game
     * is initiated, and a response confirming the start of the game is returned.
     *
     * @param op The {@code Request} object containing the operation details, including the difficulty level.
     * @return A {@code Response} object. If successful, the response includes a response type of
     * {@code ResponseType.START}, the game board state, and game options. If the difficulty
     * level is out of range, the response contains a response type of {@code ResponseType.ERROR}
     * with an error message and menu options for navigating back to the main menu.
     * @throws IOException If an I/O error occurs during the operation.
     */
    private Response handleStart(Request op) throws IOException {
        logger.info("START request received.");
        int difficulty = op.getDifficulty();
        if (difficulty < 1 || difficulty > 20) {
            // Difficulty out of range
            return Response.newBuilder()
                    .setResponseType(Response.ResponseType.ERROR)
                    .setErrorType(5) // from PROTOCOL.md
                    .setMessage("\nError: difficulty is out of range")
                    .setMenuoptions(menuOptions)
                    .setNext(2)  // main menu
                    .build();
        }

        // Start new game
        game.newGame(grading, difficulty);
        logger.debug(game.getDisplayBoard());
        inGame = true;
        currentState = 3; // in-game

        // Log the start of the game
        logger.info("Starting new game with difficulty {}", difficulty);

        return Response.newBuilder()
                .setResponseType(Response.ResponseType.START)
                .setBoard(game.getDisplayBoard())
                .setMessage("\nStarting new game.")
                .setMenuoptions(gameOptions)
                .setNext(currentState)
                .build();
    }

    /**
     * Processes and generates a leaderboard from the server's log data.
     * Aggregates logins and points for each user found in the log file,
     * and constructs a response containing the leaderboard information.
     * The response includes:
     * 1. The type set to {@code Response.ResponseType.LEADERBOARD}.
     * 2. Menu options for navigation.
     * 3. An ordered list of leaderboard entries with user names, points, and login counts.
     *
     * @return A {@code Response} object containing the aggregated leaderboard data
     * and navigation details for the client.
     * @throws Exception If an error occurs while reading or processing the log file.
     */
    // --- in SockBaseServer --------------------------------------------------
    private Response handleLeaderboard() throws Exception {
        Logs.Builder logs = readLogFile();

        /* Map<name, int[2]>  ->  [0]=logins , [1]=points */
        Map<String, int[]> agg = new LinkedHashMap<>();
        // int[0] = logins   int[1] = max points

        for (String line : logs.getLogList()) {
            int dash = line.indexOf(" - ");
            if (dash < 0) continue;
            String name = line.substring(line.lastIndexOf(':', dash) + 1, dash).trim();

            agg.computeIfAbsent(name, k -> new int[]{0, 0})[0]++;   // ++logins

            int idx = line.indexOf("SCORE=");
            if (idx >= 0) {
                int pts = Integer.parseInt(line.substring(idx + 6).trim());
                agg.get(name)[1] = Math.max(agg.get(name)[1], pts);   // keep best score
            }
        }

        logger.debug("Aggregated leaderboard: {}", agg);

        Response.Builder rb = Response.newBuilder()
                .setResponseType(Response.ResponseType.LEADERBOARD)
                .setMenuoptions(menuOptions)
                .setNext(2);

        for (Map.Entry<String, int[]> e : agg.entrySet()) {
            rb.addLeader(ResponseProtos.Entry.newBuilder()
                    .setName(e.getKey())
                    .setPoints(e.getValue()[1])
                    .setLogins(e.getValue()[0])
                    .build());
        }
        return rb.build();
    }

    /**
     * parseLogsToEntries - adapt to log format,
     * converting each line to a minimal Entry with name, points, logins if available.
     */
    private List<ResponseProtos.Entry> parseLogsToEntries(Logs.Builder logs) {
        // Example is naive: each line is "date: name - CONNECT"
        // or "date: name - some other message"
        // Return an empty list or parse more thoroughly if you store points/logins
        return logs.getLogList().stream()
                .map(logLine -> {
                    // Some naive parsing...
                    // e.g. "Wed Sep 27 ...: chris - CONNECT"
                    String nameStr = "Unknown";
                    // Could do regex or splitting
                    int dashIdx = logLine.indexOf('-');
                    if (dashIdx > 0 && dashIdx < logLine.length()) {
                        nameStr = logLine.substring(0, dashIdx).trim();
                        // Also remove date. This is purely an example; adapt as needed
                        int colonIdx = nameStr.indexOf(':');
                        if (colonIdx > 0 && colonIdx < nameStr.length()) {
                            // date + space => skip
                            nameStr = nameStr.substring(colonIdx + 1).trim();
                        }
                    }
                    // We have nameStr, but no points/logins in the logs. We'll return 0,0
                    return ResponseProtos.Entry.newBuilder()
                            .setName(nameStr)
                            .setPoints(0)
                            .setLogins(1)
                            .build();
                })
                .toList();
    }

    //--------------------------------------------------------------------------------
    // ERRORS & LOGGING
    //--------------------------------------------------------------------------------

    /**
     * handleUpdate => places a number in the Sudoku board. If invalid => -2 points
     * If solved => +20
     * Also sets the type field to the correct EvalType
     */
    private Response handleUpdate(Request op) throws IOException {
        // Check if the update operation has valid fields
        if (op.getRow() == 0 || op.getColumn() == 0 || op.getValue() == 0) {
            return error(2, "Invalid operation type");
        }

        int row = op.getRow();
        int col = op.getColumn();
        int val = op.getValue();

        // 1..9 => valid. Convert to 0-based
        if (row < 1 || row > 9 || col < 1 || col > 9) {
            return error(3, "Row/Col out of range");
        }
        int row0 = row - 1;
        int col0 = col - 1;

        // 0 => success, 1 => preset, 2 => dup row, 3 => dup col, 4 => dup grid, 5 => unknown
        int result = game.updateBoard(row0, col0, val, 0);

        // Evaluate
        Response.EvalType evalType = Response.EvalType.UPDATE;
        if (result == 1) {
            evalType = Response.EvalType.PRESET_VALUE;
            game.setPoints(-2);
        } else if (result == 2) {
            evalType = Response.EvalType.DUP_ROW;
            game.setPoints(-2);
        } else if (result == 3) {
            evalType = Response.EvalType.DUP_COL;
            game.setPoints(-2);
        } else if (result == 4) {
            evalType = Response.EvalType.DUP_GRID;
            game.setPoints(-2);
        } else if (result == 5) {
            // unknown => partial error
            return error(0, "Unknown update error");
        }
        // if result=0 => success => do nothing
        logger.debug("Update result: {} => {}", evalType, result);
        // check if won => +20
        boolean puzzleDone = game.getWon();
        if (puzzleDone) {
            game.setPoints(20);
            // write score
            writeScore(name, game.getPoints());
            return Response.newBuilder()
                    .setResponseType(Response.ResponseType.WON)
                    .setBoard(game.getDisplayBoard())
                    .setType(evalType) // keep the last update reason
                    .setMenuoptions(menuOptions)
                    .setMessage("You solved the current puzzle, good job!")
                    .setPoints(game.getPoints())
                    .setNext(2) // main menu
                    .build();
        }

        // keep playing
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.PLAY)
                .setBoard(game.getDisplayBoard())
                .setPoints(game.getPoints())
                .setType(evalType)
                .setMenuoptions(gameOptions)
                .setNext(3)
                .build();
    }

    /**
     * handleClear => row/col might be -1 if not used,
     * cval => type of clear. Use matching EvalType to reflect the operation.
     */
    private Response handleClear(Request op) throws IOException {
        int crow = op.getRow();
        int ccol = op.getColumn();
        int cval = op.getValue(); // 1..6 => which clear

        // If row or col != -1, check bounds 1..9
        if ((crow != -1 && (crow < 1 || crow > 9)) ||
                (ccol != -1 && (ccol < 1 || ccol > 9))) {
            return error(3, "Row/Col out of range");
        }

        // Convert to 0-based if not -1
        if (crow != -1) crow -= 1;
        if (ccol != -1) ccol -= 1;

        // Determine correct EvalType
        Response.EvalType evalType;
        switch (cval) {
            case 1:
                evalType = Response.EvalType.CLEAR_VALUE;
                break;
            case 2:
                evalType = Response.EvalType.CLEAR_ROW;
                break;
            case 3:
                evalType = Response.EvalType.CLEAR_COL;
                break;
            case 4:
                evalType = Response.EvalType.CLEAR_GRID;
                break;
            case 5:
                evalType = Response.EvalType.CLEAR_BOARD;
                break;
            case 6:
                evalType = Response.EvalType.RESET_BOARD;
                break;
            default:
                return error(2, "Invalid CLEAR code");
        }

        // -5 points for clearing
        game.setPoints(-5);
        //write score
        writeScore(name, game.getPoints());
        int res = game.updateBoard(crow, ccol, cval, cval);

        //log
        logger.debug("Clearing {} => {}", evalType, res);

        // just keep playing
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.PLAY)
                .setBoard(game.getDisplayBoard())
                .setPoints(game.getPoints())
                .setType(evalType)
                .setMenuoptions(gameOptions)
                .setNext(3)
                .build();
    }

    /**
     * Handles the quit operation for the current session by updating the game state to indicate
     * the player has exited and constructing an appropriate response message.
     *
     * @return A {@code Response} object with a response type of {@code ResponseType.BYE} and
     * a message indicating the session has ended.
     * @throws IOException If an I/O error occurs during the operation.
     */
    private Response handleQuit() throws IOException {
        inGame = false;
        logger.info("Client ID {} quitting.", id);
        //write score
        writeScore(name, game.getPoints());
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.BYE)
                .setMessage("Thank you for playing! goodbye.")
                .build();
    }

    /**
     * Constructs an error response based on the provided error type and associated information.
     * The generated response includes a specific error message and the error type,
     * guiding the client to the appropriate next action depending on the game state.
     *
     * @param err  The error type represented as an integer. It indicates the type of issue:
     *             1 = field missing, 2 = not supported, 3 = row/col out of range,
     *             5 = difficulty out of range. Any other value results in a generic error.
     * @param info Additional information or context related to the error, used for custom error messages.
     * @return A Response object with the error details, including the response type, error type,
     * message, and navigation options for the next action.
     * @throws IOException If an I/O operation fails during the creation of the response.
     */
    private Response error(int err, String info) throws IOException {
        int eType = err; // e.g. 1=field missing,2=not supported,3=row/col out of range,5=difficulty out of range
        String message;
        switch (err) {
            case 1:
                message = "\nError: required field missing or empty";
                break;
            case 2:
                message = "\nError: request not supported";
                break;
            case 3:
                message = "\nError: row or col out of bounds";
                break;
            case 5:
                message = "\nError: difficulty is out of range";
                break;
            default:
                // fallback
                eType = 0;
                message = "\nError: cannot process your request => " + info;
                break;
        }
        logger.debug("Error: {} => {}", err, message);

        return Response.newBuilder()
                .setResponseType(Response.ResponseType.ERROR)
                .setErrorType(eType)
                .setMessage(message)
                .setNext(currentState) // 1=name, 2=main menu, 3=in-game
                .build();
    }

    /**
     * Closes the provided InputStream, OutputStream, and Socket if they are not null.
     * Ensures resources are released properly to avoid resource leaks.
     *
     * @param in         the InputStream to be closed; can be null
     * @param out        the OutputStream to be closed; can be null
     * @param serverSock the Socket to be closed; can be null
     * @throws IOException if an I/O error occurs during the closing of any resource
     */
    void exitAndClose(InputStream in, OutputStream out, Socket serverSock) throws IOException {
        if (in != null) in.close();
        if (out != null) out.close();
        if (serverSock != null) serverSock.close();
        logger.info("Closed all resources for client ID {}", id);
    }

}
