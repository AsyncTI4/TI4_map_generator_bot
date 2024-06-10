package ti4.helpers;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ti4.generator.Mapper;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;

public class RegexHelper {

    private static String regexBuilder(String groupname, Set<String> options) {
        StringBuilder sb = new StringBuilder("(?<").append(groupname).append(">(");
        sb.append(String.join("|", options));
        sb.append("))");
        return sb.toString();
    }

    private static String regexBuilder(String groupname, String pattern) {
        StringBuilder sb = new StringBuilder("(?<").append(groupname).append(">");
        sb.append(pattern).append(")");
        return sb.toString();
    }

    private static Set<String> legalColors(Game game) {
        Set<String> colors = new HashSet<>();
        Mapper.getColors().forEach(color -> {
            if (game == null || game.getPlayerByColorID(color.getAlias()).isPresent()) {
                colors.add(color.getName());
                colors.add(color.getAlias());
            }
        });
        return colors;
    }

    private static Set<String> legalFactions(Game game) {
        Set<String> factionAliases = new HashSet<>();
        Mapper.getFactions().forEach(faction -> {
            if (game == null || game.getPlayerFromColorOrFaction(faction.getAlias()) != null) {
                factionAliases.add(faction.getAlias());
            }
        });
        return factionAliases;
    }

    public static String optional(String regex) {
        StringBuilder sb = new StringBuilder("(").append(regex).append(")?");
        return sb.toString();
    }

    public static String oneOf(List<String> regex) {
        StringBuilder sb = new StringBuilder("(");
        sb.append("(").append(String.join(")|(", regex)).append(")");
        sb.append(")");
        return sb.toString();
    }

    /* Provide a regex group that matches on any legal color in the bot */
    /* If a game object is provided, only match on the colors present in that particular game */
    public static String colorRegex(Game game) {
        Set<String> colorNames = legalColors(game);
        return regexBuilder("color", colorNames);
    }

    /* Provide a regex group that matches on any faction in the bot */
    /* If a game object is provided, only match on the factions present in that particular game */
    public static String factionRegex(Game game) {
        Set<String> factionAliases = legalFactions(game);
        return regexBuilder("faction", factionAliases);
    }

    /* Provide a regex group that matches on any faction or legal color in the bot */
    /* If a game object is provided, only match on the colors and factions present in that particular game */
    public static String factionOrColorRegex(Game game) {
        Set<String> matchers = legalFactions(game);
        matchers.addAll(legalColors(game));
        return regexBuilder("factionorcolor", matchers);
    }

    public static String unitTypeRegex() {
        Set<String> types = new HashSet<>();
        Arrays.asList(UnitType.values()).forEach(x -> types.add(x.getValue()));
        return regexBuilder("unittype", types);
    }

    public static String genericCardRegex() {
        Set<String> cards = Mapper.getGenericCards().keySet();
        return regexBuilder("genericcard", cards);
    }

    public static String intRegex(String group) {
        return regexBuilder(group, "[0-9]+");
    }

    public static String posRegex(Game game) {
        Set<String> positions = game.getTileMap().keySet();
        return regexBuilder("pos", positions);
    }

    public static String posRegex(Game game, String group) {
        Set<String> positions = game.getTileMap().keySet();
        return regexBuilder(group, positions);
    }

    public static String unitHolderRegex(Game game, String group) {
        Set<String> unitholders = new HashSet<>();
        unitholders.addAll(game.getPlanets());
        unitholders.add("space");
        return regexBuilder(group, unitholders);
    }

    public static String acRegex(Game game) {
        Set<String> allACs = new HashSet<>();
        if (game != null) {
            allACs.addAll(game.getActionCards());
            allACs.addAll(game.getDiscardActionCards().keySet());
        } else {
            allACs.addAll(Mapper.getActionCards().keySet());
        }
        return regexBuilder("ac", allACs);
    }

    public static String pageRegex() {
        return "page" + RegexHelper.intRegex("page") + "^";
    }
}
