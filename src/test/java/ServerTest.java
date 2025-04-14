import buffers.RequestProtos;
import buffers.ResponseProtos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.Socket;

public class ServerTest {

    Socket sock;
    OutputStream out;
    ObjectOutputStream os;
    InputStream in;
    static String menuOptions = "\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game";
    static String gameOptions = "\nChoose an action: \n (1-9) - Enter an int to specify the row you want to update \n c - Clear number \n r - New Board";

    //////////// IMPORTANT ////////////////
    //    Test only works with Grading task for the server
    //    Leaderboard will only be correct if that person was not on there yet

    // Establishing a connection to the server, make sure you start the server on localhost and 8888
    @BeforeEach
    public void setUp() throws Exception {
        System.out.println("check");

        sock = new Socket("localhost", 8000);

        // write to the server
        out = sock.getOutputStream();
        in = sock.getInputStream();

    }

    @AfterEach
    public void close() throws Exception {
        if (out != null)  out.close();
        if (sock != null) sock.close();
    }

    private void startGameRequest() throws IOException {
        nameReq();

        RequestProtos.Request op2 = RequestProtos.Request.newBuilder()
                .setOperationType(RequestProtos.Request.OperationType.START)
                .setDifficulty(3)
                .build();
        op2.writeDelimitedTo(out);
        ResponseProtos.Response response = ResponseProtos.Response.parseDelimitedFrom(in);
    }

    private void nameReq() throws IOException {
        String name = "Mehlhase";
        // Build the first request object just including the name
        RequestProtos.Request op = RequestProtos.Request.newBuilder()
                .setOperationType(RequestProtos.Request.OperationType.NAME)
                .setName(name)
                .build();
        op.writeDelimitedTo(out);
        ResponseProtos.Response response = ResponseProtos.Response.parseDelimitedFrom(in);
    }

    @Test
    @DisplayName("Testing not yet handled request")
    public void unsupportedRequest() throws IOException {
        String name = "Mehlhase";
        // Build the first request object just including the name
        RequestProtos.Request op = RequestProtos.Request.newBuilder()
                .setOperationType(RequestProtos.Request.OperationType.UPDATE)
                .build();
        op.writeDelimitedTo(out);

        ResponseProtos.Response response = ResponseProtos.Response.parseDelimitedFrom(in);

        assertEquals(ResponseProtos.Response.ResponseType.ERROR, response.getResponseType());
        assertEquals(2, response.getErrorType());
        assertEquals(1, response.getNext());
 }
    @Test
    @DisplayName("Testing the name request")
    public void nameRequest() throws IOException {
        String name = "Mehlhase";
        // Build the first request object just including the name
        RequestProtos.Request op = RequestProtos.Request.newBuilder()
                .setOperationType(RequestProtos.Request.OperationType.NAME)
                .setName(name)
                .build();
        op.writeDelimitedTo(out);

        ResponseProtos.Response response = ResponseProtos.Response.parseDelimitedFrom(in);

        assertEquals(ResponseProtos.Response.ResponseType.GREETING, response.getResponseType());
        assertEquals("Hello " + name + " and welcome to a simple game of Sudoku.", response.getMessage());
        assertEquals("\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game", response.getMenuoptions());
    }

