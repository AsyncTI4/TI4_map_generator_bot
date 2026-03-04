package ti4.service.emoji;

import ti4.map.Game;
import ti4.map.Player;

public enum CardEmojis implements TI4Emoji {
    // Cards base
    ActionCard,
    ActionCardAlt, //
    Agenda,
    AgendaAlt, //
    SecretObjective,
    SecretObjectiveAlt, //
    Public1,
    Public1alt, //
    Public2,
    Public2alt, //
    PN, //
    // Cards pok
    RelicCard,
    CulturalCard,
    HazardousCard,
    IndustrialCard,
    FrontierCard,
    Event,

    // Normal Strategy Cards
    SC1,
    SC1Back, // Leadership
    SC2,
    SC2Back, // Diplomacy
    SC3,
    SC3Back, // Politics
    SC4,
    SC4Back, // Construction
    SC5,
    SC5Back, // Trade
    SC6,
    SC6Back, // Warfare
    SC7,
    SC7Back, // Technology
    SC8,
    SC8Back, // Imperial
    SCFrontBlank,
    SCBackBlank, // Generic

    // Strat Card Pings
    sc_1_1,
    sc_1_2,
    sc_1_3,
    sc_1_4,
    sc_1_5,
    sc_1_6, // Leadership
    sc_2_1,
    sc_2_2,
    sc_2_3,
    sc_2_4,
    sc_2_5,
    sc_2_6, // Diplomacy
    sc_3_1,
    sc_3_2,
    sc_3_3,
    sc_3_4,
    sc_3_5, // Politics
    sc_4_1,
    sc_4_2,
    sc_4_3,
    sc_4_4,
    sc_4_5,
    sc_4_6,
    sc_4_7, // Construction
    sc_5_1,
    sc_5_2,
    sc_5_3,
    sc_5_4, // Trade
    sc_6_1,
    sc_6_2,
    sc_6_3,
    sc_6_4,
    sc_6_5, // Warfare
    sc_7_1,
    sc_7_2,
    sc_7_3,
    sc_7_4,
    sc_7_5,
    sc_7_6,
    sc_7_7, // Technology
    sc_8_1,
    sc_8_2,
    sc_8_3,
    sc_8_4,
    sc_8_5, // Imperial

    // Twilight's Fall decks
    TF_Ability_Card,
    TF_Action_Card,
    TF_Edict_Card,
    TF_Genome_Card,
    TF_Paradigm_Card,
    TF_Unit_Uprade_Card,

    // Twilight's Fall Strategy Card Pings
    // Lux
    tf_1_1,
    tf_1_2,
    tf_1_3,
    // Noctis
    tf_2_1,
    tf_2_2,
    tf_2_3,
    tf_2_4,
    // Tyrannus
    tf_3_1,
    tf_3_2,
    tf_3_3,
    tf_3_4,
    tf_3_5,
    tf_3_6,
    // Civitas
    tf_4_1,
    tf_4_2,
    tf_4_3,
    tf_4_4,
    // Amicus
    tf_5_1,
    tf_5_2,
    tf_5_4,
    tf_5_3,
    // Calamitas
    tf_6_1,
    tf_6_2,
    tf_6_3,
    tf_6_4,
    tf_6_5,
    tf_6_6,
    // Magus
    tf_7_1,
    tf_7_2,
    tf_7_3,
    tf_7_4,
    // Aeterna
    tf_8_1,
    tf_8_2,
    tf_8_3,
    tf_8_4,
    tf_8_5,

