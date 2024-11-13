package ti4.helpers;

public enum DisplayType {
    all("all"),
    map("map"),
    stats("stats"),
    split("split"),
    system("system"),
    wormholes("wormholes"),
    anomalies("anomalies"),
    legendaries("legendaries"),
    empties("empties"),
    aetherstream("aetherstream"),
    spacecannon("space_cannon_offense"),
    traits("traits"),
    techskips("technology_specialties"),
    attachments("attachments"),
    shipless("shipless"),
    googly("googly"),
    landscape("landscape");

    public final String value;

    DisplayType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
