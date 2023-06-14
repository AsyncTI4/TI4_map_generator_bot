package ti4.model;

public enum PlanetTypeModel {
    CULTURAL,
    HAZARDOUS,
    INDUSTRIAL,
    FACTION,
    NONE;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
