package ti4.service.map;

public record BridgeEnd(int side, boolean entry, boolean exit) {

    public boolean isOneWay() {
        return entry != exit;
    }
}
