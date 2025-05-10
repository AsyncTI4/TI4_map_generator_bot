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
    landscape("landscape"),
    unlocked("unlocked"); // Master Display Type = Map, force HexBorder = Solid, hide locked units (have a CC of their colour in their system)

    public final String value;

    DisplayType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
