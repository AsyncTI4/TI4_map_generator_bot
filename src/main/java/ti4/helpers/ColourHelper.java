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
        switch (factionId) {
            case "arborec":
            case "pi_arborec":
            case "miltymodarborec":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 24;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 4;
                    case "red":
                    case "bld":
                        return 25;
                    case "splitred":
                    case "splitbld":
                        return 5;
                    case "org":
                        return 23;
                    case "splitorg":
                        return 3;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 33;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 13;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 26;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 6;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 39;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 19;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy", "rbw", "rse", "pnk":
                        return 21;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy", "splitrbw", "splitrse", "splitpnk":
                        return 1;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 20;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 0;
                }
            case "ghost":
            case "pi_ghost":
            case "miltymodghost":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk", "lvn", "ppl", "sns":
                        return 27;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca", "splitlvn", "splitppl", "splitsns":
                        return 7;
                    case "red":
                    case "bld":
                        return 22;
                    case "splitred":
                    case "splitbld":
                        return 2;
                    case "org", "splittea", "splitptr", "spliteth", "splitblu", "splitnvy":
                        return 20;
                    case "splitorg":
                        return 0;
                    case "tan":
                    case "bwn":
                    case "chk", "lme", "grn", "tqs", "frs", "eme", "spr", "ylw", "gld", "crm":
                        return 21;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk", "splitlme", "splitgrn", "splittqs", "splitfrs", "spliteme", "splitspr", "splitylw", "splitgld", "splitcrm":
                        return 1;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 40;
                    case "rse":
                    case "pnk":
                        return 24;
                    case "splitrse":
                    case "splitpnk":
                        return 4;
                    case "rbw":
                        return 33;
                    case "splitrbw":
                        return 3;
                }
            case "hacan":
            case "pi_hacan":
            case "miltymodhacan":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 20;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 0;
                    case "red":
                    case "bld":
                        return 26;
                    case "splitred":
                    case "splitbld":
                        return 6;
                    case "org":
                        return 31;
                    case "splitorg":
                        return 11;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 30;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 10;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 38;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 18;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme", "lvn", "ppl", "sns":
                        return 23;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme", "splitlvn", "splitppl", "splitsns":
                        return 3;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 24;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 4;
                    case "rse":
                    case "pnk":
                        return 22;
                    case "splitrse":
                    case "splitpnk":
                        return 2;
                    case "rbw":
                        return 29;
                    case "splitrbw":
                        return 9;
                }
            case "jolnar":
            case "pi_jolnar":
            case "miltymodjolnar":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk", "red", "bld":
                        return 23;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca", "splitred", "splitbld":
                        return 3;
                    case "org":
                        return 22;
                    case "splitorg":
                        return 2;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 21;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 1;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 24;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 4;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme", "rse", "pnk":
                        return 27;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme", "splitrse", "splitpnk":
                        return 7;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 36;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 16;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 34;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 14;
                    case "rbw":
                        return 25;
                    case "splitrbw":
                        return 5;
                }
            case "l1z1x":
            case "lazax":
            case "pi_l1z1x":
            case "miltymodl1z1x":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk", "red", "bld":
                        return 34;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca", "splitred", "splitbld":
                        return 14;
                    case "org":
                        return 23;
                    case "splitorg":
                        return 3;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 27;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 7;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 25;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 5;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 22;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 2;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 33;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 13;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 26;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 6;
                    case "rse":
                    case "pnk":
                        return 20;
                    case "splitrse":
                    case "splitpnk":
                        return 0;
                    case "rbw":
                        return 21;
                    case "splitrbw":
                        return 1;
                }
            case "letnev":
            case "pi_letnev":
            case "miltymodletnev":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 34;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 14;
                    case "red":
                    case "bld":
                        return 36;
                    case "splitred":
                    case "splitbld":
                        return 16;
                    case "org":
                        return 24;
                    case "splitorg":
                        return 4;
                    case "tan":
                    case "bwn":
                    case "chk", "tea", "ptr", "eth", "blu", "nvy":
                        return 27;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk", "splittea", "splitptr", "spliteth", "splitblu", "splitnvy":
                        return 7;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm", "lvn", "ppl", "sns":
                        return 23;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm", "splitlvn", "splitppl", "splitsns":
                        return 3;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 20;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 0;
                    case "rse":
                    case "pnk", "rbw":
                        return 21;
                    case "splitrse":
                    case "splitpnk", "splitrbw":
                        return 1;
                }
            case "mentak":
            case "pi_mentak":
            case "miltymodmentak":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 28;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 8;
                    case "red":
                    case "bld", "lvn", "ppl", "sns":
                        return 24;
                    case "splitred":
                    case "splitbld", "splitlvn", "splitppl", "splitsns":
                        return 4;
                    case "org":
                        return 36;
                    case "splitorg":
                        return 16;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 30;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 10;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 34;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 14;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme", "tea", "ptr", "eth", "blu", "nvy":
                        return 21;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme", "splittea", "splitptr", "spliteth", "splitblu", "splitnvy":
                        return 1;
                    case "rse":
                    case "pnk":
                        return 23;
                    case "splitrse":
                    case "splitpnk":
                        return 3;
                    case "rbw":
                        return 25;
                    case "splitrbw":
                        return 5;
                }
            case "muaat":
            case "pi_muaat":
            case "miltymodmuaat":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 25;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 5;
                    case "red":
                    case "bld":
                        return 37;
                    case "splitred":
                    case "splitbld":
                        return 17;
                    case "org":
                        return 36;
                    case "splitorg":
                        return 16;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 24;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 4;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 30;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 10;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme", "tea", "ptr", "eth", "blu", "nvy":
                        return 20;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme", "splittea", "splitptr", "spliteth", "splitblu", "splitnvy":
                        return 0;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 21;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 1;
                    case "rse":
                    case "pnk":
                        return 23;
                    case "splitrse":
                    case "splitpnk":
                        return 3;
                    case "rbw":
                        return 29;
                    case "splitrbw":
                        return 9;
                }
            case "naalu":
            case "pi_naalu":
            case "miltymodnaalu":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk", "tea", "ptr", "eth", "blu", "nvy":
                        return 25;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca", "splittea", "splitptr", "spliteth", "splitblu", "splitnvy":
                        return 5;
                    case "red":
                    case "bld", "lvn", "ppl", "sns":
                        return 22;
                    case "splitred":
                    case "splitbld", "splitlvn", "splitppl", "splitsns":
                        return 2;
                    case "org":
                        return 26;
                    case "splitorg":
                        return 6;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 27;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 7;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm", "rbw":
                        return 29;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm", "splitrbw":
                        return 9;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 37;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 17;
                    case "rse":
                    case "pnk":
                        return 21;
                    case "splitrse":
                    case "splitpnk":
                        return 1;
                }
            case "nekro":
            case "pi_nekro":
            case "miltymodnekro":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 34;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 14;
                    case "red":
                    case "bld":
                        return 40;
                    case "splitred":
                    case "splitbld", "tea", "ptr", "eth", "blu", "nvy", "lme", "grn", "tqs", "frs", "eme":
                        return 20;
                    case "org", "rse", "pnk", "lvn", "ppl", "sns":
                        return 22;
                    case "splitorg", "splitrse", "splitpnk", "splitlvn", "splitppl", "splitsns":
                        return 2;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 24;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 4;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm", "rbw":
                        return 21;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm", "splitrbw":
                        return 1;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme", "splittea", "splitptr", "spliteth", "splitblu", "splitnvy":
                        return 0;
                }
            case "saar":
            case "pi_saar":
            case "miltymodsaar":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk", "tea", "ptr", "eth", "blu", "nvy":
                        return 27;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca", "splittea", "splitptr", "spliteth", "splitblu", "splitnvy":
                        return 7;
                    case "red":
                    case "bld":
                        return 24;
                    case "splitred":
                    case "splitbld":
                        return 4;
                    case "org":
                        return 32;
                    case "splitorg":
                        return 12;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 36;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 6;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 33;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 13;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 29;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 9;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 22;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 2;
                    case "rse":
                    case "pnk":
                        return 20;
                    case "splitrse":
                    case "splitpnk":
                        return 0;
                    case "rbw":
                        return 25;
                    case "splitrbw":
                        return 5;
                }
            case "sardakk":
            case "pi_sardakk":
            case "miltymodsardakk":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 35;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 15;
                    case "red":
                    case "bld":
                        return 38;
                    case "splitred":
                    case "splitbld":
                        return 18;
                    case "org":
                        return 25;
                    case "splitorg":
                        return 5;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 30;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 10;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 24;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 4;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 26;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 6;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 20;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 0;
                    case "lvn":
                    case "ppl":
                    case "sns", "rbw", "rse", "pnk":
                        return 21;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns", "splitrbw", "splitrse", "splitpnk":
                        return 1;
                }
            case "sol":
            case "pi_sol":
            case "miltymodsol":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk", "rse", "pnk", "org", "red", "bld":
                        return 21;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca", "splitrse", "splitpnk", "splitorg", "splitred", "splitbld":
                        return 1;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 27;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 7;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 32;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 12;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme", "rbw":
                        return 29;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme", "splitrbw":
                        return 9;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 37;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 17;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 22;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 2;
                }
            case "winnu":
            case "pi_winnu":
            case "miltymodwinnu":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk", "lme", "grn", "tqs", "frs", "eme":
                        return 21;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca", "splitlme", "splitgrn", "splittqs", "splitfrs", "spliteme":
                        return 1;
                    case "red":
                    case "bld":
                        return 23;
                    case "splitred":
                    case "splitbld":
                        return 3;
                    case "org":
                        return 28;
                    case "splitorg":
                        return 8;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 24;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 4;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 34;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 14;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 25;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 5;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 35;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 15;
                    case "rse":
                    case "pnk":
                        return 29;
                    case "splitrse":
                    case "splitpnk":
                        return 9;
                    case "rbw":
                        return 33;
                    case "splitrbw":
                        return 13;
                }
            case "xxcha":
            case "pi_xxcha":
            case "miltymodxxcha":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk", "rse", "pnk", "lvn", "ppl", "sns", "org":
                        return 21;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca", "splitrse", "splitpnk", "splitlvn", "splitppl", "splitsns", "splitorg":
                        return 1;
                    case "red":
                    case "bld":
                        return 20;
                    case "splitred":
                    case "splitbld":
                        return 0;
                    case "tan":
                    case "bwn":
                    case "chk", "spr", "ylw", "gld", "crm":
                        return 27;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk", "splitspr", "splitylw", "splitgld", "splitcrm":
                        return 7;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 35;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 15;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 30;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 10;
                    case "rbw":
                        return 29;
                    case "splitrbw":
                        return 9;
                }
            case "yin":
            case "pi_yin":
            case "miltymodyin":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 31;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 11;
                    case "red":
                    case "bld":
                        return 23;
                    case "splitred":
                    case "splitbld":
                        return 3;
                    case "org", "tea", "ptr", "eth", "blu", "nvy":
                        return 21;
                    case "splitorg", "splittea", "splitptr", "spliteth", "splitblu", "splitnvy":
                        return 1;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 27;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 7;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm", "rse", "pnk":
                        return 28;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm", "splitrse", "splitpnk":
                        return 8;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme", "rbw":
                        return 25;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme", "splitrbw":
                        return 5;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 35;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 15;
                }
            case "yssaril":
            case "pi_yssaril":
            case "miltymodyssaril":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 26;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 6;
                    case "red":
                    case "bld":
                        return 24;
                    case "splitred":
                    case "splitbld":
                        return 4;
                    case "org", "lvn", "ppl", "sns":
                        return 23;
                    case "splitorg", "splitlvn", "splitppl", "splitsns":
                        return 3;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 30;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 10;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 27;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 7;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 36;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 16;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 22;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 2;
                    case "rse":
                    case "pnk":
                        return 20;
                    case "splitrse":
                    case "splitpnk":
                        return 0;
                    case "rbw":
                        return 21;
                    case "splitrbw":
                        return 1;
                }
            case "argent":
            case "pi_argent":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 23;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 3;
                    case "red":
                    case "bld":
                        return 28;
                    case "splitred":
                    case "splitbld":
                        return 8;
                    case "org":
                        return 39;
                    case "splitorg":
                        return 19;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 30;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 10;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 29;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 9;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme", "rbw":
                        return 25;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme", "splitrbw":
                        return 5;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy", "rse", "pnk", "lvn", "ppl", "sns":
                        return 21;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy", "splitrse", "splitpnk", "splitlvn", "splitppl", "splitsns":
                        return 1;
                }
            case "cabal":
            case "pi_cabal":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 30;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 10;
                    case "red":
                    case "bld":
                        return 40;
                    case "splitred":
                    case "splitbld", "lme", "grn", "tqs", "frs", "eme":
                        return 20;
                    case "org", "rbw":
                        return 25;
                    case "splitorg", "splitrbw":
                        return 5;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 27;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 7;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 21;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 1;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 0;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy", "lvn", "ppl", "sns":
                        return 24;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy", "splitlvn", "splitppl", "splitsns":
                        return 4;
                    case "rse":
                    case "pnk":
                        return 26;
                    case "splitrse":
                    case "splitpnk":
                        return 6;
                }
            case "empyrean":
            case "pi_empyrean":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk", "tea", "ptr", "eth", "blu", "nvy":
                        return 29;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca", "splittea", "splitptr", "spliteth", "splitblu", "splitnvy":
                        return 9;
                    case "red":
                    case "bld":
                        return 26;
                    case "splitred":
                    case "splitbld":
                        return 6;
                    case "org", "lme", "grn", "tqs", "frs", "eme", "tan", "bwn", "chk":
                        return 21;
                    case "splitorg", "splitlme", "splitgrn", "splittqs", "splitfrs", "spliteme", "splittan", "splitbwn", "splitchk":
                        return 1;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm", "splitlvn", "splitppl", "splitsns":
                        return 20;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 0;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 40;
                    case "rse":
                    case "pnk":
                        return 28;
                    case "splitrse":
                    case "splitpnk":
                        return 8;
                    case "rbw":
                        return 33;
                    case "splitrbw":
                        return 13;
                }
            case "mahact":
            case "pi_mahact":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 28;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 8;
                    case "red":
                    case "bld", "org":
                        return 26;
                    case "splitred":
                    case "splitbld", "splitorg":
                        return 6;
                    case "tan":
                    case "bwn":
                    case "chk", "rse", "pnk", "lme", "grn", "tqs", "frs", "eme":
                        return 24;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk", "splitrse", "splitpnk", "splitlme", "splitgrn", "splittqs", "splitfrs", "spliteme":
                        return 4;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 39;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 19;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 25;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 5;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 32;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 12;
                    case "rbw":
                        return 33;
                    case "splitrbw":
                        return 13;
                }
            case "nomad":
            case "pi_nomad":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 25;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 5;
                    case "red":
                    case "bld":
                        return 22;
                    case "splitred":
                    case "splitbld":
                        return 2;
                    case "org", "tan", "bwn", "chk":
                        return 21;
                    case "splitorg", "splittan", "splitbwn", "splitchk":
                        return 1;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 23;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 3;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 26;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 6;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 36;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 16;
                    case "lvn":
                    case "ppl":
                    case "sns", "rbw":
                        return 33;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns", "splitrbw":
                        return 13;
                    case "rse":
                    case "pnk":
                        return 27;
                    case "splitrse":
                    case "splitpnk":
                        return 7;
                }
            case "naaz":
            case "pi_naaz":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk", "lvn", "ppl", "sns":
                        return 24;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca", "splitlvn", "splitppl", "splitsns":
                        return 4;
                    case "red":
                    case "bld", "org":
                        return 23;
                    case "splitred":
                    case "splitbld", "splitorg":
                        return 3;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 30;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 10;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 26;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 6;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 39;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 19;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy", "rse", "pnk":
                        return 21;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy", "splitrse", "splitpnk":
                        return 1;
                    case "rbw":
                        return 29;
                    case "splitrbw":
                        return 9;
                }
            case "titans":
            case "pi_titans":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 22;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 2;
                    case "red":
                    case "bld", "rbw", "spr", "ylw", "gld", "crm":
                        return 25;
                    case "splitred":
                    case "splitbld", "splitrbw", "splitspr", "splitylw", "splitgld", "splitcrm":
                        return 5;
                    case "org", "lme", "grn", "tqs", "frs", "eme":
                        return 24;
                    case "splitorg", "splitlme", "splitgrn", "splittqs", "splitfrs", "spliteme":
                        return 4;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 27;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 7;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 23;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 3;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 26;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 6;
                    case "rse":
                    case "pnk":
                        return 40;
                    case "splitrse":
                    case "splitpnk":
                        return 20;
                }
            case "keleresa":
            case "keleresm":
            case "keleresx":
            case "keleresplus":
            case "pi_keleresm":
            case "pi_keleresx":
            case "pi_keleresa":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 33;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 13;
                    case "red":
                    case "bld":
                        return 21;
                    case "splitred":
                    case "splitbld":
                        return 1;
                    case "org":
                        return 23;
                    case "splitorg":
                        return 3;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 30;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 10;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 27;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm", "splitrbw":
                        return 7;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 26;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 6;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 32;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 12;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 35;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 15;
                    case "rse":
                    case "pnk":
                        return 34;
                    case "splitrse":
                    case "splitpnk":
                        return 4;
                    case "rbw":
                        return 37;
                }
                // Discordant Stars
            case "augers":
                switch (colour.getAlias()) {
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 35;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 15;
                }
            case "axis":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 35;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 15;
                    case "red":
                    case "bld":
                        return 30;
                    case "splitred":
                    case "splitbld":
                        return 10;
                }
            case "bentor":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 30;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 10;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 35;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 15;
                }
            case "celdauri":
                switch (colour.getAlias()) {
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 35;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 15;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 30;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 10;
                }
            case "cheiran":
                switch (colour.getAlias()) {
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 35;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 15;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 30;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 10;
                }
            case "cymiae":
                switch (colour.getAlias()) {
                    case "org":
                        return 35;
                    case "splitorg":
                        return 15;
                }
            case "dihmohn":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 30;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 10;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 35;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 15;
                }
            case "edyn":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 35;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 15;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 30;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 10;
                }
            case "florzen":
                switch (colour.getAlias()) {
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 35;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 15;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 30;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 10;
                }
            case "freesystems":
                switch (colour.getAlias()) {
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 30;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 10;
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 35;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 15;
                }
            case "ghemina":
                switch (colour.getAlias()) {
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 35;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 15;
                }
            case "ghoti":
                switch (colour.getAlias()) {
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 30;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme", "splittea", "splitptr", "spliteth", "splitblu", "splitnvy":
                        return 15;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 35;
                }
            case "gledge":
                switch (colour.getAlias()) {
                    case "org":
                        return 35;
                    case "splitorg":
                        return 15;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 30;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 10;
                }
            case "khrask":
                switch (colour.getAlias()) {
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 35;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 15;
                    case "org":
                        return 30;
                    case "splitorg":
                        return 10;
                }
            case "kjalengard":
                switch (colour.getAlias()) {
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 35;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 15;
                    case "red":
                    case "bld":
                        return 30;
                    case "splitred":
                    case "splitbld":
                        return 10;
                }
            case "kollecc":
                switch (colour.getAlias()) {
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 35;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 15;
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 30;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 10;
                }
            case "kolume":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 35;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 15;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 30;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 10;
                }
            case "kortali":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 35;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 15;
                }
            case "kyro":
                switch (colour.getAlias()) {
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 35;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 15;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 30;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 10;
                }
            case "lanefir":
                switch (colour.getAlias()) {
                    case "org":
                        return 35;
                    case "splitorg":
                        return 15;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 30;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 10;
                }
            case "lizho":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 30;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 10;
                    case "tan":
                    case "bwn":
                    case "chk":
                        return 35;
                    case "splittan":
                    case "splitbwn":
                    case "splitchk":
                        return 15;
                }
            case "mirveda":
                switch (colour.getAlias()) {
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 35;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 15;
                    case "rse":
                    case "pnk":
                        return 30;
                    case "splitrse":
                    case "splitpnk":
                        return 10;
                }
            case "mortheus":
                switch (colour.getAlias()) {
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy", "rse", "pnk":
                        return 30;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy", "splitrse", "splitpnk":
                        return 10;
                    case "rbw":
                        return 35;
                    case "splitrbw":
                        return 15;
                }
            case "mykomentori":
                switch (colour.getAlias()) {
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme", "rse", "pnk":
                        return 30;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme", "splitrse", "splitpnk":
                        return 10;
                    case "rbw":
                        return 35;
                    case "splitrbw":
                        return 15;
                }
            case "nivyn":
                switch (colour.getAlias()) {
                    case "lgy":
                    case "gry":
                    case "blk":
                        return 30;
                    case "splitlgy":
                    case "splitgry":
                    case "splitblk":
                    case "orca":
                        return 10;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 35;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 15;
                }
            case "nokar":
                switch (colour.getAlias()) {
                    case "org":
                        return 35;
                    case "splitorg":
                        return 15;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 30;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 10;
                }
            case "olradin":
                switch (colour.getAlias()) {
                    case "org":
                        return 35;
                    case "splitorg":
                        return 15;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 30;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 10;
                }
            case "rohdhna":
                switch (colour.getAlias()) {
                    case "lvn":
                    case "ppl":
                    case "sns":
                        return 35;
                    case "splitlvn":
                    case "splitppl":
                    case "splitsns":
                        return 15;
                }
            case "tnelis":
                switch (colour.getAlias()) {
                    case "red":
                    case "bld":
                        return 30;
                    case "splitred":
                    case "splitbld":
                        return 10;
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 35;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 15;
                }
            case "vaden":
                switch (colour.getAlias()) {
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 35;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 15;
                }
            case "vaylerian":
                switch (colour.getAlias()) {
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 35;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 15;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 30;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 10;
                }
            case "veldyr":
                switch (colour.getAlias()) {
                    case "lme":
                    case "grn":
                    case "tqs":
                    case "frs":
                    case "eme":
                        return 30;
                    case "splitlme":
                    case "splitgrn":
                    case "splittqs":
                    case "splitfrs":
                    case "spliteme":
                        return 10;
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 35;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 15;
                }
            case "zealots":
                switch (colour.getAlias()) {
                    case "tea":
                    case "ptr":
                    case "eth":
                    case "blu":
                    case "nvy":
                        return 35;
                    case "splittea":
                    case "splitptr":
                    case "spliteth":
                    case "splitblu":
                    case "splitnvy":
                        return 15;
                }
            case "zelian":
                switch (colour.getAlias()) {
                    case "red":
                    case "bld":
                        return 35;
                    case "splitred":
                    case "splitbld":
                        return 15;
                }
                // other homebrew
            case "drahn":
                switch (colour.getAlias()) {
                    case "red":
                    case "bld":
                        return 35;
                    case "splitred":
                    case "splitbld":
                        return 15;
                    case "spr":
                    case "ylw":
                    case "gld":
                    case "crm":
                        return 30;
                    case "splitspr":
                    case "splitylw":
                    case "splitgld":
                    case "splitcrm":
                        return 10;
                }
        }
        if (colour.getAlias().startsWith("split") || colour.getAlias().equals("orca")) {
            return 5;
        }
        return 15;
    }
}
