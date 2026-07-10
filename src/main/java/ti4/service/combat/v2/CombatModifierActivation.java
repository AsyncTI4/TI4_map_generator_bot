package ti4.service.combat.v2;

/** A saved combat-effect source; the rule registry resolves its behavior for each roll type. */
public record CombatModifierActivation(
        String sourceType, String sourceId, int turn, String systemId, String holderName) {

    public CombatModifierActivation {
        systemId = systemId == null ? "" : systemId;
        holderName = holderName == null ? "" : holderName;
    }

    public static CombatModifierActivation pending(String sourceType, String sourceId, int turn) {
        return new CombatModifierActivation(sourceType, sourceId, turn, "", "");
    }

    public boolean pending() {
        return systemId.isEmpty() && holderName.isEmpty();
    }

    public CombatModifierActivation bind(String systemId, String holderName) {
        return new CombatModifierActivation(sourceType, sourceId, turn, systemId, holderName);
    }

    public String getSaveString() {
        return String.join(",", sourceType, sourceId, Integer.toString(turn), systemId, holderName);
    }

    public static CombatModifierActivation fromSaveString(String saveString) {
        String[] fields = saveString.split(",", -1);
        if (fields.length == 6) {
            return new CombatModifierActivation(
                    fields[1], fields[2], Integer.parseInt(fields[3]), fields[4], fields[5]);
        }
        if (fields.length != 5) throw new IllegalArgumentException("Invalid combat modifier activation: " + saveString);
        return new CombatModifierActivation(fields[0], fields[1], Integer.parseInt(fields[2]), fields[3], fields[4]);
    }
}
