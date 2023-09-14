package ti4.helpers;

public enum CombatRollType {
    combatround("Combat round"),
    afb("Anti-fighter barrage");//,
    //spacecannonoffence("Space cannon offence"),
    //spacecannondefence("Space cannon defence"),
    //bombardment("Bombardment");

    public final String value;

    CombatRollType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
