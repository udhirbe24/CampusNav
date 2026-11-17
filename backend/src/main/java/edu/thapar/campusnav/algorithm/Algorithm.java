package edu.thapar.campusnav.algorithm;

/** The shortest-path algorithms the routing engine can choose between. */
public enum Algorithm {
    ASTAR("A* Search"),
    DIJKSTRA("Dijkstra");

    private final String displayName;

    Algorithm(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
