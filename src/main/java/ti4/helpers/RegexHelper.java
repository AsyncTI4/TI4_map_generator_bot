package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.generator.TileHelper;
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

    /**
     * @param game if provided, only match colors present in this game
     * @return group "color" matching any color in the bot
     */
    public static String colorRegex(Game game) {
        Set<String> colorNames = legalColors(game);
        return regexBuilder("color", colorNames);
    }

    /**
     * @param game if provided, only match factions present in this game
     * @return group "faction" matching any faction in the bot
     */
    public static String factionRegex(Game game) {
        Set<String> factionAliases = legalFactions(game);
        return regexBuilder("faction", factionAliases);
    }

    /**
     * @param game if provided, only match colors and factions present in this game
     * @return group "factionorcolor" matching any faction/color in the bot
     */
    public static String factionOrColorRegex(Game game) {
        Set<String> matchers = legalFactions(game);
        matchers.addAll(legalColors(game));
        return regexBuilder("factionorcolor", matchers);
    }

    /** @return group "unittype" */
    public static String unitTypeRegex(String group) {
        Set<String> types = new HashSet<>();
        Arrays.asList(UnitType.values()).forEach(x -> types.add(x.getValue()));
        return regexBuilder(group, types);
    }

    /** @return group "unittype" */
    public static String unitTypeRegex() {
        return unitTypeRegex("unittype");
    }

    /** @return group "genericcard" */
    public static String genericCardRegex() {
        Set<String> cards = Mapper.getGenericCards().keySet();
        return regexBuilder("genericcard", cards);
    }

    /** @return group matching [+-] and any number of digits 0-9 */
    public static String intRegex(String group) {
        return regexBuilder(group, "[\\+\\-]?[0-9]+");
    }

    /** @return group matching any legal tile position on the map */
    public static String posRegex(Game game, String group) {
        if (game == null) return posRegex(group);
        Set<String> positions = game.getTileMap().keySet();
        return regexBuilder(group, positions);
    }

    /** @return group "pos" matching any tile position on the map */
    public static String posRegex(Game game) {
        return posRegex(game, "pos");
    }

    /** @return group matching any legal tile position in the bot */
    public static String posRegex(String group) {
        return regexBuilder(group, PositionMapper.getTilePositions());
    }

    /** @return group "pos" matching any legal tile position in the bot */
    public static String posRegex() {
        return posRegex("pos");
    }

    /** @return group "tileID" matching any legal tile ID in the bot */
    public static String tileIDRegex() {
        return regexBuilder("tileID", TileHelper.getAllTiles().keySet());
    }

    /** @return group matching any planet on the map, and also "space" */
    public static String unitHolderRegex(Game game, String group) {
        Set<String> unitholders = new HashSet<>();
        unitholders.addAll(game.getPlanets());
        unitholders.add("space");
        return regexBuilder(group, unitholders);
    }

    /** @return group "relic" */
    public static String relicRegex(Game game) {
        Set<String> relics = new HashSet<>(Mapper.getDeck(game.getRelicDeckID()).getNewDeck());
        return regexBuilder("relic", relics);
    }

    /** @return group "ac" */
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

    /** @return group "page" matching an integer */
    public static String pageRegex() {
        return "page" + RegexHelper.intRegex("page") + "$";
    }

    /** @return group "token" */
    public static String tokenRegex() {
        Set<String> tokens = new HashSet<>(Mapper.getTokens());
        return regexBuilder("token", tokens);
    }

    // TODO (Jazz): Use this
    /** format "__{Pfstkx}" @return groups ["type", "free", "swap", "tgsOnly", "share", "faction"] */
    public static String techMenuSuffixRegex() {
        String regex = "__\\{";
        List<String> groups = new ArrayList<>();
        groups.add(regexBuilder("type", "[PBCWUA]"));
        groups.add(regexBuilder("free", "f?"));
        groups.add(regexBuilder("swap", "s?"));
        groups.add(regexBuilder("tgsOnly", "t?"));
        groups.add(regexBuilder("share", "k?"));
        regex += String.join("", groups);
        regex += "\\}";
        return regex;
    }
}
