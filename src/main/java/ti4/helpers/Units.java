package ti4.helpers;

import lombok.Getter;

public class Units {

    public static class UnitKey {
        public UnitType unitType;
        public String colorID;

        public String asyncID() {
            return unitType.toString();
        }

        public String unitName() {
            return unitType.humanReadableName();
        }

        UnitKey(UnitType u, String c) {
            unitType = u;
            colorID = c;
        }
    }

    public enum UnitType {
        Infantry("gf"), Mech("mf"), Pds("pd"), Spacedock("sd"), CabalSpacedock("csd"), // ground based
        Fighter("ff"), Destroyer("dd"), Cruiser("ca"), Carrier("cv"), Dreadnought("dn"), Flagship("fs"), Warsun("ws"), //ships
        PlenaryOrbital("plenaryorbital"), TyrantsLament("tyrantslament"); //relics

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
                default -> "asdf";
            };
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    public static UnitKey getUnitKey(String unitType, String color) {
        UnitType u = UnitType.valueOf(unitType);

        return new UnitKey(u, color);
    }
}
