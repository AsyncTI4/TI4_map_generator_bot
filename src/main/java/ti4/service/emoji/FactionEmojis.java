package ti4.service.emoji;

import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import ti4.helpers.Emojis;

public enum FactionEmojis implements TI4Emoji {

    //base
    Arborec, Ghost, Hacan, Jolnar, L1Z1X, Letnev, Mentak, Muaat, Naalu, Nekro, Saar, Sardakk, Sol, Winnu, Xxcha, Yin, Yssaril,

    // Prophecy of Kings
    Argent, Cabal, Empyrean, Mahact, Naaz, Nomad, Titans, Keleres,

    // Discordant Stars
    augers, axis, bentor, celdauri, cheiran, cymiae, dihmohn, edyn, florzen, freesystems, ghemina, //
    ghoti, gledge, khrask, kjalengard, kollecc, kolume, kortali, kyro, lanefir, lizho, mirveda, mortheus, //
    mykomentori, nivyn, nokar, olradin, rohdhna, tnelis, vaden, vaylerian, veldyr, zealots, zelian, //

    // Franken
    Franken1, Franken2, Franken3, Franken4, Franken5, Franken6, Franken7, Franken8, //
    Franken9, Franken10, Franken11, Franken12, Franken13, Franken14, Franken15, Franken16, //

    // Other (random homebrew)
    Lazax, Neutral, RandomFaction, AdminsFaction, netharii, Drahn, //misc
    Qulane, echoes, enclave, raven, syndicate, terminator; // baldrick

    @Override
    public String toString() {
        return emojiString();
    }

    @NotNull
    public static String getFactionIconFromDiscord(String faction) {
        TI4Emoji emoji = getFactionEmojiFromDiscord(faction);
        if (emoji == null) return Emojis.getRandomizedEmoji(0, null);
        return emoji.toString();
    }

    @Nullable
    public static TI4Emoji getFactionEmojiFromDiscord(String faction) {
        return switch (faction.toLowerCase()) {
            case null -> null;
            case "arborec" -> Arborec;
            case "argent" -> Argent;
            case "cabal" -> Cabal;
            case "empyrean" -> Empyrean;
            case "ghost", "creuss", "ghosts" -> Ghost;
            case "hacan" -> Hacan;
            case "jolnar" -> Jolnar;
            case "l1z1x" -> L1Z1X;
            case "barony", "letnev" -> Letnev;
            case "yssaril" -> Yssaril;
            case "mahact" -> Mahact;
            case "mentak" -> Mentak;
            case "muaat" -> Muaat;
            case "naalu" -> Naalu;
            case "naaz" -> Naaz;
            case "nekro" -> Nekro;
            case "nomad" -> Nomad;
            case "saar" -> Saar;
            case "sardakk" -> Sardakk;
            case "sol" -> Sol;
            case "titans" -> Titans;
            case "winnu" -> Winnu;
            case "xxcha" -> Xxcha;
            case "yin" -> Yin;

            case "lazax" -> Lazax;
            case "neutral" -> Neutral;

            case "keleres", "keleresx", "keleresm", "keleresa" -> Keleres;

            case "augers" -> augers;
            case "axis" -> axis;
            case "bentor" -> bentor;
            case "kyro" -> kyro;
            case "celdauri" -> celdauri;
            case "cheiran" -> cheiran;
            case "cymiae" -> cymiae;
            case "dihmohn" -> dihmohn;
            case "edyn" -> edyn;
            case "florzen" -> florzen;
            case "freesystems" -> freesystems;
            case "ghemina" -> ghemina;
            case "ghoti" -> ghoti;
            case "gledge" -> gledge;
            case "khrask" -> khrask;
            case "kjalengard" -> kjalengard;
            case "kollecc" -> kollecc;
            case "kolume" -> kolume;
            case "kortali" -> kortali;
            case "lanefir" -> lanefir;
            case "lizho" -> lizho;
            case "mirveda" -> mirveda;
            case "mortheus" -> mortheus;
            case "mykomentori" -> mykomentori;
            case "nivyn" -> nivyn;
            case "nokar" -> nokar;
            case "olradin" -> olradin;
            case "rohdhna" -> rohdhna;
            case "tnelis" -> tnelis;
            case "vaden" -> vaden;
            case "vaylerian" -> vaylerian;
            case "veldyr" -> veldyr;
            case "zealots" -> zealots;
            case "zelian" -> zelian;

            case "admins" -> AdminsFaction;
            case "qulane" -> Qulane;

            case "franken1" -> Franken1;
            case "franken2" -> Franken2;
            case "franken3" -> Franken3;
            case "franken4" -> Franken4;
            case "franken5" -> Franken5;
            case "franken6" -> Franken6;
            case "franken7" -> Franken7;
            case "franken8" -> Franken8;
            case "franken9" -> Franken9;
            case "franken10" -> Franken10;
            case "franken11" -> Franken11;
            case "franken12" -> Franken12;
            case "franken13" -> Franken13;
            case "franken14" -> Franken14;
            case "franken15" -> Franken15;
            case "franken16" -> Franken16;

            case "echoes" -> echoes;
            case "enclave" -> enclave;
            case "raven" -> raven;
            case "syndicate" -> syndicate;
            case "terminator" -> terminator;

            case "netharii" -> netharii;
            case "drahn" -> Drahn;
            default -> null;
        };
    }
}