    // Agendas
    CrackedWarSun,
    ExhaustedPlanet,
    MixedWormhole,
    SadMech,
    TechExplode,
    ThroughAnomaly;

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
        StringBuilder sb = new StringBuilder();
        sb.append(sc_1_1)
                .appendCodePoint(0x2060)
                .append(sc_1_2)
                .appendCodePoint(0x2060)
                .append(sc_1_3)
                .appendCodePoint(0x2060)
                .append(sc_1_4)
                .appendCodePoint(0x2060)
                .append(sc_1_5)
                .appendCodePoint(0x2060)
                .append(sc_1_6);
        return sb.toString();
    }

    public static String SC2Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(sc_2_1)
                .appendCodePoint(0x2060)
                .append(sc_2_2)
                .appendCodePoint(0x2060)
                .append(sc_2_3)
                .appendCodePoint(0x2060)
                .append(sc_2_4)
                .appendCodePoint(0x2060)
                .append(sc_2_5)
                .appendCodePoint(0x2060)
                .append(sc_2_6);
        return sb.toString();
    }

    public static String SC3Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(sc_3_1)
                .appendCodePoint(0x2060)
                .append(sc_3_2)
                .appendCodePoint(0x2060)
                .append(sc_3_3)
                .appendCodePoint(0x2060)
                .append(sc_3_4)
                .appendCodePoint(0x2060)
                .append(sc_3_5);
        return sb.toString();
    }

    public static String SC4Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(sc_4_1)
                .appendCodePoint(0x2060)
                .append(sc_4_2)
                .appendCodePoint(0x2060)
                .append(sc_4_3)
                .appendCodePoint(0x2060)
                .append(sc_4_4)
                .appendCodePoint(0x2060)
                .append(sc_4_5)
                .appendCodePoint(0x2060)
                .append(sc_4_6)
                .appendCodePoint(0x2060)
                .append(sc_4_7);
        return sb.toString();
    }

    public static String SC5Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(sc_5_1)
                .appendCodePoint(0x2060)
                .append(sc_5_2)
                .appendCodePoint(0x2060)
                .append(sc_5_3)
                .appendCodePoint(0x2060)
                .append(sc_5_4);
        return sb.toString();
    }

    public static String SC6Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(sc_6_1)
                .appendCodePoint(0x2060)
                .append(sc_6_2)
                .appendCodePoint(0x2060)
                .append(sc_6_3)
                .appendCodePoint(0x2060)
                .append(sc_6_4)
                .appendCodePoint(0x2060)
                .append(sc_6_5);
        return sb.toString();
    }

    public static String SC7Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(sc_7_1)
                .appendCodePoint(0x2060)
                .append(sc_7_2)
                .appendCodePoint(0x2060)
                .append(sc_7_3)
                .appendCodePoint(0x2060)
                .append(sc_7_4)
                .appendCodePoint(0x2060)
                .append(sc_7_5)
                .appendCodePoint(0x2060)
                .append(sc_7_6)
                .appendCodePoint(0x2060)
                .append(sc_7_7);
        return sb.toString();
    }

    public static String SC8Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(sc_8_1)
                .appendCodePoint(0x2060)
                .append(sc_8_2)
                .appendCodePoint(0x2060)
                .append(sc_8_3)
                .appendCodePoint(0x2060)
                .append(sc_8_4)
                .appendCodePoint(0x2060)
                .append(sc_8_5);
        return sb.toString();
    }

    /**
     * @return Fancy SC1 Emojis for Lux
     */
    public static String TFSC1Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(tf_1_1)
                .appendCodePoint(0x2060)
                .append(tf_1_2)
                .appendCodePoint(0x2060)
                .append(tf_1_3);
        return sb.toString();
    }

    /**
     * @return Fancy SC2 Emojis for Noctis
     */
    public static String TFSC2Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(tf_2_1)
                .appendCodePoint(0x2060)
                .append(tf_2_2)
                .appendCodePoint(0x2060)
                .append(tf_2_3)
                .appendCodePoint(0x2060)
                .append(tf_2_4);
        return sb.toString();
    }

    /**
     * @return Fancy SC3 Emojis for Tyrannus
     */
    public static String TFSC3Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(tf_3_1)
                .appendCodePoint(0x2060)
                .append(tf_3_2)
                .appendCodePoint(0x2060)
                .append(tf_3_3)
                .appendCodePoint(0x2060)
                .append(tf_3_4)
                .appendCodePoint(0x2060)
                .append(tf_3_5)
                .appendCodePoint(0x2060)
                .append(tf_3_6);
        return sb.toString();
    }

    /**
     * @return Fancy SC4 Emojis for Civitas
     */
    public static String TFSC4Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(tf_4_1)
                .appendCodePoint(0x2060)
                .append(tf_4_2)
                .appendCodePoint(0x2060)
                .append(tf_4_3)
                .appendCodePoint(0x2060)
                .append(tf_4_4);
        return sb.toString();
    }

    /**
     * @return Fancy SC5 Emojis for Amicus
     */
    public static String TFSC5Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(tf_5_1)
                .appendCodePoint(0x2060)
                .append(tf_5_2)
                .appendCodePoint(0x2060)
                .append(tf_5_3)
                .appendCodePoint(0x2060)
                .append(tf_5_4);
        return sb.toString();
    }

    /**
     * @return Fancy SC6 Emojis for Calamitas
     */
    public static String TFSC6Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(tf_6_1)
                .appendCodePoint(0x2060)
                .append(tf_6_2)
                .appendCodePoint(0x2060)
                .append(tf_6_3)
                .appendCodePoint(0x2060)
                .append(tf_6_4)
                .appendCodePoint(0x2060)
                .append(tf_6_5)
                .appendCodePoint(0x2060)
                .append(tf_6_6);
        return sb.toString();
    }

    /**
     * @return Fancy SC7 Emojis for Magus
     */
    public static String TFSC7Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(tf_7_1)
                .appendCodePoint(0x2060)
                .append(tf_7_2)
                .appendCodePoint(0x2060)
                .append(tf_7_3)
                .appendCodePoint(0x2060)
                .append(tf_7_4);
        return sb.toString();
    }

    /**
     * @return Fancy SC8 Emojis for Aeterna
     */
    public static String TFSC8Mention() {
        StringBuilder sb = new StringBuilder();
        sb.append(tf_8_1)
                .appendCodePoint(0x2060)
                .append(tf_8_2)
                .appendCodePoint(0x2060)
                .append(tf_8_3)
                .appendCodePoint(0x2060)
                .append(tf_8_4)
                .appendCodePoint(0x2060)
                .append(tf_8_5);
        return sb.toString();
    }

    public static TI4Emoji getACEmoji(Game game) {
        if (game != null && game.isTwilightsFallMode()) {
            return TF_Action_Card;
        }
        return ActionCard;
    }

    public static TI4Emoji getACEmoji(Player player) {
        if (player != null) {
            return getACEmoji(player.getGame());
        }
        return ActionCard;
    }

    @Override
    public String toString() {
        return emojiString();
    }
}
