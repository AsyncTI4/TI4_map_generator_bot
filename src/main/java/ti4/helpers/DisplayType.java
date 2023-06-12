package ti4.helpers;

public enum DisplayType {
    all("all"),
    map("map"),
    stats("stats"),
    split("split"),
    system("system");

    public final String value;

    DisplayType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
