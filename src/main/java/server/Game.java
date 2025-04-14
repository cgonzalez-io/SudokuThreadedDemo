package server;
import java.util.*;
import java.io.*;

/**
 * Class: Game 
 * Description: Game class that can load an ascii image
 * Class can be used to hold the persistent state for a game for different threads
 * synchronization is not taken care of .
 * You can change this Class in any way you like or decide to not use it at all
 * I used this class in my SockBaseServer to create a new game and keep track of the current image evenon differnt threads. 
 * My threads each get a reference to this Game
 */

public class Game {

    /**
     * fullBoard: Solution with no empty spots
     * referenceBoard: the board with the initial empty spaces, used for clearing, determining if spot being cleared is
     *                  one that was generated with the board, etc.
     * playerBoard: the board directly modified by the player selecting spots
     * difficulty: how many empty cells in symmetry, if you notice the empty spots on the board the opposite
     *          grids are a mirror of themselves
     */
    private final int size = 9;

    private final char[][] solvedBoard =  new char[size][size]; // the solution
    private final char[][] referenceBoard =  new char[size][size]; // the given board to player at start
    private final char[][] playerBoard =  new char[size][size]; // current board player sees
    private int difficulty = 1;

    private int points = 0;

    private boolean won; // if the game is won or not


    public Game(){
        // you can of course add more or change this setup completely. You are totally free to also use just Strings in your Server class instead of this class
        won = true; // setting it to true, since then in newGame() a new image will be created

    }

    /**
     * Sets the won flag to true
     * @param args Unused.
     * @return Nothing.
     */
    public void setWon(){
        won = true;
    }

    public boolean getWon(){
        return won;
    }

    /**
     * Good to use for getting the first board of game
     * Method loads in a new image from the specified files and creates the hidden image for it.
     * @return Nothing.
     */
    public void newGame(boolean grading, int difficulty) {
        this.difficulty = difficulty;
        points = 0; // reset points
        if (won) {
            won = false;
            if (!grading) {
                create();
                prepareForPlay();
            } else {

                String[] inputData = {
                        "5631X94X2",
                        "17948X563",
                        "482563179",
                        "631794825",
                        "794825631",
                        "825631794",
                        "317948256",
                        "X48X56317",
                        "2X63X7948"
                };

                // Loop through the input data and load it into the fullBoard array
                for (int i = 0; i < size; i++) {
                    String line = inputData[i];  // Get the line from inputData

                    // Loop through each character in the line and populate the 2D array
                    for (int j = 0; j < size; j++) {
                        referenceBoard[i][j] = line.charAt(j);  // Assign each character
                        playerBoard[i][j] = line.charAt(j);
                    }
                }

                char[][] solvedBoard = {
                        {'5', '6', '3', '1', '7', '9', '4', '8', '2'},
                        {'1', '7', '9', '4', '8', '2', '5', '6', '3'},
                        {'4', '8', '2', '5', '6', '3', '1', '7', '9'},
                        {'6', '3', '1', '7', '9', '4', '8', '2', '5'},
                        {'7', '9', '4', '8', '2', '5', '6', '3', '1'},
                        {'8', '2', '5', '6', '3', '1', '7', '9', '4'},
                        {'3', '1', '7', '9', '4', '8', '2', '5', '6'},
                        {'9', '4', '8', '2', '5', '6', '3', '1', '7'},
                        {'2', '5', '6', '3', '1', '7', '9', '4', '8'}
                };


            }

        }

    }


    /**
     * Might be good to use when CLEAR and getting a new board
     * Method that creates a new board with given grading flag but same difficulty as was provided before
     * @return Nothing.
     */
    public void newBoard(boolean grading){
        newGame(grading, difficulty);
    }


