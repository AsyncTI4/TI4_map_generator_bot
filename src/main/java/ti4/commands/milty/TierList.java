package ti4.commands.milty;

public enum TierList {
    high("high"),
    mid("mid"),
    low("low"),
    red("red"),
    anomaly("anomaly");

    public final String value;

    TierList(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
