package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import ti4.model.ColorModel;

public class ColourHelper {

    public static List<ColorModel> sortColours(String factionId, List<ColorModel> colours) {
        List<ColorModel> newcolours = new ArrayList<>(colours);
        // sort by name for deterministic sorting
        newcolours.sort(Comparator.comparing(ColorModel::getAlias));
        // sort by colour
        newcolours.sort((c1, c2) -> colourValue(factionId, c2) - colourValue(factionId, c1));
        // for each "page" of colours, randomise
        // this process means widows and orphans will never cross page boundaries
        for (int x = 0; x < newcolours.size(); x += 22) {
            Collections.shuffle(newcolours.subList(x, Math.min(x + 22, newcolours.size())));
        }
        // resort by colour
        newcolours.sort((c1, c2) -> colourValue(factionId, c2) - colourValue(factionId, c1));
        return newcolours;
    }

    private static int colourValue(String factionId, ColorModel colour) {
        return colourValue(factionId, colour.getAlias());
    }

    private static int colourValue(String factionId, String colour) {
        if (colour.startsWith("split")) {
            return colourValue(factionId, colour.replace("split", "")) - 20;
        }
        switch (factionId) {
            case "arborec", "pi_arborec", "miltymodarborec":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 24;
                    case "orca":
                        return 4;
                    case "red", "rst", "bld":
                        return 25;
                    case "org", "cpr", "pch":
                        return 23;
                    case "tan", "bwn", "chk":
                        return 33;
                    case "spr", "ylw", "gld", "crm":
                        return 26;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 39;
                    case "tea", "ptr", "eth", "blu", "nvy", "rbw", "rse", "pnk":
                        return 21;
                    case "lvn", "ppl", "plm", "sns":
                        return 20;
                }
            case "ghost", "pi_ghost", "miltymodghost":
                switch (colour) {
                    case "lgy", "gry", "blk", "lvn", "ppl", "sns":
                        return 27;
                    case "orca":
                        return 7;
                    case "red", "rst", "bld":
                        return 22;
                    case "org":
                        return 20;
                    case "tan", "bwn", "chk", "lme", "grn", "tqs", "frs", "eme", "spr", "ylw", "gld", "crm":
                        return 21;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 40;
                    case "rse", "pnk":
                        return 24;
                    case "rbw":
                        return 33;
                }
            case "hacan", "pi_hacan", "miltymodhacan":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 20;
                    case "orca":
                        return 0;
                    case "red", "rst", "bld":
                        return 26;
                    case "org", "cpr", "pch":
                        return 31;
                    case "tan", "bwn", "chk":
                        return 30;
                    case "spr", "ylw", "gld", "crm":
                        return 38;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme", "lvn", "ppl", "sns":
                        return 23;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 24;
                    case "rse", "pnk":
                        return 22;
                    case "rbw":
                        return 29;
                }
            case "jolnar", "pi_jolnar", "miltymodjolnar":
                switch (colour) {
                    case "lgy", "gry", "blk", "red", "bld":
                        return 23;
                    case "orca":
                        return 3;
                    case "org", "cpr", "pch":
                        return 22;
                    case "tan", "bwn", "chk":
                        return 21;
                    case "spr", "ylw", "gld", "crm":
                        return 24;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme", "rse", "pnk":
                        return 27;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 36;
                    case "lvn", "ppl", "plm", "sns":
                        return 34;
                    case "rbw":
                        return 25;
                }
            case "l1z1x", "lazax", "pi_l1z1x", "miltymodl1z1x":
                switch (colour) {
                    case "lgy", "gry", "blk", "red", "bld":
                        return 34;
                    case "orca":
                        return 14;
                    case "org", "cpr", "pch":
                        return 23;
                    case "tan", "bwn", "chk":
                        return 27;
                    case "spr", "ylw", "gld", "crm":
                        return 25;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 22;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 33;
                    case "lvn", "ppl", "plm", "sns":
                        return 26;
                    case "rse", "pnk":
                        return 20;
                    case "rbw":
                        return 21;
                }
            case "letnev", "pi_letnev", "miltymodletnev":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 34;
                    case "orca":
                        return 14;
                    case "red", "rst", "bld":
                        return 36;
                    case "org", "cpr", "pch":
                        return 24;
                    case "tan", "bwn", "chk", "tea", "ptr", "eth", "blu", "nvy":
                        return 27;
                    case "spr", "ylw", "gld", "crm", "lvn", "ppl", "sns":
                        return 23;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 20;
                    case "rse", "pnk", "rbw":
                        return 21;
                }
            case "mentak", "pi_mentak", "miltymodmentak":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 28;
                    case "orca":
                        return 8;
                    case "red", "rst", "bld", "lvn", "ppl", "sns":
                        return 24;
                    case "org", "cpr", "pch":
                        return 36;
                    case "tan", "bwn", "chk":
                        return 30;
                    case "spr", "ylw", "gld", "crm":
                        return 34;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme", "tea", "ptr", "eth", "blu", "nvy":
                        return 21;
                    case "rse", "pnk":
                        return 23;
                    case "rbw":
                        return 25;
                }
            case "muaat", "pi_muaat", "miltymodmuaat":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 25;
                    case "orca":
                        return 5;
                    case "red", "rst", "bld":
                        return 37;
                    case "org", "cpr", "pch":
                        return 36;
                    case "tan", "bwn", "chk":
                        return 24;
                    case "spr", "ylw", "gld", "crm":
                        return 30;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme", "tea", "ptr", "eth", "blu", "nvy":
                        return 20;
                    case "lvn", "ppl", "plm", "sns":
                        return 21;
                    case "rse", "pnk":
                        return 23;
                    case "rbw":
                        return 29;
                }
            case "naalu", "pi_naalu", "miltymodnaalu":
                switch (colour) {
                    case "lgy", "gry", "blk", "tea", "ptr", "eth", "blu", "nvy":
                        return 25;
                    case "orca":
                        return 5;
                    case "red", "rst", "bld", "lvn", "ppl", "sns":
                        return 22;
                    case "org", "cpr", "pch":
                        return 26;
                    case "tan", "bwn", "chk":
                        return 27;
                    case "spr", "ylw", "gld", "crm", "rbw":
                        return 29;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 37;
                    case "rse", "pnk":
                        return 21;
                }
            case "nekro", "pi_nekro", "miltymodnekro":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 34;
                    case "orca":
                        return 14;
                    case "red", "rst", "bld":
                        return 40;
                    case "tea", "ptr", "eth", "blu", "nvy", "lme", "grn", "tqs", "frs", "eme":
                        return 20;
                    case "org", "rse", "pnk", "lvn", "ppl", "sns":
                        return 22;
                    case "tan", "bwn", "chk":
                        return 24;
                    case "spr", "ylw", "gld", "crm", "rbw":
                        return 21;
                }
            case "saar", "pi_saar", "miltymodsaar":
                switch (colour) {
                    case "lgy", "gry", "blk", "tea", "ptr", "eth", "blu", "nvy":
                        return 27;
                    case "orca":
                        return 7;
                    case "red", "rst", "bld":
                        return 24;
                    case "org", "cpr", "pch":
                        return 32;
                    case "tan", "bwn", "chk":
                        return 36;
                    case "spr", "ylw", "gld", "crm":
                        return 33;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 29;
                    case "lvn", "ppl", "plm", "sns":
                        return 22;
                    case "rse", "pnk":
                        return 20;
                    case "rbw":
                        return 25;
                }
            case "sardakk", "pi_sardakk", "miltymodsardakk":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 35;
                    case "orca":
                        return 15;
                    case "red", "rst", "bld":
                        return 38;
                    case "org", "cpr", "pch":
                        return 25;
                    case "tan", "bwn", "chk":
                        return 30;
                    case "spr", "ylw", "gld", "crm":
                        return 24;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 26;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 20;
                    case "lvn", "ppl", "plm", "sns", "rbw", "rse", "pnk":
                        return 21;
                }
            case "sol", "pi_sol", "miltymodsol":
                switch (colour) {
                    case "lgy", "gry", "blk", "rse", "pnk", "org", "red", "bld":
                        return 21;
                    case "orca":
                        return 1;
                    case "tan", "bwn", "chk":
                        return 27;
                    case "spr", "ylw", "gld", "crm":
                        return 32;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme", "rbw":
                        return 29;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 37;
                    case "lvn", "ppl", "plm", "sns":
                        return 22;
                }
            case "winnu", "pi_winnu", "miltymodwinnu":
                switch (colour) {
                    case "lgy", "gry", "blk", "lme", "grn", "tqs", "frs", "eme":
                        return 21;
                    case "orca":
                        return 1;
                    case "red", "rst", "bld":
                        return 23;
                    case "org", "cpr", "pch":
                        return 28;
                    case "tan", "bwn", "chk":
                        return 24;
                    case "spr", "ylw", "gld", "crm":
                        return 34;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 25;
                    case "lvn", "ppl", "plm", "sns":
                        return 35;
                    case "rse", "pnk":
                        return 29;
                    case "rbw":
                        return 33;
                }
            case "xxcha", "pi_xxcha", "miltymodxxcha":
                switch (colour) {
                    case "lgy", "gry", "blk", "rse", "pnk", "lvn", "ppl", "sns", "org":
                        return 21;
                    case "orca":
                        return 1;
                    case "red", "rst", "bld":
                        return 20;
                    case "tan", "bwn", "chk", "spr", "ylw", "gld", "crm":
                        return 27;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 35;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 30;
                    case "rbw":
                        return 29;
                }
            case "yin", "pi_yin", "miltymodyin":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 31;
                    case "orca":
                        return 11;
                    case "red", "rst", "bld":
                        return 23;
                    case "org", "tea", "ptr", "eth", "blu", "nvy":
                        return 21;
                    case "tan", "bwn", "chk":
                        return 27;
                    case "spr", "ylw", "gld", "crm", "rse", "pnk":
                        return 28;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme", "rbw":
                        return 25;
                    case "lvn", "ppl", "plm", "sns":
                        return 35;
                }
            case "yssaril", "pi_yssaril", "miltymodyssaril":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 26;
                    case "orca":
                        return 6;
                    case "red", "rst", "bld":
                        return 24;
                    case "org", "lvn", "ppl", "sns":
                        return 23;
                    case "tan", "bwn", "chk":
                        return 30;
                    case "spr", "ylw", "gld", "crm":
                        return 27;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 36;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 22;
                    case "rse", "pnk":
                        return 20;
                    case "rbw":
                        return 21;
                }
            case "argent", "pi_argent":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 23;
                    case "orca":
                        return 3;
                    case "red", "rst", "bld":
                        return 28;
                    case "org", "cpr", "pch":
                        return 39;
                    case "tan", "bwn", "chk":
                        return 30;
                    case "spr", "ylw", "gld", "crm":
                        return 29;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme", "rbw":
                        return 25;
                    case "tea", "ptr", "eth", "blu", "nvy", "rse", "pnk", "lvn", "ppl", "sns":
                        return 21;
                }
            case "cabal", "pi_cabal":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 30;
                    case "orca":
                        return 10;
                    case "red", "rst", "bld":
                        return 40;
                    case "lme", "grn", "tqs", "frs", "eme":
                        return 20;
                    case "org", "rbw":
                        return 25;
                    case "tan", "bwn", "chk":
                        return 27;
                    case "spr", "ylw", "gld", "crm":
                        return 21;
                    case "tea", "ptr", "eth", "blu", "nvy", "lvn", "ppl", "sns":
                        return 24;
                    case "rse", "pnk":
                        return 26;
                }
            case "empyrean", "pi_empyrean":
                switch (colour) {
                    case "lgy", "gry", "blk", "tea", "ptr", "eth", "blu", "nvy":
                        return 29;
                    case "orca":
                        return 9;
                    case "red", "rst", "bld":
                        return 26;
                    case "org", "lme", "grn", "tqs", "frs", "eme", "tan", "bwn", "chk":
                        return 21;
                    case "spr", "ylw", "gld", "crm":
                        return 20;
                    case "lvn", "ppl", "plm", "sns":
                        return 40;
                    case "rse", "pnk":
                        return 28;
                    case "rbw":
                        return 33;
                }
            case "mahact", "pi_mahact":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 28;
                    case "orca":
                        return 8;
                    case "red", "rst", "bld", "org":
                        return 26;
                    case "tan", "bwn", "chk", "rse", "pnk", "lme", "grn", "tqs", "frs", "eme":
                        return 24;
                    case "spr", "ylw", "gld", "crm":
                        return 39;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 25;
                    case "lvn", "ppl", "plm", "sns":
                        return 32;
                    case "rbw":
                        return 33;
                }
            case "nomad", "pi_nomad":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 25;
                    case "orca":
                        return 5;
                    case "red", "rst", "bld":
                        return 22;
                    case "org", "tan", "bwn", "chk":
                        return 21;
                    case "spr", "ylw", "gld", "crm":
                        return 23;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 26;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 36;
                    case "lvn", "ppl", "plm", "sns", "rbw":
                        return 33;
                    case "rse", "pnk":
                        return 27;
                }
            case "naaz", "pi_naaz":
                switch (colour) {
                    case "lgy", "gry", "blk", "lvn", "ppl", "sns":
                        return 24;
                    case "orca":
                        return 4;
                    case "red", "rst", "bld", "org":
                        return 23;
                    case "tan", "bwn", "chk":
                        return 30;
                    case "spr", "ylw", "gld", "crm":
                        return 26;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 39;
                    case "tea", "ptr", "eth", "blu", "nvy", "rse", "pnk":
                        return 21;
                    case "rbw":
                        return 29;
                }
            case "titans", "pi_titans":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 22;
                    case "orca":
                        return 2;
                    case "red", "rst", "bld", "rbw", "spr", "ylw", "gld", "crm":
                        return 25;
                    case "org", "lme", "grn", "tqs", "frs", "eme":
                        return 24;
                    case "tan", "bwn", "chk":
                        return 27;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 23;
                    case "lvn", "ppl", "plm", "sns":
                        return 26;
                    case "rse", "pnk":
                        return 40;
                }
            case "keleresa", "keleresm", "keleresx", "keleresplus", "pi_keleresm", "pi_keleresx", "pi_keleresa":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 33;
                    case "orca":
                        return 13;
                    case "red", "rst", "bld":
                        return 21;
                    case "org", "cpr", "pch":
                        return 23;
                    case "tan", "bwn", "chk":
                        return 30;
                    case "spr", "ylw", "gld", "crm":
                        return 27;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 26;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 32;
                    case "lvn", "ppl", "plm", "sns":
                        return 35;
                    case "rse", "pnk":
                        return 34;
                    case "rbw":
                        return 37;
                }
            // Discordant Stars
            case "augers":
                switch (colour) {
                    case "lvn", "ppl", "plm", "sns":
                        return 35;
                }
            case "axis":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 35;
                    case "orca":
                        return 15;
                    case "red", "rst", "bld":
                        return 30;
                }
            case "bentor":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 30;
                    case "orca":
                        return 10;
                    case "lvn", "ppl", "plm", "sns":
                        return 35;
                }
            case "celdauri":
                switch (colour) {
                    case "spr", "ylw", "gld", "crm":
                        return 35;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 30;
                }
            case "cheiran":
                switch (colour) {
                    case "tan", "bwn", "chk":
                        return 35;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 30;
                }
            case "cymiae":
                switch (colour) {
                    case "org", "cpr", "pch":
                        return 35;
                }
            case "dihmohn":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 30;
                    case "orca":
                        return 10;
                    case "lvn", "ppl", "plm", "sns":
                        return 35;
                }
            case "edyn":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 35;
                    case "orca":
                        return 15;
                    case "spr", "ylw", "gld", "crm":
                        return 30;
                }
            case "florzen":
                switch (colour) {
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 35;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 30;
                }
            case "freesystems":
                switch (colour) {
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 30;
                    case "lvn", "ppl", "plm", "sns":
                        return 35;
                }
            case "ghemina":
                switch (colour) {
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 35;
                }
            case "ghoti":
                switch (colour) {
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 30;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 35;
                }
            case "gledge":
                switch (colour) {
                    case "org", "cpr", "pch":
                        return 35;
                    case "tan", "bwn", "chk":
                        return 30;
                }
            case "khrask":
                switch (colour) {
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 35;
                    case "org", "cpr", "pch":
                        return 30;
                }
            case "kjalengard":
                switch (colour) {
                    case "lvn", "ppl", "plm", "sns":
                        return 35;
                    case "red", "rst", "bld":
                        return 30;
                }
            case "kollecc":
                switch (colour) {
                    case "tan", "bwn", "chk":
                        return 35;
                    case "lgy", "gry", "blk":
                        return 30;
                    case "orca":
                        return 10;
                }
            case "kolume":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 35;
                    case "orca":
                        return 15;
                    case "tan", "bwn", "chk":
                        return 30;
                }
            case "kortali":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 35;
                    case "orca":
                        return 15;
                }
            case "kyro":
                switch (colour) {
                    case "spr", "ylw", "gld", "crm":
                        return 35;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 30;
                }
            case "lanefir":
                switch (colour) {
                    case "org", "cpr", "pch":
                        return 35;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 30;
                }
            case "lizho":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 30;
                    case "orca":
                        return 10;
                    case "tan", "bwn", "chk":
                        return 35;
                }
            case "mirveda":
                switch (colour) {
                    case "lvn", "ppl", "plm", "sns":
                        return 35;
                    case "rse", "pnk":
                        return 30;
                }
            case "mortheus":
                switch (colour) {
                    case "tea", "ptr", "eth", "blu", "nvy", "rse", "pnk":
                        return 30;
                    case "rbw":
                        return 35;
                }
            case "mykomentori":
                switch (colour) {
                    case "lme", "grn", "tpl", "tqs", "frs", "eme", "rse", "pnk":
                        return 30;
                    case "rbw":
                        return 35;
                }
            case "nivyn":
                switch (colour) {
                    case "lgy", "gry", "blk":
                        return 30;
                    case "orca":
                        return 10;
                    case "spr", "ylw", "gld", "crm":
                        return 35;
                }
            case "nokar":
                switch (colour) {
                    case "org", "cpr", "pch":
                        return 35;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 30;
                }
            case "olradin":
                switch (colour) {
                    case "org", "cpr", "pch":
                        return 35;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 30;
                }
            case "rohdhna":
                switch (colour) {
                    case "lvn", "ppl", "plm", "sns":
                        return 35;
                }
            case "tnelis":
                switch (colour) {
                    case "red", "rst", "bld":
                        return 30;
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 35;
                }
            case "vaden":
                switch (colour) {
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 35;
                }
            case "vaylerian":
                switch (colour) {
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 35;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 30;
                }
            case "veldyr":
                switch (colour) {
                    case "lme", "grn", "tpl", "tqs", "frs", "eme":
                        return 30;
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 35;
                }
            case "zealots":
                switch (colour) {
                    case "tea", "ptr", "eth", "blu", "nvy":
                        return 35;
                }
            case "zelian":
                switch (colour) {
                    case "red", "rst", "bld":
                        return 35;
                }
            // other homebrew
            case "drahn":
                switch (colour) {
                    case "red", "rst", "bld":
                        return 35;
                    case "spr", "ylw", "gld", "crm":
                        return 30;
                }
        }
        if ("orca".equals(colour)) {
            return 5;
        }
        return 15;
    }
}
