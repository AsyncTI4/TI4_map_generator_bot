package ti4.service.emoji;

public enum MiltyDraftEmojis implements TI4Emoji {
    sliceUnpicked, //
    sliceA,
    sliceB,
    sliceC,
    sliceD,
    sliceE, //
    sliceF,
    sliceG,
    sliceH,
    sliceI,
    sliceJ, //
    sliceK,
    sliceL,
    sliceM,
    sliceN,
    sliceO, //
    sliceP,
    sliceQ,
    sliceR,
    sliceS,
    sliceT, //
    sliceU,
    sliceV,
    sliceW,
    sliceX,
    sliceY, //
    sliceZ,

    // Positions
    positionUnpicked, //
    position1,
    position2,
    position3,
    position4, //
    position5,
    position6,
    position7,
    position8, //
    position9,
    position10,
    position11,
    position12;

    @Override
    public String toString() {
        return emojiString();
    }

    public static TI4Emoji getMiltyDraftEmoji(int ord) {
        return getMiltyDraftEmoji(Integer.toString(ord));
    }

    public static TI4Emoji getMiltyDraftEmoji(String ord) {
        if (ord == null) return sliceUnpicked;
        return switch (ord.toLowerCase()) {
            case "1", "a" -> sliceA;
            case "2", "b" -> sliceB;
            case "3", "c" -> sliceC;
            case "4", "d" -> sliceD;
            case "5", "e" -> sliceE;
            case "6", "f" -> sliceF;
            case "7", "g" -> sliceG;
            case "8", "h" -> sliceH;
            case "9", "i" -> sliceI;
            case "10", "j" -> sliceJ;
            case "11", "k" -> sliceK;
            case "12", "l" -> sliceL;
            case "13", "m" -> sliceM;
            case "14", "n" -> sliceN;
            case "15", "o" -> sliceO;
            case "16", "p" -> sliceP;
            case "17", "q" -> sliceQ;
            case "18", "r" -> sliceR;
            case "19", "s" -> sliceS;
            case "20", "t" -> sliceT;
            case "21", "u" -> sliceU;
            case "22", "v" -> sliceV;
            case "23", "w" -> sliceW;
            case "24", "x" -> sliceX;
            case "25", "y" -> sliceY;
            case "26", "z" -> sliceZ;
            default -> sliceUnpicked;
        };
    }

    public static TI4Emoji getSpeakerPickEmoji(int ord) {
        return switch (ord) {
            case 1 -> position1;
            case 2 -> position2;
            case 3 -> position3;
            case 4 -> position4;
            case 5 -> position5;
            case 6 -> position6;
            case 7 -> position7;
            case 8 -> position8;
            case 9 -> position9;
            case 10 -> position10;
            case 11 -> position11;
            case 12 -> position12;
            default -> positionUnpicked;
        };
    }
}
