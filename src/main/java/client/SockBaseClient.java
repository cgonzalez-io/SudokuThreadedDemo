package client;

import buffers.RequestProtos.Request;
import buffers.ResponseProtos.Response;

import java.io.*;
import java.net.Socket;

public class SockBaseClient {
    private static final BufferedReader console = new BufferedReader(new InputStreamReader(System.in));

    /**
     * The main method serves as the entry point for the SockBaseClient application.
     * It establishes a connection to the server using the provided host and port,
     * and facilitates interaction between the client and the server. The method
     * handles different types of responses from the server, including greeting,
     * leaderboard, start game, error, and termination messages.
     *
     * @param args Command-line arguments where the first argument is the server host
     *             and the second is the server port. The method expects exactly two
     *             arguments. If the arguments are not provided or are invalid, the
     *             program provides usage instructions and terminates.
     * @throws Exception If any IO or parsing errors occur during the execution.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: java client.SockBaseClient <host> <port>");
            System.exit(1);
        }
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        try (Socket sock = new Socket(host, port);
             InputStream in = sock.getInputStream();
             OutputStream out = sock.getOutputStream()) {
            // 1) send NAME
            Request nameReq = nameRequest().build();
            nameReq.writeDelimitedTo(out);

            // 2) main loop
            while (true) {
                Response resp = Response.parseDelimitedFrom(in);
                if (resp == null) break;  // server gone

                switch (resp.getResponseType()) {
                    case GREETING:
                        handleGreeting(resp, out);
                        break;

                    case LEADERBOARD:
                        System.out.println("=== Leaderboard ===");
                        resp.getLeaderList().forEach(e ->
                                System.out.printf("%s : %d points, %d logins%n", e.getName(), e.getPoints(), e.getLogins())
                        );
                        System.out.println();
                        handleGreeting(resp, out);
                        break;

                    case START:
                        System.out.println(resp.getMessage());
                        handleInGame(resp, in, out);
                        break;

                    case ERROR:
                        System.err.println("Server error: " + resp.getMessage().trim());
                        if (resp.getNext() == 2) {
                            handleGreeting(resp, out);
                        } else if (resp.getNext() == 1) {
                            Request nr = nameRequest().build();
                            nr.writeDelimitedTo(out);
                        }
                        break;

                    case BYE:
                        System.out.println(resp.getMessage());
                        return;

                    default:
                        System.err.println("Unexpected response: " + resp);
                        return;
                }
            }
        }
    }

    /**
     * Build a NAME request by asking the user.
     */
    static Request.Builder nameRequest() throws IOException {
        System.out.print("Enter your name: ");
        System.out.flush();
        String name = console.readLine().trim();
        return Request.newBuilder()
                .setOperationType(Request.OperationType.NAME)
                .setName(name);
    }

    /**
     * After GREETING or ERROR(next=2), prompt 1=leaderboard,2=play,3=quit
     */
    static void handleGreeting(Response resp, OutputStream out) throws IOException {
        System.out.println(resp.getMessage());
        System.out.println(resp.getMenuoptions());
        while (true) {
            System.out.print("Choose (1-3): ");
            System.out.flush();
            String choice = console.readLine().trim();
            Request.Builder req = Request.newBuilder();
            switch (choice) {
                case "1":
                    req.setOperationType(Request.OperationType.LEADERBOARD);
                    req.build().writeDelimitedTo(out);
                    return;
                case "2":
                    System.out.print("Choose difficulty (1–20): ");
                    System.out.flush();
                    int d = Integer.parseInt(console.readLine().trim());
                    req.setOperationType(Request.OperationType.START)
                            .setDifficulty(d)
                            .build()
                            .writeDelimitedTo(out);
                    return;
                case "3":
                    req.setOperationType(Request.OperationType.QUIT)
                            .build()
                            .writeDelimitedTo(out);
                    return;
                default:
                    System.out.println("Invalid choice, try again.");
            }
        }
    }

