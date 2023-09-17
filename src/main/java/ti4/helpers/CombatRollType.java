package ti4.helpers;

public enum CombatRollType {
    combatround("Combat round"),
    afb("Anti-fighter barrage"),
    bombardment("Bombardment");//,
    //spacecannonoffence("Space cannon offence"),
    //spacecannondefence("Space cannon defence"),
    

    public final String value;

    CombatRollType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
