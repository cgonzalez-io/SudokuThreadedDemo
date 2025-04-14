# Description 
This is a simple Sudoku game. Many players can play a separate game but see the same leaderboard. Player can type "exit" at any point during the game state to quit the game and disconnect the client from the
server.

## How to run the program
The proto file can be compiled using
``gradle generateProto``  

This will also be done when building the project.  

You should see the compiled proto file in Java under build/generated/source/proto/main/java/buffers  

Now you can run the client and server, please follow these instructions to start:
* Please run `gradle runServer -Pport=port` and `gradle runClient -Phost=hostIP -Pport=port` together.
* There is a separate task `gradle runServerGrading -Pport=port` which has a given board that you can also use for testing
  * Solution as row col val
  * 1 8 8
    1 5 7
    2 6 2
    8 1 9
    8 4 2
    9 2 5
    9 5 1
* Can also be run using `gradle runServer` and `gradle runClient` for localhost and default port
* Recommended that you include the flag `-q --console=plain` to get the best gaming experience (limited output)
* Programs runs on hostIP
* Port and hostIP specification is optional.
* NOTE: If for some reason the .txt files are causing the server or client to crash please either delete them or clear their contents.
  * Hasn't happened to me but, I wanted to make a note just in case someone comes across this!

## Screencast

# Readme checklist:


# Requirements (If checked off then completed and includes debugging)
- 