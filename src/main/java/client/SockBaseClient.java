package client;

import buffers.RequestProtos.Request;
import buffers.ResponseProtos.Response;

import java.io.*;
import java.net.Socket;

/**
 * This class represents a basic socket-based client that communicates with a server using a predefined protocol.
 * The client sends requests and receives responses from the server, allowing interaction through user input.
 * It supports basic operations such as initiating connections, handling server responses, and performing specific actions
 * like name requests, menu selections, and board manipulation.
 * The main method establishes a connection to a server with a specified host and port.
 * It manages input/output streams and continuously handles responses from the server.
 * Based on the server response type, the client builds appropriate subsequent requests and sends them back.
 */
class SockBaseClient {
    public static void main(String[] args) throws Exception {
        Socket serverSock = null;
        OutputStream out = null;
        InputStream in = null;
        int i1 = 0, i2 = 0;
        int port = 8000; // default port

        // Make sure two arguments are given
        if (args.length != 2) {
            System.out.println("Expected arguments: <host(String)> <port(int)>");
            System.exit(1);
        }
        String host = args[0];
        try {
            port = Integer.parseInt(args[1]);
        } catch (NumberFormatException nfe) {
            System.out.println("[Port] must be integer");
            System.exit(2);
        }

        // Build the first request object just including the name
        Request op = nameRequest().build();
        Response response;
        try {
            // connect to the server
            serverSock = new Socket(host, port);

            // write to the server
            out = serverSock.getOutputStream();
            in = serverSock.getInputStream();

            op.writeDelimitedTo(out);

            while (true) {
                // read from the server
                response = Response.parseDelimitedFrom(in);
                System.out.println("Got a response: " + response.toString());

                Request.Builder req = Request.newBuilder();

                switch (response.getResponseType()) {
                    case GREETING:
                        System.out.println(response.getMessage());
                        req = chooseMenu(req, response);
                        break;
                    case ERROR:
                        System.out.println("Error: " + response.getMessage() + "Type: " + response.getErrorType());
                        if (response.getNext() == 1) {
                            req = nameRequest();
                        } else {
                            System.out.println("That error type is not handled yet");
                            req = nameRequest();
                        }
                        break;
                }
                req.build().writeDelimitedTo(out);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            exitAndClose(in, out, serverSock);
        }
    }

    /**
     * handles building a simple name requests, asks the user for their name and builds the request
     *
     * @return Request.Builder which holds all teh information for the NAME request
     */
    static Request.Builder nameRequest() throws IOException {
        System.out.println("Please provide your name for the server.");
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
        String strToSend = stdin.readLine();

        return Request.newBuilder()
                .setOperationType(Request.OperationType.NAME)
                .setName(strToSend);
    }

    /**
     * Shows the main menu and lets the user choose a number, it builds the request for the next server call
     *
     * @return Request.Builder which holds the information the server needs for a specific request
     */
    static Request.Builder chooseMenu(Request.Builder req, Response response) throws IOException {
        while (true) {
            System.out.println(response.getMenuoptions());
            System.out.print("Enter a number 1-3: ");
            BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
            String menu_select = stdin.readLine();
            System.out.println(menu_select);
            switch (menu_select) {
                // needs to include the other requests
                case "2":
                    req.setOperationType(Request.OperationType.START); // this is not a complete START request!! Just as example
                    return req;
                default:
                    System.out.println("\nNot a valid choice, please choose again");
                    break;
            }
        }
    }

    /**
     * Exits the connection
     */
    static void exitAndClose(InputStream in, OutputStream out, Socket serverSock) throws IOException {
        if (in != null) in.close();
        if (out != null) out.close();
        if (serverSock != null) serverSock.close();
        System.exit(0);
    }

    /**
     * Handles the clear menu logic when the user chooses that in Game menu. It retuns the values exactly
     * as needed in the CLEAR request row int[0], column int[1], value int[3]
     */
    static int[] boardSelectionClear() throws Exception {
        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Choose what kind of clear by entering an integer (1 - 5)");
        System.out.print(" 1 - Clear value \n 2 - Clear row \n 3 - Clear column \n 4 - Clear Grid \n 5 - Clear Board \n");

        String selection = stdin.readLine();

        while (true) {
            if (selection.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                int temp = Integer.parseInt(selection);

                if (temp < 1 || temp > 5) {
                    throw new NumberFormatException();
                }

                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.println("Choose what kind of clear by entering an integer (1 - 5)");
                System.out.print("1 - Clear value \n 2 - Clear row \n 3 - Clear column \n 4 - Clear Grid \n 5 - Clear Board \n");
            }
            selection = stdin.readLine();
        }

        int[] coordinates = new int[3];

        switch (selection) {
            case "1":
                // clear value, so array will have {row, col, 1}
                coordinates = boardSelectionClearValue();
                break;
            case "2":
                // clear row, so array will have {row, -1, 2}
                coordinates = boardSelectionClearRow();
                break;
            case "3":
                // clear col, so array will have {-1, col, 3}
                coordinates = boardSelectionClearCol();
                break;
            case "4":
                // clear grid, so array will have {gridNum, -1, 4}
                coordinates = boardSelectionClearGrid();
                break;
            case "5":
                // clear entire board, so array will have {-1, -1, 5}
                coordinates[0] = -1;
                coordinates[1] = -1;
                coordinates[2] = 5;
                break;
            default:
                break;
        }

        return coordinates;
    }

    /**
     * Prompts the user to select the row and column of a value they wish to clear
     * from a board. The method captures user input for the row and column,
     * validates it as integers within the range of 1 to 9, and checks for an "exit"
     * command. If the user enters "exit", it returns an array with marker values
     * indicating termination.
     * @return An integer array of size 3, where:
     *         - index 0 represents the selected row (1-9, or Integer.MIN_VALUE for exit),
     *         - index 1 represents the selected column (1-9, or Integer.MIN_VALUE for exit),
     *         - index 2 is set to 1, indicating a value-clear action.
     * @throws Exception if an I/O error occurs while reading user input.
     */
    static int[] boardSelectionClearValue() throws Exception {
        int[] coordinates = new int[3];

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Choose coordinates of the value you want to clear");
        System.out.print("Enter the row as an integer (1 - 9): ");
        String row = stdin.readLine();

        while (true) {
            if (row.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                Integer.parseInt(row);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.print("Enter the row as an integer (1 - 9): ");
            }
            row = stdin.readLine();
        }

        coordinates[0] = Integer.parseInt(row);

        System.out.print("Enter the column as an integer (1 - 9): ");
        String col = stdin.readLine();

        while (true) {
            if (col.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                Integer.parseInt(col);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.print("Enter the column as an integer (1 - 9): ");
            }
            col = stdin.readLine();
        }

        coordinates[1] = Integer.parseInt(col);
        coordinates[2] = 1;

        return coordinates;
    }

    /**
     * Handles logic for selecting and clearing an entire row in a game board.
     * Prompts the user to input an integer representing the row to clear (from 1 to 9),
     * or to type "exit" to terminate the selection process.
     * @return An integer array of size 3, where:
     *         - The first element is the row number to clear.
     *         - The second element is set to -1 as a placeholder.
     *         - The third element is a constant value of 2 to denote a row clear request.
     *         If the user inputs "exit*/
    static int[] boardSelectionClearRow() throws Exception {
        int[] coordinates = new int[3];

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Choose the row you want to clear");
        System.out.print("Enter the row as an integer (1 - 9): ");
        String row = stdin.readLine();

        while (true) {
            if (row.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                Integer.parseInt(row);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.print("Enter the row as an integer (1 - 9): ");
            }
            row = stdin.readLine();
        }

        coordinates[0] = Integer.parseInt(row);
        coordinates[1] = -1;
        coordinates[2] = 2;

        return coordinates;
    }

    /**
     * Handles the logic for clearing a specific column in the game board.
     * Prompts the user to input a column number between 1 and 9 and validates the input.
     * If user inputs 'exit', an array with {@code Integer.MIN_VALUE} for all elements is returned.
     * @return An integer array of size 3 where:
     *         {@code array[0] = -1}, {@code array[1]} contains the selected column number,
     *         and {@code array[2] = 3} representing the column clear action.
     * @throws Exception If an error occurs*/
    static int[] boardSelectionClearCol() throws Exception {
        int[] coordinates = new int[3];

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Choose the column you want to clear");
        System.out.print("Enter the column as an integer (1 - 9): ");
        String col = stdin.readLine();

        while (true) {
            if (col.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                Integer.parseInt(col);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.print("Enter the column as an integer (1 - 9): ");
            }
            col = stdin.readLine();
        }

        coordinates[0] = -1;
        coordinates[1] = Integer.parseInt(col);
        coordinates[2] = 3;
        return coordinates;
    }

    /**
     * Prompts the user to select a grid area to clear and returns the corresponding
     * grid selection as an integer array. The method asks the user to input a number
     * between 1 and 9 to indicate the area of the grid to be cleared.
     * If the user inputs "exit", the method returns an array with Integer.MIN_VALUE
     * as all elements, signaling an exit operation.
     * @return an integer array where:
     *         - index 0 contains*/
    static int[] boardSelectionClearGrid() throws Exception {
        int[] coordinates = new int[3];

        BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));

        System.out.println("Choose area of the grid you want to clear");
        System.out.println(" 1 2 3 \n 4 5 6 \n 7 8 9 \n");
        System.out.print("Enter the grid as an integer (1 - 9): ");
        String grid = stdin.readLine();

        while (true) {
            if (grid.equalsIgnoreCase("exit")) {
                return new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
            }
            try {
                Integer.parseInt(grid);
                break;
            } catch (NumberFormatException nfe) {
                System.out.println("That's not an integer!");
                System.out.print("Enter the grid as an integer (1 - 9): ");
            }
            grid = stdin.readLine();
        }

        coordinates[0] = Integer.parseInt(grid);
        coordinates[1] = -1;
        coordinates[2] = 4;

        return coordinates;
    }
}
