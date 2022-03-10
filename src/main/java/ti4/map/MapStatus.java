package ti4.map;

public enum MapStatus {
    open("open"),
    locked("locked"),
    finished("finished");

    public final String value;

    MapStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
