package ti4.service.emoji;

import ti4.image.Mapper;
import ti4.model.ColorModel;

public enum ColorEmojis implements TI4Emoji {

    // Colors
    black, bloodred, blue, brown, chocolate, chrome, rainbow, sherbet, rose, emerald, ethereal, forest, gold, gray, green, lavender, //
    lightgray, lime, navy, orange, orca, petrol, pink, purple, red, spring, sunset, tan, teal, turquoise, yellow, //
    copper, plum, peach, rust, tropical, jupiter, poison, vapourwave, watermelon, checker, paintball, wasp, magma, glacier, plaid, harlequin, //
    splitbloodred, splitblue, splitchocolate, splitemerald, splitgold, splitgreen, splitlime, splitnavy, splitorange, //
    splitpetrol, splitpink, splitpurple, splitrainbow, splitred, splittan, splitteal, splitturquoise, splityellow, riftset; //

    public static String getColorEmojiWithName(String color) {
        if (getColorEmojiWithNameLegacy(color) != null) {
            return getColorEmojiWithNameLegacy(color);
        }
        ColorModel model = Mapper.getColor(color);
        if (model != null)
            return getColorEmoji(color) + " **" + model.getName() + "**";
        return getColorEmoji(color) + " " + color;
    }

    /**
     * @deprecated TODO: add the proper name to ColorModel data
     */
    @Deprecated
    private static String getColorEmojiWithNameLegacy(String color) {
        return switch (color) {
            case "gry", "gray" -> gray + "**Gray**";
            case "blk", "black" -> black + "**Black**";
            case "blu", "blue" -> blue + "**Blue**";
            case "grn", "green" -> green + "**Green**";
            case "org", "orange" -> orange + "**Orange**";
            case "pnk", "pink" -> pink + "**Pink**";
            case "ppl", "purple" -> purple + "**Purple**";
            case "red" -> red + "**Red**";
            case "ylw", "yellow" -> yellow + "**Yellow**";
            case "ptr", "petrol" -> petrol + "**Petrol**";
            case "bwn", "brown" -> brown + "**Brown**";
            case "tan" -> tan + "**Tan**";
            case "frs", "forest" -> forest + "**Forest**";
            case "crm", "chrome" -> chrome + "**Chrome**";
            case "sns", "sunset" -> sunset + "**Sunset**";
            case "mgm", "magma" -> magma + "**Magma**";
            case "gcr", "glacier" -> glacier + "**Glacier**";
            case "tqs", "turquoise" -> turquoise + "**Turquoise**";
            case "gld", "gold" -> gold + "**Gold**";
            case "lgy", "lightgray" -> lightgray + "**LightGray**";
            case "tea", "teal" -> teal + "**Teal**";
            case "bld", "bloodred" -> bloodred + "**BloodRed**";
            case "eme", "emerald" -> emerald + "**Emerald**";
            case "nvy", "navy" -> navy + "**Navy**";
            case "rse", "rose" -> rose + "**Rose**";
            case "lme", "lime" -> lime + "**Lime**";
            case "lvn", "lavender" -> lavender + "**Lavender**";
            case "spr", "spring" -> spring + "**Spring**";
            case "chk", "chocolate" -> chocolate + "**Chocolate**";
            case "rbw", "rainbow" -> rainbow + "**Rainbow**";
            case "sbt", "sherbet" -> sherbet + "**Sherbet**";
            case "eth", "ethereal" -> ethereal + "**Ethereal**";
            case "cpr", "copper" -> copper + "**Copper**";
            case "plm", "plum" -> plum + "**Plum**";
            case "pch", "peach" -> peach + "**Peach**";
            case "rst", "rust" -> rust + "**Rust**";
            case "tpl", "tropical" -> tropical + "**Tropical**";
            case "jpt", "jupiter" -> jupiter + "**Jupiter**";
            case "psn", "poison" -> poison + "**Poison**";
            case "vpw", "vapourwave" -> vapourwave + "**Vapourwave**";
            case "wtm", "watermelon" -> watermelon + "**Watermelon**";
            case "wsp", "wasp" -> wasp + "**Wasp**";
            case "cqr", "checker" -> checker + "**Checker**";
            case "pld", "plaid" -> plaid + "**Plaid**";
            case "hqn", "harlequin" -> harlequin + "**Harlequin**";
            case "ptb", "paintball" -> paintball + "**Paintball**";
            case "orca" -> orca + "**Orca**";
            case "splitred" -> splitred + "**SplitRed**";
            case "splitblu", "splitblue" -> splitblue + "**SplitBlue**";
            case "splitgrn", "splitgreen" -> splitgreen + "**SplitGreen**";
            case "splitppl", "splitpurple" -> splitpurple + "**SplitPurple**";
            case "splitorg", "splitorange" -> splitorange + "**SplitOrange**";
            case "splitylw", "splityellow" -> splityellow + "**SplitYellow**";
            case "splitpnk", "splitpink" -> splitpink + "**SplitPink**";
            case "splitgld", "splitgold" -> splitgold + "**SplitGold**";
            case "splitlme", "splitlime" -> splitlime + "**SplitLime**";
            case "splittan" -> splittan + "**SplitTan**";
            case "splittea", "splitteal" -> splitteal + "**SplitTeal**";
            case "splittqs", "splitturquoise" -> splitturquoise + "**SplitTurquoise**";
            case "splitbld", "splitbloodred" -> splitbloodred + "**SplitBloodRed**";
            case "splitchk", "splitchocolate" -> splitchocolate + "**SplitChocolate**";
            case "spliteme", "splitemerald" -> splitemerald + "**SplitEmerald**";
            case "splitnvy", "splitnavy" -> splitnavy + "**SplitNavy**";
            case "splitptr", "splitpetrol" -> splitpetrol + "**SplitPetrol**";
            case "splitrbw", "splitrainbow" -> splitrainbow + "**SplitRainbow**";
            case "ero", "riftset" -> riftset + "**RiftSet**";
            default -> null;
        };
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
            case "mgm", "magma" -> magma;
            case "gcr", "glacier" -> glacier;
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
            case "sbt", "sherbet" -> sherbet;
            case "eth", "ethereal" -> ethereal;
            case "cpr", "copper" -> copper;
            case "plm", "plum" -> plum;
            case "pch", "peach" -> peach;
            case "rst", "rust" -> rust;
            case "tpl", "tropical" -> tropical;
            case "jpt", "jupiter" -> jupiter;
            case "psn", "poison" -> poison;
            case "vpw", "vapourwave" -> vapourwave;
            case "wtm", "watermelon" -> watermelon;
            case "wsp", "wasp" -> wasp;
            case "cqr", "checker" -> checker;
            case "pld", "plaid" -> plaid;
            case "hqn", "harlequin" -> harlequin;
            case "ptb", "paintball" -> paintball;
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
