package ti4.helpers;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Units {

    private static final String EMDASH = "—";
    private static final Pattern UNIT_PATTERN = Pattern.compile(RegexHelper.colorRegex(null) + EMDASH + RegexHelper.unitTypeRegex());

    /**
     * <H3>
     * DO NOT ADD NEW VALUES TO THIS OBJECT.
     * </H3>
     * 
     * <p>
     * It is being used as a key in some major hashmaps which causes issues when we attempt to
     * save/restore from JSON as JSON map keys have to be strings, not JSON objects. This forces
     * us to use custom mappers to resolve.
     * </p>
     */
    @Data
    public static class UnitKey {

        private UnitType unitType;
        private String colorID;

        @JsonIgnore
        public String getColor() {
            return AliasHandler.resolveColor(colorID);
        }

        public String asyncID() {
            return unitType.toString();
        }

        public String unitName() {
            return unitType.plainName();
        }

        public String unitEmoji() {
            return unitType.getUnitTypeEmoji();
        }

        @JsonIgnore
        public String getFileName() {
            return getFileName(ThreadLocalRandom.current().nextInt(Constants.EYE_CHANCE) == 0);
        }

        public String getFileName(boolean eyes) {
            if (unitType == UnitType.Destroyer && eyes) {
                return String.format("%s_dd_eyes.png", colorID);
            }
            if (UnitType.TyrantsLament == unitType || UnitType.Lady == unitType || UnitType.Cavalry == unitType) {
                return String.format("%s_%s.png", colorID, "fs");
            }
            if (UnitType.PlenaryOrbital == unitType) {
                return String.format("%s_%s.png", colorID, "sd");
            }

            return String.format("%s_%s.png", colorID, asyncID());
        }

        @JsonIgnore
        public String getOldUnitID() {
            return String.format("%s_%s.png", colorID, asyncID());
        }

        public String toString() {
            return String.format("%s—%s", colorID, unitType.humanReadableName());
        }

        public String outputForSave() {
            return String.format("%s%s%s", colorID, EMDASH, asyncID());
        }

        public UnitKey(@JsonProperty("unitType") UnitType unitType, @JsonProperty("colorID") String colorID) {
            this.unitType = unitType;
            this.colorID = colorID;
        }
    }

    public enum UnitType {

        Infantry("gf"), Mech("mf"), Pds("pd"), Spacedock("sd"), CabalSpacedock("csd"), // ground based
        Fighter("ff"), Destroyer("dd"), Cruiser("ca"), Carrier("cv"), Dreadnought("dn"), Flagship("fs"), Warsun("ws"), //ships
        PlenaryOrbital("plenaryorbital"), TyrantsLament("tyrantslament"), Lady("lady"), Cavalry("cavalry"); //relics

        @Getter
        public final String value;

        UnitType(String value) {
            this.value = value;
        }

        public String humanReadableName() {
            return switch (value) {
                case "gf" -> "Infantry";
                case "mf" -> "Mech";
                case "pd" -> "PDS";
                case "sd" -> "Space Dock";
                case "csd" -> "Dimensional Tear";
                case "ff" -> "Fighter";
                case "dd" -> "Destroyer";
                case "ca" -> "Cruiser";
                case "cv" -> "Carrier";
                case "dn" -> "Dreadnought";
                case "fs" -> "Flagship";
                case "ws" -> "War Sun";
                case "plenaryorbital" -> "Plenary Orbital";
                case "tyrantslament" -> "Tyrant's Lament";
                case "cavalry" -> "The Cavalry";
                case "lady" -> "The Lady";
                default -> null;
            };
        }

        public String plainName() {
            return switch (value) {
                case "gf" -> "infantry";
                case "mf" -> "mech";
                case "pd" -> "pds";
                case "sd" -> "spacedock";
                case "csd" -> "cabalspacedock";
                case "ff" -> "fighter";
                case "dd" -> "destroyer";
                case "ca" -> "cruiser";
                case "cv" -> "carrier";
                case "dn" -> "dreadnought";
                case "fs" -> "flagship";
                case "ws" -> "warsun";
                case "plenaryorbital" -> "plenaryorbital";
                case "tyrantslament" -> "tyrantslament";
                case "cavalry" -> "cavalry";
                case "lady" -> "lady";
                default -> null;
            };
        }

        public String getUnitTypeEmoji() {
            return switch (value) {
                case "gf" -> Emojis.infantry;
                case "mf" -> Emojis.mech;
                case "pd" -> Emojis.pds;
                case "sd", "csd", "plenaryorbital" -> Emojis.spacedock;
                case "ff" -> Emojis.fighter;
                case "dd" -> Emojis.destroyer;
                case "ca" -> Emojis.cruiser;
                case "cv" -> Emojis.carrier;
                case "dn" -> Emojis.dreadnought;
                case "fs", "tyrantslament", "lady", "cavalry" -> Emojis.flagship;
                case "ws" -> Emojis.warsun;
                default -> null;
            };
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public static UnitType findUnitType(String unitType) {
        for (UnitType t : UnitType.values()) {
            if (t.value.equalsIgnoreCase(unitType)) return t;
        }
        return null;
    }

    public static UnitKey getUnitKey(String unitType, String colorID) {
        UnitType u = findUnitType(unitType);
        if (colorID == null || u == null) return null;
        return new UnitKey(u, colorID);
    }

    public static UnitKey getUnitKey(UnitType unitType, String colorID) {
        return new UnitKey(unitType, colorID);
    }

    @Nullable
    public static UnitKey parseID(String id) {
        if (id.contains(".png")) {
            id = id.replace(".png", "").replace("_", EMDASH);
        }

        Matcher unitParser = UNIT_PATTERN.matcher(id);
        if (unitParser.matches()) {
            String colorID = unitParser.group("color");
            String unitType = unitParser.group("unittype");
            return getUnitKey(unitType, colorID);
        }
        return null;
    }
}
