package ti4.service.emoji;

import ti4.image.Mapper;
import ti4.model.ColorModel;

public enum ColorEmojis implements TI4Emoji {

    // Colors
    black, bloodred, blue, brown, chocolate, chrome, rainbow, rose, emerald, ethereal, forest, gold, gray, green, lavender, //
    lightgray, lime, navy, orange, orca, petrol, pink, purple, red, spring, sunset, tan, teal, turquoise, yellow, //
    splitbloodred, splitblue, splitchocolate, splitemerald, splitgold, splitgreen, splitlime, splitnavy, splitorange, //
    splitpetrol, splitpink, splitpurple, splitrainbow, splitred, splittan, splitteal, splitturquoise, splityellow, riftset; //

    public static String getColorEmojiWithName(String color) {
        ColorModel model = Mapper.getColor(color);
        if (model != null)
            return getColorEmoji(color) + " **" + model.getName() + "**";
        return getColorEmoji(color) + " " + color;
    }

    public static TI4Emoji getColorEmoji(String color) {
        return switch (color) {
            case "gry", "gray" -> gray;
            case "blk", "black" -> black;
            case "blu", "blue" -> blue;
            case "grn", "green" -> green;
            case "org", "orange" -> orange;
            case "pnk", "pink" -> pink;
            case "ppl", "purple" -> purple;
            case "red" -> red;
            case "ylw", "yellow" -> yellow;
            case "ptr", "petrol" -> petrol;
            case "bwn", "brown" -> brown;
            case "tan" -> tan;
            case "frs", "forest" -> forest;
            case "crm", "chrome" -> chrome;
            case "sns", "sunset" -> sunset;
            case "tqs", "turquoise" -> turquoise;
            case "gld", "gold" -> gold;
            case "lgy", "lightgray" -> lightgray;
            case "tea", "teal" -> teal;
            case "bld", "bloodred" -> bloodred;
            case "eme", "emerald" -> emerald;
            case "nvy", "navy" -> navy;
            case "rse", "rose" -> rose;
            case "lme", "lime" -> lime;
            case "lvn", "lavender" -> lavender;
            case "spr", "spring" -> spring;
            case "chk", "chocolate" -> chocolate;
            case "rbw", "rainbow" -> rainbow;
            case "eth", "ethereal" -> ethereal;
            case "orca" -> orca;
            case "splitred" -> splitred;
            case "splitblu", "splitblue" -> splitblue;
            case "splitgrn", "splitgreen" -> splitgreen;
            case "splitppl", "splitpurple" -> splitpurple;
            case "splitorg", "splitorange" -> splitorange;
            case "splitylw", "splityellow" -> splityellow;
            case "splitpnk", "splitpink" -> splitpink;
            case "splitgld", "splitgold" -> splitgold;
            case "splitlme", "splitlime" -> splitlime;
            case "splittan" -> splittan;
            case "splittea", "splitteal" -> splitteal;
            case "splittqs", "splitturquoise" -> splitturquoise;
            case "splitbld", "splitbloodred" -> splitbloodred;
            case "splitchk", "splitchocolate" -> splitchocolate;
            case "spliteme", "splitemerald" -> splitemerald;
            case "splitnvy", "splitnavy" -> splitnavy;
            case "splitptr", "splitpetrol" -> splitpetrol;
            case "splitrbw", "splitrainbow" -> splitrainbow;
            case "ero", "riftset" -> riftset;

            default -> TI4Emoji.getRandomGoodDog();
        };
    }

    @Override
    public String toString() {
        return emojiString();
    }
}