    @Test
    @DisplayName("Testing the name request with empty string")
    public void emptyName() throws IOException {
        String name = "";
        // Build the first request object just including the name
        RequestProtos.Request op = RequestProtos.Request.newBuilder()
                .setOperationType(RequestProtos.Request.OperationType.NAME)
                .setName(name)
                .build();
        op.writeDelimitedTo(out);

        ResponseProtos.Response response = ResponseProtos.Response.parseDelimitedFrom(in);

        assertEquals(ResponseProtos.Response.ResponseType.ERROR, response.getResponseType());
        assertEquals("\nError: required field missing or empty", response.getMessage());
        assertEquals(1, response.getErrorType());

        // Build the first request object just including the name
        RequestProtos.Request op2 = RequestProtos.Request.newBuilder()
                .setOperationType(RequestProtos.Request.OperationType.NAME)
                .build();
        op.writeDelimitedTo(out);

        ResponseProtos.Response response2 = ResponseProtos.Response.parseDelimitedFrom(in);

        assertEquals(ResponseProtos.Response.ResponseType.ERROR, response2.getResponseType());
        assertEquals("\nError: required field missing or empty", response2.getMessage());
        assertEquals(1, response2.getErrorType());
    }

//    @Test
//    @DisplayName("Testing game flow to Leaderboard")
//    // this test only passes when that person has not logged in yet!! Could be done better but here we are
//    public void leaderRequest() throws IOException {
//        String name = "Mehlhase3";
//        // Build the first request object just including the name
//        RequestProtos.Request op = RequestProtos.Request.newBuilder()
//                .setOperationType(RequestProtos.Request.OperationType.NAME)
//                .setName(name)
//                .build();
//        op.writeDelimitedTo(out);
//
//        ResponseProtos.Response response = ResponseProtos.Response.parseDelimitedFrom(in);
//
//        assertEquals(ResponseProtos.Response.ResponseType.GREETING, response.getResponseType());
//        assertEquals("Hello " + name + " and welcome to a simple game of Sudoku.", response.getMessage());
//        assertEquals("\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game", response.getMenuoptions());
//
//        RequestProtos.Request req = RequestProtos.Request.newBuilder()
//                .setOperationType(RequestProtos.Request.OperationType.LEADERBOARD).build();
//        req.writeDelimitedTo(out);
//
//        ResponseProtos.Response leader = ResponseProtos.Response.parseDelimitedFrom(in);
//
//        assertEquals(ResponseProtos.Response.ResponseType.LEADERBOARD, leader.getResponseType());
//        assertEquals("\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game", leader.getMenuoptions());
//
//        ResponseProtos.Entry entry = ResponseProtos.Entry.newBuilder().setName(name).setLogins(1).setPoints(0).build();
//        assertTrue(leader.getLeaderList().contains(entry), "List does not contain name");
//    }

//    @Test
//    @DisplayName("Testing the START request with wrong difficulty")
//    public void startGameError() throws IOException {
//        nameReq();
//
////        // error difficulty
//        RequestProtos.Request op = RequestProtos.Request.newBuilder()
//                .setOperationType(RequestProtos.Request.OperationType.START)
//                .setDifficulty(-3)
//                .build();
//        op.writeDelimitedTo(out);
//
//        ResponseProtos.Response response = ResponseProtos.Response.parseDelimitedFrom(in);
//
//        assertEquals(ResponseProtos.Response.ResponseType.ERROR, response.getResponseType());
//        assertEquals("\nError: difficulty is out of range", response.getMessage());
//        assertEquals("\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game", response.getMenuoptions());
//        assertEquals(2, response.getNext());
//        assertEquals(5, response.getErrorType());
//
//        // error difficulty
//        RequestProtos.Request op2 = RequestProtos.Request.newBuilder()
//                .setOperationType(RequestProtos.Request.OperationType.START)
//                .setDifficulty(22)
//                .build();
//        op2.writeDelimitedTo(out);
//
//        ResponseProtos.Response response2 = ResponseProtos.Response.parseDelimitedFrom(in);
//
//        assertEquals(ResponseProtos.Response.ResponseType.ERROR, response2.getResponseType());
//        assertEquals("\nError: difficulty is out of range", response2.getMessage());
//        assertEquals("\nWhat would you like to do? \n 1 - to see the leader board \n 2 - to enter a game \n 3 - quit the game", response2.getMenuoptions());
//        assertEquals(2, response2.getNext());
//        assertEquals(5, response2.getErrorType());
//    }
//
//    @Test
//    @DisplayName("Testing the START request correct values")
//    public void startGame() throws IOException {
//        nameReq();
//        RequestProtos.Request op = RequestProtos.Request.newBuilder()
//                .setOperationType(RequestProtos.Request.OperationType.START)
//                .setDifficulty(3)
//                .build();
//        op.writeDelimitedTo(out);
//
//        ResponseProtos.Response response = ResponseProtos.Response.parseDelimitedFrom(in);
//
//        assertEquals(ResponseProtos.Response.ResponseType.START, response.getResponseType());
//        assertEquals("\nStarting new game.", response.getMessage());
//        assertEquals("\nChoose an action: \n (1-9) - Enter an int to specify the row you want to update \n c - Clear number \n r - New Board", response.getMenuoptions());
//        assertEquals(3, response.getNext());
//
//        String[] inputData = {
//                "5631X94X2",
//                "17948X563",
//                "482563179",
//                "631794825",
//                "794825631",
//                "825631794",
//                "317948256",
//                "X48X56317",
//                "2X63X7948"
//        };
//
//        String result = String.join("", inputData);
//        String playerBoard = response.getBoard().replaceAll("[\\s\\n]", "");
//        assertEquals(result, playerBoard);
//    }

//    @Test
//    @DisplayName("Testing the START request making guesses")
//    public void startGameplay() throws IOException {
//        startGameRequest();
//        // start playing good guess
//        RequestProtos.Request op = RequestProtos.Request.newBuilder()
//                .setOperationType(RequestProtos.Request.OperationType.UPDATE)
//                .setRow(1)
//                .setColumn(8)
//                .setValue(8)
//                .build();
//        op.writeDelimitedTo(out);
//
//        ResponseProtos.Response response = ResponseProtos.Response.parseDelimitedFrom(in);
//
//        String[] inputData = {
//                "5631X9482",
//                "17948X563",
//                "482563179",
//                "631794825",
//                "794825631",
//                "825631794",
//                "317948256",
//                "X48X56317",
//                "2X63X7948"
//        };
//
//        String result = String.join("", inputData);
//        String playerBoard = response.getBoard().replaceAll("[\\s\\n]", "");
//        assertEquals(result, playerBoard);
//        assertEquals(3, response.getNext());
//        assertEquals(0, response.getPoints());
//        assertEquals(gameOptions, response.getMenuoptions());
//        assertEquals(ResponseProtos.Response.EvalType.UPDATE, response.getType());
//
//        assertEquals(ResponseProtos.Response.ResponseType.PLAY, response.getResponseType());
//    }

//    @Test
//    @DisplayName("Testing Update guess previously set")
//    public void startGameplayPrevSet() throws IOException {
//        startGameRequest();
//        // start playing good guess
//        RequestProtos.Request op = RequestProtos.Request.newBuilder()
//                .setOperationType(RequestProtos.Request.OperationType.UPDATE)
//                .setRow(1)
//                .setColumn(1)
//                .setValue(1)
//                .build();
//        op.writeDelimitedTo(out);
//
//        ResponseProtos.Response response = ResponseProtos.Response.parseDelimitedFrom(in);
//
//        String[] inputData = {
//                "5631X94X2",
//                "17948X563",
//                "482563179",
//                "631794825",
//                "794825631",
//                "825631794",
//                "317948256",
//                "X48X56317",
//                "2X63X7948"
//        };
//
//        String result = String.join("", inputData);
//        String playerBoard = response.getBoard().replaceAll("[\\s\\n]", "");
//        assertEquals(result, playerBoard);
//        assertEquals(ResponseProtos.Response.EvalType.PRESET_VALUE, response.getType());
//        assertEquals(3, response.getNext());
//        assertEquals(-2, response.getPoints());
//        assertEquals(gameOptions, response.getMenuoptions());
//
//        assertEquals(ResponseProtos.Response.ResponseType.PLAY, response.getResponseType());
//    }
}