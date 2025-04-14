package client;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Class: Player 
 * Description: Class that represents a Player, I only used it in my Client 
 * to sort the LeaderBoard list
 * You can change this class, decide to use it or not to use it, up to you.
 */

public class Player implements Comparable<Player> {

    private final int wins;
    private final String name;

    // constructor, getters, setters
    public Player(String name, int wins){
      this.wins = wins;
      this.name = name;
    }

    public int getWins(){
      return wins;
    }

    // override equals and hashCode
    @Override
    public int compareTo(Player player) {
        return player.getWins() - this.wins;
    }

    @Override
       public String toString() {
            return ("\n" +this.wins + ": " + this.name);
       }
}