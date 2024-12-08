package ti4.service.emoji;

public enum StratCardEmojis implements TI4Emoji {

    // Normal Strategy Cards
    SC1, SC1Back, // Leadership
    SC2, SC2Back, // Diplomacy
    SC3, SC3Back, // Politics
    SC4, SC4Back, // Construction
    SC5, SC5Back, // Trade
    SC6, SC6Back, // Warfare
    SC7, SC7Back, // Technology
    SC8, SC8Back, // Imperial

    // Strat Card Pings
    sc_1_1, sc_1_2, sc_1_3, sc_1_4, sc_1_5, sc_1_6, // Leadership
    sc_2_1, sc_2_2, sc_2_3, sc_2_4, sc_2_5, sc_2_6, // Diplomacy
    sc_3_1, sc_3_2, sc_3_3, sc_3_4, sc_3_5, // Politics
    sc_4_1, sc_4_2, sc_4_3, sc_4_4, sc_4_5, sc_4_6, sc_4_7, // Construction
    sc_5_1, sc_5_2, sc_5_3, sc_5_4, // Trade
    sc_6_1, sc_6_2, sc_6_3, sc_6_4, sc_6_5, // Warfare
    sc_7_1, sc_7_2, sc_7_3, sc_7_4, sc_7_5, sc_7_6, sc_7_7, // Technology
    sc_8_1, sc_8_2, sc_8_3, sc_8_4, sc_8_5; // Imperial

    @Override
    public String toString() {
        return emojiString();
    }

    // Full Mentions
    public static String SC1Mention() {
        return sc_1_1.toString()
            + sc_1_2.toString()
            + sc_1_3.toString()
            + sc_1_4.toString()
            + sc_1_5.toString()
            + sc_1_6.toString();
    }

    public static String SC2Mention() {
        return sc_2_1.toString()
            + sc_2_2.toString()
            + sc_2_3.toString()
            + sc_2_4.toString()
            + sc_2_5.toString()
            + sc_2_6.toString();
    }

    public static String SC3Mention() {
        return sc_3_1.toString()
            + sc_3_2.toString()
            + sc_3_3.toString()
            + sc_3_4.toString()
            + sc_3_5.toString();
    }

    public static String SC4Mention() {
        return sc_4_1.toString()
            + sc_4_2.toString()
            + sc_4_3.toString()
            + sc_4_4.toString()
            + sc_4_5.toString()
            + sc_4_6.toString()
            + sc_4_7.toString();
    }

    public static String SC5Mention() {
        return sc_5_1.toString()
            + sc_5_2.toString()
            + sc_5_3.toString()
            + sc_5_4.toString();
    }

    public static String SC6Mention() {
        return sc_6_1.toString()
            + sc_6_2.toString()
            + sc_6_3.toString()
            + sc_6_4.toString()
            + sc_6_5.toString();
    }

    public static String SC7Mention() {
        return sc_7_1.toString()
            + sc_7_2.toString()
            + sc_7_3.toString()
            + sc_7_4.toString()
            + sc_7_5.toString()
            + sc_7_6.toString()
            + sc_7_7.toString();
    }

    public static String SC8Mention() {
        return sc_8_1.toString()
            + sc_8_2.toString()
            + sc_8_3.toString()
            + sc_8_4.toString()
            + sc_8_5.toString();
    }
}
