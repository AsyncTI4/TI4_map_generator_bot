package ti4.service.combat;

import lombok.Getter;

@Getter
public enum CombatRollType {
    combatround("Combat round"),
    AFB("Anti-fighter barrage"),
    bombardment("Bombardment"),
    SpaceCannonOffence("Space cannon offence"),
    SpaceCannonDefence("Space cannon defence");

    public final String value;

    CombatRollType(String value) {
        this.value = value;
    }
}
