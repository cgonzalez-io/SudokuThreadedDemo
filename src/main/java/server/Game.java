package server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
     * one that was generated with the board, etc.
     * playerBoard: the board directly modified by the player selecting spots
     * difficulty: how many empty cells in symmetry, if you notice the empty spots on the board the opposite
     * grids are a mirror of themselves
     */
    private final int size = 9;

    private final char[][] solvedBoard = new char[size][size]; // the solution
    private final char[][] referenceBoard = new char[size][size]; // the given board to player at start
    private final char[][] playerBoard = new char[size][size]; // current board player sees
    private int difficulty = 1;

    private int points = 0;

    private boolean won; // if the game is won or not


    /**
     * Constructor for the Game class.
     * Initializes the game state by setting the 'won' field to true.
     * This ensures that a new image will be created when calling the newGame() method.
     * The constructor does not perform any additional setup beyond this initialization.
     */
    public Game() {
        // you can of course add more or change this setup completely. You are totally free to also use just Strings in your Server class instead of this class
        won = true; // setting it to true, since then in newGame() a new image will be created

    }


    /**
     * Marks the game as won by setting the internal state of the 'won' field to true.
     * This method does not perform any additional checks or operations
     * and simply updates the 'won' flag, which can be used to track whether
     * the game has been completed successfully.
     * for testing purposes, you can call this method to simulate a win condition.
     */
    public void setWon() {
        won = true;
    }

    public boolean getWon() {
        return won;
    }

    /**
     * Good to use for getting the first board of game
     * Method loads in a new image from the specified files and creates the hidden image for it.
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
     */
    public void newBoard(boolean grading) {
        newGame(grading, difficulty);
    }


    ////////////////////////
    // The next three methods are used in the game to create a new random board, you should not need to touch or call them

    /**
     * Creates a completely new Sudoku board (should not need to be changed)
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
                resultType = 1; // the original number so cannot replace

            } else {
                // not original number so replacing
                playerBoard[row][column] = (char) (value + '0');
                int moveOK = checkMove(row, column);
                if (moveOK == 0) {
                    won = checkWon();
                    resultType = 0;
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
     * Checks the validity of a move in a Sudoku board by evaluating
     * whether a number already exists in the same row, column, or grid.
     *
     * @param row the row index where the move is being checked
     * @param col the column index where the move is being checked
     * @return an integer indicating the result of the check:
     * 0 - the move is valid, and the board can be changed
     * 2 - the number already exists in the specified row
     * 3 - the number already exists in the specified column
     * 4 - the number already exists in the specified grid
     */
    public int checkMove(int row, int col) {
        if (isExistsInRow(row)) {
            return 2;
        } else if (isExistsInCol(col)) {
            return 3;
        } else if (isExistsInGrid(row, col)) {
            return 4;
        } else { // X was replaced
            return 0;
        }
    }

    /**
     * Checks whether the game has been won by verifying all cells in the player's board.
     * The method ensures that there are no cells marked with 'X',
     * indicating an unresolved or incomplete state.
     *
     * @return true if all cells in the player's board are not marked as 'X', indicating a win;
     * false otherwise.
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
     * Checks whether a duplicate number exists in the 3x3 grid that includes the specified row and column.
     *
     * @param row the row index of the 3x3 grid to check
     * @param col the column index of the 3x3 grid to check
     * @return true if there is a duplicate number in the 3x3 grid; false otherwise
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
     * Checks whether a duplicate numeric value exists in the specified column
     * of a Sudoku board. Values are fetched for the column, and duplicates
     * are identified excluding cells marked with 'X'.
     *
     * @param col the column index (0-based) for which to check for duplicate values
     * @return true if a duplicate value exists in the column; false otherwise
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
     * Checks if there are duplicate numeric values in the specified row of the Sudoku board.
     * This method evaluates the current state of the board and determines
     * whether a number appears more than once in the given row, ignoring 'X' characters.
     *
     * @param row the row index to check for duplicate numeric values, where 0 is the first row
     * @return true if a duplicate numeric value exists in the specified row, false otherwise
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
     * Extracts a specific column from a Sudoku board representation.
     * The board is represented as a single string with rows separated by a fixed step,
     * and the method retrieves all characters corresponding to the specified column.
     *
     * @param board the string representation of the Sudoku board
     * @param col   the column index (0-based) to extract from the board
     * @return a string containing all characters from the specified column
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
     *
     * @param board the current board
     * @param row   the row of the grid
     * @param col   the column of the grid
     * @return the grid as a string
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
     *
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
     *
     * @return String of the current board
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

        return (sb.toString());
    }

    public int getPoints() {
        return points;
    }

    public int setPoints(int diff) {
        return points += diff;
    }

    /**
     * Creates a completely new Sudoku board with Xs
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
     *
     * @return numbers, a list of shuffled integers from 0 to 8
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
}
