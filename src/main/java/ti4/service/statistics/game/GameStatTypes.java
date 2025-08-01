package ti4.service.statistics.game;

public enum GameStatTypes {
    // Add your new statistic here
    UNLEASH_THE_NAMES("Unleash the Names", "Show all the names of the games"),
    AVERAGE_TURNS("Average Turn Amount", "Show the average turns for a faction in a game"),
    HIGHEST_SPENDERS("List Highest Spenders", "Show stats for spending on command tokens/plastics that bot has"),
    GAME_LENGTH("Game Length", "Show game lengths"),
    GAME_LENGTH_4MO("Game Length (past 4 months)", "Show game lengths from the past 4 months"),
    FACTIONS_PLAYED("Plays per Faction", "Show faction play count"),
    COLOURS_PLAYED("Plays per Colour", "Show colour play count"),
    FACTION_WINS("Wins per Faction", "Show the wins per faction"),
    SOS_SCORED("Times a secret objective has been scored", "Show the amount of times each secret objective was scored"),
    FACTION_WIN_PERCENT("Faction win percent", "Shows each faction's win percent rounded to the nearest integer"),
    FACTION_PERFORMANCE("Faction performance", "Shows how much each faction over or under performs its expected win rate"),
    COLOUR_WINS("Wins per Colour", "Show the wins per colour"),
    WINNING_PATH("Winners Path to Victory", "Shows a count of each game's path to victory"),
    PHASE_TIMES("Phase Times", "Shows how long each phase lasted, in days"),
    SUPPORT_WIN_COUNT("Wins with SftT", "Shows a count of wins that occurred holding a Support for the Throne"),
    GAME_COUNT("Total game count", "Shows the total game count");

    private final String name;
    private final String description;

    GameStatTypes(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }

    static GameStatTypes fromString(String id) {
        for (GameStatTypes stat : values()) {
            if (id.equals(stat.toString())) {
                return stat;
            }
        }
        return null;
    }

    public String getAutoCompleteName() {
        return name + ": " + description;
    }

    public boolean search(String searchString) {
        return name.toLowerCase().contains(searchString) || description.toLowerCase().contains(searchString) || toString().contains(searchString);
    }
}
