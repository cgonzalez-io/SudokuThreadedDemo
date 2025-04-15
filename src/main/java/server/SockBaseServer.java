package server;

import buffers.RequestProtos.Logs;
import buffers.RequestProtos.Message;
import buffers.RequestProtos.Request;
import buffers.ResponseProtos.Response;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Date;
import java.util.List;

class SockBaseServer {
    static String logFilename = "logs.txt";

    // Please use these as given so it works with our test cases
    static String menuOptions = "\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game";
    static String gameOptions = "\nChoose an action: \n (1-9) - Enter an int to specify the row you want to update \n c - Clear number \n r - New Board";
    private static boolean grading = true; // if the grading board should be used
    private final int id; // client id
    ServerSocket serv = null;
    InputStream in = null;
    OutputStream out = null;
    Socket clientSocket = null;
    Game game; // current game
    private boolean inGame = false; // a game was started (you can decide if you want this
    private String name; // player name
    private int currentState = 1; // I used something like this to keep track of where I am in the game, you can decide if you want that as well

    public SockBaseServer(Socket sock, Game game, int id) {
        this.clientSocket = sock;
        this.game = game;
        this.id = id;
        try {
            in = clientSocket.getInputStream();
            out = clientSocket.getOutputStream();
        } catch (Exception e) {
            System.out.println("Error in constructor: " + e);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Expected arguments: <port(int)> <gradingMode(true|false)>");
            System.exit(1);
        }
        int port = 8000;
        try {
            grading = Boolean.parseBoolean(args[1]);
        } catch (Exception e) {
            System.out.println("Grading mode must be 'true' or 'false'");
            System.exit(2);
        }
        Socket clientSocket = null;
        ServerSocket socket = null;

        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException nfe) {
            System.out.println("[Port must be an integer]");
            System.exit(2);
        }
        try {
            socket = new ServerSocket(port);
            System.out.println("Server started..");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
        int id = 1;
        while (true) {
            try {
                clientSocket = socket.accept();
                System.out.println("Attempting to connect to client-" + id);
                Game game = new Game();
                SockBaseServer server = new SockBaseServer(clientSocket, game, id++);
                server.startGame();
            } catch (Exception e) {
                System.out.println("Error in accepting client connection.");
            }
        }
    }

    /**
     * Received a request, starts to evaluate what it is and handles it, not complete
     */
    public void startGame() throws IOException {
        try {
            while (true) {
                // read the proto object and put into new object
                Request op = Request.parseDelimitedFrom(in);
                System.out.println("Got request: " + op.toString());
                Response response;

                boolean quit = false;

                // should handle all the other request types here, my advice is to put them in methods similar to nameRequest()
                switch (op.getOperationType()) {
                    case NAME:
                        if (op.getName().isBlank()) {
                            response = error(1, "name");
                        } else {
                            response = nameRequest(op);
                        }
                        break;
                    case START:
                        response = startGame(op);
                        break;
                    case LEADERBOARD:
                        Logs.Builder logs = readLogFile();
                        List<String> leaderboardEntries = logs.getLogList();
                        Response.Builder lbResponse = Response.newBuilder()
                                .setResponseType(Response.ResponseType.LEADERBOARD)
                                .setMenuoptions(menuOptions)
                                .setNext(2);
                        for (String entry : leaderboardEntries) {
                            lbResponse.addLeader(entry);
                        }
                        response = lbResponse.build();
                        break;
                    case CLEAR:
                        int crow = op.getRow();
                        int ccol = op.getColumn();
                        int cval = op.getValue();
                        int clearResult = game.updateBoard(crow, ccol, cval, cval);

                        response = Response.newBuilder()
                                .setResponseType(Response.ResponseType.PLAY)
                                .setBoard(game.getDisplayBoard())
                                .setPoints(game.getPoints())
                                .setType(Response.ResponseType.CLEAR)
                                .setMenuoptions(gameOptions)
                                .setNext(3)
                                .build();
                        break;
                    case UPDATE:
                        int row = op.getRow();
                        int col = op.getColumn();
                        int val = op.getValue();
                        int result = game.updateBoard(row, col, val, 0);

                        if (game.getWon()) {
                            response = Response.newBuilder()
                                    .setResponseType(Response.ResponseType.WON)
                                    .setBoard(game.getDisplayBoard())
                                    .setType(Response.ResponseType.UPDATE)
                                    .setMenuoptions(menuOptions)
                                    .setMessage("You solved the current puzzle, good job!")
                                    .setPoints(game.getPoints())
                                    .setNext(2)
                                    .build();
                        } else {
                            response = Response.newBuilder()
                                    .setResponseType(Response.ResponseType.PLAY)
                                    .setBoard(game.getDisplayBoard())
                                    .setPoints(game.getPoints())
                                    .setType(Response.ResponseType.UPDATE)
                                    .setMenuoptions(gameOptions)
                                    .setNext(3)
                                    .build();
                        }
                        break;
                    case QUIT:
                        response = quit();
                        quit = true;
                        break;
                    default:
                        response = error(2, op.getOperationType().name());
                        break;
                }
                response.writeDelimitedTo(out);

                if (quit) {
                    return;
                }
            }
        } catch (SocketException se) {
            System.out.println("Client disconnected");
        } catch (Exception ex) {
            Response error = error(0, "Unexpected server error: " + ex.getMessage());
            error.writeDelimitedTo(out);
        } finally {
            System.out.println("Client ID " + id + " disconnected");
            this.inGame = false;
            exitAndClose(in, out, clientSocket);
        }
    }

