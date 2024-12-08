package ti4.service.emoji;

public enum MiltyDraftEmojis implements TI4Emoji {
    sliceUnpicked, //
    sliceA, sliceB, sliceC, sliceD, sliceE, //
    sliceF, sliceG, sliceH, sliceI, sliceJ, //
    sliceK, sliceL, sliceM, sliceN, sliceO, //
    sliceP, sliceQ, sliceR, sliceS, sliceT, //
    sliceU, sliceV, sliceW, sliceX, sliceY, //
    sliceZ,

    // Positions
    positionUnpicked, //
    position1, position2, position3, position4, //
    position5, position6, position7, position8, //
    position9, position10, position11, position12;

    @Override
    public String toString() {
        return emojiString();
    }

    public static String getMiltyDraftEmoji(int ord) {
        return switch (ord) {
            case 1 -> sliceA.toString();
            case 2 -> sliceB.toString();
            case 3 -> sliceC.toString();
            case 4 -> sliceD.toString();
            case 5 -> sliceE.toString();
            case 6 -> sliceF.toString();
            case 7 -> sliceG.toString();
            case 8 -> sliceH.toString();
            case 9 -> sliceI.toString();
            case 10 -> sliceJ.toString();
            case 11 -> sliceK.toString();
            case 12 -> sliceL.toString();
            case 13 -> sliceM.toString();
            case 14 -> sliceN.toString();
            case 15 -> sliceO.toString();
            case 16 -> sliceP.toString();
            case 17 -> sliceQ.toString();
            case 18 -> sliceR.toString();
            case 19 -> sliceS.toString();
            case 20 -> sliceT.toString();
            case 21 -> sliceU.toString();
            case 22 -> sliceV.toString();
            case 23 -> sliceW.toString();
            case 24 -> sliceX.toString();
            case 25 -> sliceY.toString();
            case 26 -> sliceZ.toString();
            default -> sliceUnpicked.toString();
        };
    }

    public static String getMiltyDraftEmoji(String ord) {
        if (ord == null) return sliceUnpicked.toString();
        return switch (ord.toLowerCase()) {
            case "1", "a" -> sliceA.toString();
            case "2", "b" -> sliceB.toString();
            case "3", "c" -> sliceC.toString();
            case "4", "d" -> sliceD.toString();
            case "5", "e" -> sliceE.toString();
            case "6", "f" -> sliceF.toString();
            case "7", "g" -> sliceG.toString();
            case "8", "h" -> sliceH.toString();
            case "9", "i" -> sliceI.toString();
            case "10", "j" -> sliceJ.toString();
            case "11", "k" -> sliceK.toString();
            case "12", "l" -> sliceL.toString();
            case "13", "m" -> sliceM.toString();
            case "14", "n" -> sliceN.toString();
            case "15", "o" -> sliceO.toString();
            case "16", "p" -> sliceP.toString();
            case "17", "q" -> sliceQ.toString();
            case "18", "r" -> sliceR.toString();
            case "19", "s" -> sliceS.toString();
            case "20", "t" -> sliceT.toString();
            case "21", "u" -> sliceU.toString();
            case "22", "v" -> sliceV.toString();
            case "23", "w" -> sliceW.toString();
            case "24", "x" -> sliceX.toString();
            case "25", "y" -> sliceY.toString();
            case "26", "z" -> sliceZ.toString();
            default -> sliceUnpicked.toString();
        };
    }

    public static String getSpeakerPickEmoji(int ord) {
        return switch (ord) {
            case 1 -> position1.toString();
            case 2 -> position2.toString();
            case 3 -> position3.toString();
            case 4 -> position4.toString();
            case 5 -> position5.toString();
            case 6 -> position6.toString();
            case 7 -> position7.toString();
            case 8 -> position8.toString();
            case 9 -> position9.toString();
            case 10 -> position10.toString();
            case 11 -> position11.toString();
            case 12 -> position12.toString();
            default -> positionUnpicked.toString();
        };
    }

}