    ////////////////////////
    // The next three methods are used in the game to create a new random board, you should not need to touch or call them
    /**
     * Creates a completely new Sudoku board (should not need to be changed)
     * @return Nothing.
     */
    public void create() {

        List<Integer> numbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9));
        Collections.shuffle(numbers);

        List<Integer> positions = new ArrayList<>(Arrays.asList(0, 3, 6, 1, 4, 7, 2, 5, 8));

        List<Integer> rows = shuffle();
        List<Integer> columns = shuffle();

        for (int row = 0; row < rows.size(); row++) {
            List<Integer> newRow = new ArrayList<>();
            for (int col = 0; col < columns.size(); col++) {
                int position = (positions.get(row) + col) % numbers.size();
                newRow.add(numbers.get(position));
            }
            int i = 0;
            for (Integer num : newRow) {
                solvedBoard[row][i++] = (char) (num + '0');
            }
        }

        for (int row = 0; row < rows.size(); row++) {
            for (int col = 0; col < columns.size(); col++) {
                playerBoard[row][col] = solvedBoard[row][col];
                referenceBoard[row][col] = solvedBoard[row][col];
            }
        }
    }

    /**
     * Creates a completely new Sudoku board with Xs
     * @return Nothing.
     */
    private void prepareForPlay() {
        int empties = difficulty;

        int maxCells = (int) Math.ceil((double) (size * size) / 2);
        List<Integer> allCells = new ArrayList<>();
        for (int i = 0; i < maxCells; i++) {
            allCells.add(i);
        }

        Collections.shuffle(allCells);

        List<Integer> cells = allCells.subList(0, Math.min(empties, allCells.size()));

        for (Integer cell : cells) {
            int row = cell / size;
            int col = cell % size;

            playerBoard[row][col] = 'X';
            playerBoard[8 - row][8 - col] = 'X';

            referenceBoard[row][col] = 'X';
            referenceBoard[8 - row][8 - col] = 'X';
        }
    }

    /**
     * Creates a completely new Sudoku board (should not need to be changed)
     * @return Nothing.
     */
    private List<Integer> shuffle() {
        List<Integer> first = new ArrayList<>(Arrays.asList(0, 1, 2));
        List<Integer> second = new ArrayList<>(Arrays.asList(3, 4, 5));
        List<Integer> third = new ArrayList<>(Arrays.asList(6, 7, 8));

        Collections.shuffle(first);
        Collections.shuffle(second);
        Collections.shuffle(third);

        List<Integer> numbers = new ArrayList<>();
        numbers.addAll(first);
        numbers.addAll(second);
        numbers.addAll(third);

        Collections.shuffle(numbers);

        return numbers;
    }

    /**
     * Good to use for an UPDATE call
     * Method changes the given row column with value if type is 0 and the move is valid.
     * If move is not valid it returns a number specifying what went wrong
     * 0 - board was changed - new number added or clear operation
     * 1 - tried to change given number
     * 2 - number was already in row so cannot be added
     * 3 - number was already in column so cannot be added
     * 4 - number was already in grid so cannot be added
     */
    public int updateBoard(int row, int column, int value, int type) {
        int resultType = 0;
        if (type == 0) {
            if (referenceBoard[row][column] != 'X') {
                resultType =  1; // the original number so cannot replace

            } else {
                // not original number so replacing
                playerBoard[row][column] = (char) (value + '0');
                int moveOK = checkMove(row, column);
                if (moveOK == 0) {
                    won = checkWon();
                    resultType =  0;
                } else {
                    playerBoard[row][column] = referenceBoard[row][column];
                    resultType = moveOK;
                }
            }
        } else if (type == 1) {
            // type 1 is clearing a single cell back to 'X'
            playerBoard[row][column] = referenceBoard[row][column];
        } else if (type == 2) {
            // clear row back to original
            if (size >= 0) System.arraycopy(referenceBoard[row], 0, playerBoard[row], 0, size);
        } else if (type == 3) {
            // clear col back to original
            for (int i = 0; i < size; i++) {
                playerBoard[i][column] = referenceBoard[i][column];
            }
        } else if (type == 4) {
            // clear grid back to original
            int startRow = (row / 3) * 3;
            int startCol = (column / 3) * 3;

            for (int i = startRow; i < startRow + 3; i++) {
                System.arraycopy(referenceBoard[i], startCol, playerBoard[i], startCol, startCol + 3 - startCol);
            }
        } else if (type == 5) {
            // clear entire board back to original
            for (int i = 0; i < 9; i++) {
                System.arraycopy(referenceBoard[i], 0, playerBoard[i], 0, 9);
            }
        } else if (type == 6) {
            // generate a new board
            create();
            prepareForPlay();
        } else {
            // not recognized, setting row, col to default
            playerBoard[row][column] = referenceBoard[row][column];
            resultType = 5; // something was off

        }
        System.out.println(resultType);
        return resultType;
    }

    /**
     * I never called this separatly from server
     * Checks if the move was valid for setting a number used in previous method
     */
    public int checkMove(int row, int col){
        if(isExistsInRow(row)){
            return 2;
        } else if (isExistsInCol(col)){
            return 3;
        } else if(isExistsInGrid(row, col)){
            return 4;
        } else { // X was replaced
            return 0;
        }
    }
    /** Might be useful in server
     * Method that checks if there is still an X on board, if so return false else true (basically checks if won)
     */
    public boolean checkWon() {
        for (int row = 0; row < playerBoard.length; row++) {
            for (int col = 0; col < playerBoard[row].length; col++) {
                if (playerBoard[row][col] == 'X') {
                    return false;
                }
            }
        }
        return true; //
    }

    /**
     * Method checks if in the current board there is a duplicate number in the current grid and returns true if there is a duplicate
     */
    public boolean isExistsInGrid(int row, int col) {
        String currGrid = getGrid(getBoard(), row, col);
        int[] currentGridBucket = new int[10];
        for (int i = 0; i < 9; i++) {
            if (currGrid.charAt(i) == 'X') {
                continue;
            }
            int ind = Character.getNumericValue(currGrid.charAt(i));
            currentGridBucket[ind]++;
            if (currentGridBucket[ind] > 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method checks if in the current board there is a duplicate number in the current column and returns true if there is a duplicate
     */
    public boolean isExistsInCol(int col) {
        String currCol = getColumn(getBoard(), col);
        int[] currentColumnBucket = new int[10];
        for (int i = 0; i < 9; i++) {
            if (currCol.charAt(i) == 'X') {
                continue;
            }
            int ind = Character.getNumericValue(currCol.charAt(i));
            currentColumnBucket[ind]++;
            if (currentColumnBucket[ind] > 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method checks if in the current board there is a duplicate number in the current row and returns true if there is a duplicate
     */
    public boolean isExistsInRow(int row) {
        String currRow = getBoard().substring((row * 10), (row * 10) + 10);
        int[] currentRowBucket = new int[10];
        for (int i = 0; i < 9; i++) {
            if (currRow.charAt(i) == 'X') {
                continue;
            }
            int ind = Character.getNumericValue(currRow.charAt(i));
            currentRowBucket[ind]++;
            if (currentRowBucket[ind] > 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets all values in column
     */
    public String getColumn(String board, int col) {
        StringBuilder column = new StringBuilder();

        for (int row = 0; row < 9; row++) {
            int position = (row * 10) + col;
            column.append(board.charAt(position));
        }

        return column.toString();
    }

    /**
     * Gets all values in grid
     */
    public String getGrid(String board, int row, int col) {
        StringBuilder grid = new StringBuilder();
        int startRow = (row / 3) * 3;
        int startCol = (col / 3) * 3;

        for (int i = startRow; i < startRow + 3; i++) {
            for (int j = startCol; j < startCol + 3; j++) {
                int index = (i * 10) + j;
                grid.append(board.charAt(index));
            }
        }

        return grid.toString();
    }


    /**
     * Method returns the String of the current board
     * @return String of the current board
     */
    public String getBoard() {
        StringBuilder sb = new StringBuilder();
        for (char[] subArray : playerBoard) {
            sb.append(subArray);
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * returns version of board to be shown on CLI, nicer way of seeing it and splitting it up
     */
    public String getDisplayBoard() {
        StringBuilder sb = new StringBuilder();

        for (int row = 0; row < playerBoard.length; row++) {
            if (row > 0 && row % 3 == 0) {
                sb.append("\n");
            }
            for (int col = 0; col < playerBoard.length; col++) {
                if (col > 0 && col % 3 == 0) {
                    sb.append(" ");
                }
                sb.append(playerBoard[row][col]).append(" ");
            }
            sb.append("\n");
        }

        return(sb.toString());
    }

    public int getPoints() {
        return points;
    }

    public int setPoints(int diff) {
        return points += diff;
    }
}
