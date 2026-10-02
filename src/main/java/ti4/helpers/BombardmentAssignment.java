package ti4.helpers;

public record BombardmentAssignment(
        String sourceId, String planet, boolean galvanized, BombardmentAssignmentType type) {

    public String encode() {
        return sourceId + "|" + planet + "|" + galvanized + "|" + type;
    }

    public static BombardmentAssignment decode(String s) {
        String[] p = s.split("\\|");
        return new BombardmentAssignment(
                p[0], p[1], Boolean.parseBoolean(p[2]), BombardmentAssignmentType.valueOf(p[3]));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BombardmentAssignment that = (BombardmentAssignment) obj;
        return sourceId.equals(that.sourceId)
                && planet.equals(that.planet)
                && galvanized == that.galvanized
                && type == that.type;
    }
}
