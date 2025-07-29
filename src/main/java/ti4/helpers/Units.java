package ti4.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import ti4.AsyncTI4DiscordBot;
import ti4.image.Mapper;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.UnitEmojis;

public class Units {

    private static final String EMDASH = "—";
    private static final Pattern UNIT_PATTERN = Pattern.compile(RegexHelper.colorRegex(null) + EMDASH + RegexHelper.unitTypeRegex());
    private static final Map<UnitType, Map<String, UnitKey>> keys = new ConcurrentHashMap<>();

    /**
     * <H3> DO NOT ADD NEW VALUES TO THIS OBJECT. </H3>
     * <p>
     * It is being used as a key in some major hashmaps which causes issues when we attempt to
     * save/restore from JSON as JSON map keys have to be strings, not JSON objects. This forces
     * us to use custom mappers to resolve.
     * </p>
     */
    @Data
    public static class UnitKey {

        private final UnitType unitType;
        private final String colorID;

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

    public enum UnitState {
        none, //. . 0000
        dmg, // . . 0001
        ;

        public static final int DMG = 0b0000001;

        public boolean isDamaged() {
            return (ordinal() & DMG) > 0;
        }

        public static List<UnitState> defaultAddOrder() {
            return List.of(none, dmg);
        }

        public static List<UnitState> defaultRemoveOrder() {
            return List.of(dmg, none);
        }

        public static List<UnitState> defaultAddStatusOrder() {
            return List.of(none, dmg);
        }

        public static List<UnitState> defaultRemoveStatusOrder() {
            return List.of(dmg, none);
        }

        public static List<Integer> emptyList() {
            List<Integer> ls = new ArrayList<>();
            for (int i = 0; i < values().length; i++)
                ls.add(0);
            return ls;
        }

        public String humanDescr() {
            return switch (this) {
                case none -> "";
                case dmg -> "damaged";
            };
        }
    }

    public static UnitState findUnitState(String state) {
        if (state == null) return null;
        return switch (state.toLowerCase()) {
            case "none" -> UnitState.none;
            case "dmg", "damaged", "damage" -> UnitState.dmg;
            default -> null;
        };
    }

    public static UnitType findUnitType(String unitType) {
        return switch (AliasHandler.resolveUnit(unitType)) {
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

    public static UnitKey getUnitKey(String unitType, String color) {
        UnitType u = findUnitType(unitType);
        if (color == null || u == null) return null;
        return lookupKey(u, color);
    }

    public static UnitKey getUnitKey(UnitType unitType, String color) {
        return lookupKey(unitType, color);
    }

    private static UnitKey lookupKey(UnitType type, String color) {
        String colorID = Mapper.getColorID(color);
        if (type == null || colorID == null) {
            return null;
        }
        var map = keys.computeIfAbsent(type, k -> new ConcurrentHashMap<>());
        return map.computeIfAbsent(colorID, k -> new UnitKey(type, colorID));
    }

    @Nullable
    public static UnitKey parseID(String id) {
        if (id.contains(".png")) {
            id = id.replace(".png", "").replace("_", EMDASH).replace("-", EMDASH);
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