    public void writeToLog(String name, Message message) {
        try {
            Logs.Builder logs = readLogFile();

            Date date = java.util.Calendar.getInstance().getTime();
            logs.addLog(date + ": " + name + " - " + message);

            FileOutputStream output = new FileOutputStream(logFilename);
            Logs logsObj = logs.build();
            logsObj.writeTo(output);
        } catch (Exception e) {
            System.out.println("Issue while trying to save");
        }
    }

    public Logs.Builder readLogFile() throws Exception {
        Logs.Builder logs = Logs.newBuilder();

        try {
            return logs.mergeFrom(new FileInputStream(logFilename));
        } catch (FileNotFoundException e) {
            System.out.println(logFilename + ": File not found.  Creating a new file.");
            return logs;
        }
    }

    private Response nameRequest(Request op) throws IOException {
        name = op.getName();

        writeToLog(name, Message.CONNECT);
        currentState = 2;

        System.out.println("Got a connection and a name: " + name);
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.GREETING)
                .setMessage("Hello " + name + " and welcome to a simple game of Sudoku.")
                .setMenuoptions(menuOptions)
                .setNext(currentState)
                .build();
    }

    private Response startGame(Request op) throws IOException {
        System.out.println("Start game request received");

        int difficulty = op.getDifficulty();
        if (difficulty < 1 || difficulty > 20) {
            return Response.newBuilder()
                    .setResponseType(Response.ResponseType.ERROR)
                    .setErrorType(5)
                    .setMessage("Error: difficulty is out of range")
                    .setMenuoptions(menuOptions)
                    .setNext(2)
                    .build();
        }

        game.newGame(grading, difficulty);
        System.out.println(game.getDisplayBoard());

        currentState = 3;
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.START)
                .setBoard(game.getDisplayBoard())
                .setMessage("\n")
                .setMenuoptions(gameOptions)
                .setNext(currentState)
                .build();
    }

    private Response quit() throws IOException {
        this.inGame = false;
        return Response.newBuilder()
                .setResponseType(Response.ResponseType.BYE)
                .setMessage("Thank you for playing! goodbye.")
                .build();
    }

    private Response error(int err, String field) throws IOException {
        String message = "";
        int type = err;
        Response.Builder response = Response.newBuilder();

        switch (err) {
            case 1:
                message = "\nError: required field missing or empty";
                break;
            case 2:
                message = "\nError: request not supported";
                break;
            default:
                message = "\nError: cannot process your request";
                type = 0;
                break;
        }

        response
                .setResponseType(Response.ResponseType.ERROR)
                .setErrorType(type)
                .setMessage(message)
                .setNext(currentState)
                .build();

        return response.build();
    }

    void exitAndClose(InputStream in, OutputStream out, Socket serverSock) throws IOException {
        if (in != null) in.close();
        if (out != null) out.close();
        if (serverSock != null) serverSock.close();
    }
}
