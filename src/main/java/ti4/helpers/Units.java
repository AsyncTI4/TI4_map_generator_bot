package ti4.helpers;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import ti4.AsyncTI4DiscordBot;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;

import org.jetbrains.annotations.Nullable;

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

        public TI4Emoji unitEmoji() {
            return unitType.getUnitTypeEmoji();
        }

        @JsonIgnore
        public String getFileName() {
            if (AsyncTI4DiscordBot.testingMode) return getFileName(false);
            return getFileName(RandomHelper.isOneInX(Constants.EYE_CHANCE));
        }

        public String getFileName(boolean eyes) {
            if (unitType == UnitType.Destroyer && eyes) {
                return String.format("%s_dd_eyes.png", colorID);
            }
            if (UnitType.Lady == unitType || UnitType.Cavalry == unitType) {
                return String.format("%s_%s.png", colorID, "fs");
            }
            if (UnitType.TyrantsLament == unitType) {
                return "TyrantsLament.png";
            }
            if (UnitType.PlenaryOrbital == unitType) {
                return "PlenaryOrbital.png";
            }
            if (UnitType.Monument == unitType) {
                return getColor() + "_monument.png";
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

    /**
     * UnitType - aka {@link UnitModel.getAsyncId()} - is a list of all the units in the game.
     */
    public enum UnitType {
        Infantry("gf"), Mech("mf"), Pds("pd"), Spacedock("sd"), Monument("monument"), // ground based
        Fighter("ff"), Destroyer("dd"), Cruiser("ca"), Carrier("cv"), Dreadnought("dn"), Flagship("fs"), Warsun("ws"), //ships
        PlenaryOrbital("plenaryorbital"), TyrantsLament("tyrantslament"), Lady("lady"), Cavalry("cavalry"), //relics
        StarfallPds("starfallpds");

        @Getter
        public final String value;

        UnitType(String value) {
            this.value = value;
        }

        public String humanReadableName() {
            return switch (this) {
                case Infantry -> "Infantry";
                case Mech -> "Mech";
                case Pds, StarfallPds -> "PDS";
                case Spacedock -> "Space Dock";
                case Fighter -> "Fighter";
                case Destroyer -> "Destroyer";
                case Cruiser -> "Cruiser";
                case Carrier -> "Carrier";
                case Dreadnought -> "Dreadnought";
                case Flagship -> "Flagship";
                case Warsun -> "War Sun";
                case PlenaryOrbital -> "Plenary Orbital";
                case TyrantsLament -> "Tyrant's Lament";
                case Cavalry -> "The Cavalry";
                case Lady -> "The Lady";
                case Monument -> "Monument";
            };
        }

        public String plainName() {
            return switch (this) {
                case Infantry -> "infantry";
                case Mech -> "mech";
                case Pds, StarfallPds -> "pds";
                case Spacedock -> "spacedock";
                case Fighter -> "fighter";
                case Destroyer -> "destroyer";
                case Cruiser -> "cruiser";
                case Carrier -> "carrier";
                case Dreadnought -> "dreadnought";
                case Flagship -> "flagship";
                case Warsun -> "warsun";
                case PlenaryOrbital -> "plenaryorbital";
                case TyrantsLament -> "tyrantslament";
                case Cavalry -> "cavalry";
                case Lady -> "lady";
                case Monument -> "monument";
            };
        }

        public TI4Emoji getUnitTypeEmoji() {
            return switch (this) {
                case Infantry -> UnitEmojis.infantry;
                case Mech -> UnitEmojis.mech;
                case Pds, StarfallPds -> UnitEmojis.pds;
                case Spacedock -> UnitEmojis.spacedock;
                case PlenaryOrbital -> UnitEmojis.PlenaryOrbital;
                case Fighter -> UnitEmojis.fighter;
                case Destroyer -> UnitEmojis.destroyer;
                case Cruiser -> UnitEmojis.cruiser;
                case Carrier -> UnitEmojis.carrier;
                case Dreadnought -> UnitEmojis.dreadnought;
                case Flagship, Cavalry, Lady -> UnitEmojis.flagship;
                case TyrantsLament -> UnitEmojis.TyrantsLament;
                case Warsun -> UnitEmojis.warsun;
                case Monument -> UnitEmojis.Monument;
            };
        }

        @Override
        public String toString() {
            return value;
        }
    }

    public static UnitType findUnitType(String unitType) {
        return switch (unitType) {
            case "gf" -> UnitType.Infantry;
            case "mf" -> UnitType.Mech;
            case "pd", "pds" -> UnitType.Pds;
            case "sd", "csd" -> UnitType.Spacedock;
            case "monument" -> UnitType.Monument;
            case "ff" -> UnitType.Fighter;
            case "dd" -> UnitType.Destroyer;
            case "ca" -> UnitType.Cruiser;
            case "cv" -> UnitType.Carrier;
            case "dn" -> UnitType.Dreadnought;
            case "fs" -> UnitType.Flagship;
            case "ws" -> UnitType.Warsun;
            case "plenaryorbital" -> UnitType.PlenaryOrbital;
            case "tyrantslament" -> UnitType.TyrantsLament;
            case "lady" -> UnitType.Lady;
            case "cavalry" -> UnitType.Cavalry;
            case "starfallpds" -> UnitType.StarfallPds;
            default -> null;
        };
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