    /**
     * In‑game loop.  We treat the very first `resp` (START) exactly like a PLAY,
     * then parse further PLAY / WON / ERROR messages from the server in the loop.
     */
    static void handleInGame(Response firstResp,
                             InputStream in,
                             OutputStream out) throws IOException {
        Response resp = firstResp;

        while (true) {
            // For PLAY and also the initial START, print:
            if (resp.getResponseType() == Response.ResponseType.START ||
                    resp.getResponseType() == Response.ResponseType.PLAY) {

                // server‐sent text + board + your points
                System.out.print(resp.getMessage());
                System.out.print(resp.getBoard());
                System.out.printf("Points: %d%n", resp.getPoints());
                System.out.println(resp.getMenuoptions());
                System.out.print("> ");
                System.out.flush();

                // read your move
                String inp = console.readLine().trim();
                Request.Builder next = Request.newBuilder();

                if (inp.equalsIgnoreCase("exit")) {
                    next.setOperationType(Request.OperationType.QUIT);

                } else if (inp.equalsIgnoreCase("r")) {
                    next.setOperationType(Request.OperationType.CLEAR)
                            .setRow(-1).setColumn(-1).setValue(6);

                } else if (inp.equalsIgnoreCase("c")) {
                    int[] coords = boardSelectionClear();
                    next.setOperationType(Request.OperationType.CLEAR)
                            .setRow(coords[0])
                            .setColumn(coords[1])
                            .setValue(coords[2]);

                } else {
                    // numeric row → ask col + val
                    int row = Integer.parseInt(inp);
                    System.out.print("  col (1–9)? ");
                    System.out.flush();
                    int col = Integer.parseInt(console.readLine().trim());

                    System.out.print("  val (1–9)? ");
                    System.out.flush();
                    int val = Integer.parseInt(console.readLine().trim());

                    next.setOperationType(Request.OperationType.UPDATE)
                            .setRow(row)
                            .setColumn(col)
                            .setValue(val);
                }

                // send it and await the next server response
                next.build().writeDelimitedTo(out);
            } else if (resp.getResponseType() == Response.ResponseType.WON) {
                System.out.println(resp.getBoard());
                System.out.println(resp.getMessage());
                System.out.printf("Points this game: %d%n", resp.getPoints());
                return;  // back to main menu

            } else if (resp.getResponseType() == Response.ResponseType.ERROR) {
                System.err.println("Error: " + resp.getMessage().trim());
                // server in‐game ERROR includes its MENU_GAME text
                System.out.print(resp.getMenuoptions());
            } else {
                System.err.println("Unexpected in‑game response: " + resp);
                return;
            }

            // grab the next server packet
            resp = Response.parseDelimitedFrom(in);
            if (resp == null) return;  // server died
        }
    }

    /**
     * clear submenu: pick which type of clear (value, row, column, grid, board)
     */
    private static int[] boardSelectionClear() throws IOException {
        System.out.println("Clear options:");
        System.out.println(" 1) Clear single value");
        System.out.println(" 2) Clear row");
        System.out.println(" 3) Clear column");
        System.out.println(" 4) Clear 3×3 grid");
        System.out.println(" 5) Clear entire board");
        System.out.print("> ");
        System.out.flush();
        int sel = Integer.parseInt(console.readLine().trim());
        switch (sel) {
            case 1:
                System.out.print(" Row (1-9): ");
                System.out.flush();
                int r = Integer.parseInt(console.readLine().trim());
                System.out.print(" Col (1-9): ");
                System.out.flush();
                int c = Integer.parseInt(console.readLine().trim());
                return new int[]{r, c, 1};
            case 2:
                System.out.print(" Row (1-9): ");
                System.out.flush();
                return new int[]{Integer.parseInt(console.readLine().trim()), -1, 2};
            case 3:
                System.out.print(" Col (1-9): ");
                System.out.flush();
                return new int[]{-1, Integer.parseInt(console.readLine().trim()), 3};
            case 4:
                System.out.print(" Grid# (1-9): ");
                System.out.flush();
                return new int[]{Integer.parseInt(console.readLine().trim()), -1, 4};
            default:
                return new int[]{-1, -1, 5};
        }
    }
}
