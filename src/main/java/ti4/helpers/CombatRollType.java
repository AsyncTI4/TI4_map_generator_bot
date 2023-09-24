package ti4.helpers;

public enum CombatRollType {
    combatround("Combat round"), AFB("Anti-fighter barrage"), bombardment("Bombardment"), SpaceCannonOffence("Space cannon offence");
    //spacecannondefence("Space cannon defence"),

    public final String value;

    CombatRollType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
