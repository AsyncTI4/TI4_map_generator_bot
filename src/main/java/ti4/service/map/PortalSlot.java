package ti4.service.map;

public record PortalSlot(int slot, char letter, Flow flow) {

    public static final int CENTRE = 0;
    public static final int SLOTS = 7;

    public enum Flow {
        BOTH(""),
        ENTRY_ONLY(">"),
        EXIT_ONLY("<");

        private final String marker;

        Flow(String marker) {
            this.marker = marker;
        }

        public String marker() {
            return marker;
        }

        public boolean letsShipsLeave() {
            return this != EXIT_ONLY;
        }

        public boolean letsShipsArrive() {
            return this != ENTRY_ONLY;
        }
    }

    public PortalSlot rotatedBy(int steps) {
        if (slot == CENTRE) {
            return this;
        }
        int turned = Math.floorMod(slot - 1 + steps, 6) + 1;
        return new PortalSlot(turned, letter, flow);
    }

    public String typed() {
        return slot + "=" + letter + flow.marker();
    }
}
