package ti4.map;

public enum GameStatus {
    open("open"),
    locked("locked"),
    finished("finished");

    public final String value;

    GameStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
