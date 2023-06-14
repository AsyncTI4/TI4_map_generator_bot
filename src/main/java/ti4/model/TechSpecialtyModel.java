package ti4.model;

public enum TechSpecialtyModel {
    BIOTIC,
    CYBERNETIC,
    PROPULSION,
    WARFARE,
    UNITSKIP,
    NONUNITSKIP;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
