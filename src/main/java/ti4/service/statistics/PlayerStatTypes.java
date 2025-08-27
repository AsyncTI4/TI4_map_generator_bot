package ti4.service.statistics;

public enum PlayerStatTypes {
    PLAYER_WIN_PERCENT("Player win percent", "Shows the win percent of each player rounded to the nearest integer"), //
    PLAYER_GAME_COUNT("Player game count", "Shows the number of games each player has played in"),
    PLAYER_MATCHMAKING_RATING(
        "Player matchmaking rating",
        "Shows player matchmaking rating calculated with the TrueSkill algorithm");

    private final String name;
    private final String description;

    PlayerStatTypes(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }

    public static PlayerStatTypes fromString(String id) {
        for (PlayerStatTypes stat : values()) {
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
        return name.toLowerCase().contains(searchString)
                || description.toLowerCase().contains(searchString)
                || toString().contains(searchString);
    }
}
