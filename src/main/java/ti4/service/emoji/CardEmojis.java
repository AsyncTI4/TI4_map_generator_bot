package ti4.service.emoji;

public enum CardEmojis implements TI4Emoji {
    // Cards base
    ActionCard, ActionCardAlt, //
    Agenda, AgendaAlt, //
    SecretObjective, SecretObjectiveAlt, //
    Public1, Public1alt, //
    Public2, Public2alt, //
    PN, //
    // Cards pok
    RelicCard, CulturalCard, HazardousCard, IndustrialCard, FrontierCard,

    // Normal Strategy Cards
    SC1, SC1Back, // Leadership
    SC2, SC2Back, // Diplomacy
    SC3, SC3Back, // Politics
    SC4, SC4Back, // Construction
    SC5, SC5Back, // Trade
    SC6, SC6Back, // Warfare
    SC7, SC7Back, // Technology
    SC8, SC8Back, // Imperial
    SCFrontBlank, SCBackBlank, // Generic

    // Strat Card Pings
    sc_1_1, sc_1_2, sc_1_3, sc_1_4, sc_1_5, sc_1_6, // Leadership
    sc_2_1, sc_2_2, sc_2_3, sc_2_4, sc_2_5, sc_2_6, // Diplomacy
    sc_3_1, sc_3_2, sc_3_3, sc_3_4, sc_3_5, // Politics
    sc_4_1, sc_4_2, sc_4_3, sc_4_4, sc_4_5, sc_4_6, sc_4_7, // Construction
    sc_5_1, sc_5_2, sc_5_3, sc_5_4, // Trade
    sc_6_1, sc_6_2, sc_6_3, sc_6_4, sc_6_5, // Warfare
    sc_7_1, sc_7_2, sc_7_3, sc_7_4, sc_7_5, sc_7_6, sc_7_7, // Technology
    sc_8_1, sc_8_2, sc_8_3, sc_8_4, sc_8_5, // Imperial
    
    // Agendas
    CrackedWarSun, ExhaustedPlanet, MixedWormhole, SadMech, TechExplode, ThroughAnomaly
    
    ;
    

    public static TI4Emoji getObjectiveEmoji(String type) {
        return switch (type.toLowerCase()) {
            case "1", "public1alt" -> Public1alt;
            case "2", "public2alt" -> Public2alt;
            case "secret", "secretalt", "secretobjectivealt" -> SecretObjectiveAlt;
            case "public1" -> Public1;
            case "public2" -> Public2;
            case "secretobjective" -> SecretObjective;

            default -> SecretObjective;
        };
    }

    public static TI4Emoji getSCFrontFromInteger(int sc) {
        return switch (sc) {
            case 1 -> SC1;
            case 2 -> SC2;
            case 3 -> SC3;
            case 4 -> SC4;
            case 5 -> SC5;
            case 6 -> SC6;
            case 7 -> SC7;
            case 8 -> SC8;
            default -> SCFrontBlank;
        };
    }

    public static TI4Emoji getSCBackFromInteger(int sc) {
        return switch (sc) {
            case 1 -> SC1Back;
            case 2 -> SC2Back;
            case 3 -> SC3Back;
            case 4 -> SC4Back;
            case 5 -> SC5Back;
            case 6 -> SC6Back;
            case 7 -> SC7Back;
            case 8 -> SC8Back;
            default -> SCBackBlank;
        };
    }

    // Full Mentions
    public static String SC1Mention() {
        return sc_1_1.toString()
            + sc_1_2
            + sc_1_3
            + sc_1_4
            + sc_1_5
            + sc_1_6;
    }

    public static String SC2Mention() {
        return sc_2_1.toString()
            + sc_2_2
            + sc_2_3
            + sc_2_4
            + sc_2_5
            + sc_2_6;
    }

    public static String SC3Mention() {
        return sc_3_1.toString()
            + sc_3_2
            + sc_3_3
            + sc_3_4
            + sc_3_5;
    }

    public static String SC4Mention() {
        return sc_4_1.toString()
            + sc_4_2
            + sc_4_3
            + sc_4_4
            + sc_4_5
            + sc_4_6
            + sc_4_7;
    }

    public static String SC5Mention() {
        return sc_5_1.toString()
            + sc_5_2
            + sc_5_3
            + sc_5_4;
    }

    public static String SC6Mention() {
        return sc_6_1.toString()
            + sc_6_2
            + sc_6_3
            + sc_6_4
            + sc_6_5;
    }

    public static String SC7Mention() {
        return sc_7_1.toString()
            + sc_7_2
            + sc_7_3
            + sc_7_4
            + sc_7_5
            + sc_7_6
            + sc_7_7;
    }

    public static String SC8Mention() {
        return sc_8_1.toString()
            + sc_8_2
            + sc_8_3
            + sc_8_4
            + sc_8_5;
    }

    @Override
    public String toString() {
        return emojiString();
    }

}
