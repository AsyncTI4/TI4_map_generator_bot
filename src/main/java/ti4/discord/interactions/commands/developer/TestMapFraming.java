package ti4.discord.interactions.commands.developer;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.image.MapSegment;
import ti4.message.MessageHelper;
import ti4.service.ShowGameService;
import ti4.service.map.AddTileService;
import ti4.service.option.FOWOptionService.FOWOption;

// TODO: temporary live-test panel for fog map framing/segments; delete with its DeveloperCommand entry before merge
class TestMapFraming extends GameStateSubcommand {

    private static final String PREFIX = "devMapFraming_";
    private static final String PREVIOUS_HOME_KEY = "devMapFramingPreviousHome";
    private static final List<String> SYSTEM_TILE_IDS =
            IntStream.rangeClosed(19, 50).mapToObj(String::valueOf).toList();

    TestMapFraming() {
        super(
                "test_map_framing",
                "TEMPORARY: rebuild this fog game's map into framing/segment test layouts",
                true,
                true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!getGame().isFowMode()) {
            MessageHelper.replyToMessage(event, "Use this in a Fog of War test game: every layout wipes the map.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), describe(getGame()), buttons());
    }

    private static List<Button> buttons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(PREFIX + "core", "Core: 2 rings around 000"));
        buttons.add(Buttons.green(PREFIX + "offCentre", "Off-centre: 2 rings around 701"));
        buttons.add(Buttons.green(PREFIX + "twoMaps", "Two maps + segments (000 / 1237)"));
        buttons.add(Buttons.green(PREFIX + "overCap", "Over cap: 1201 and 1237, no segments"));
        buttons.add(Buttons.green(PREFIX + "longStrip", "Long strip 1201-1237, home at 1201"));
        buttons.add(Buttons.green(PREFIX + "autoSectors", "Three clusters, automatic sectors on"));
        buttons.add(Buttons.gray(PREFIX + "toggleAutoSectors", "Toggle automatic sectors"));
        buttons.add(Buttons.green(PREFIX + "fracture", "Core + Fracture (separate map option on)"));
        buttons.add(Buttons.gray(PREFIX + "toggleFractureOption", "Toggle Separate Fracture option"));
        buttons.add(Buttons.blue(PREFIX + "revealSouth", "Give me vision at 1237"));
        buttons.add(Buttons.blue(PREFIX + "show", "Show map (as me)"));
        buttons.add(Buttons.red(PREFIX + "wipe", "Wipe map and segments"));
        buttons.add(Buttons.gray("deleteButtons", "Done"));
        return buttons;
    }

    @ButtonHandler(PREFIX)
    public static void handleTestButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!CommandHelper.hasRole(event, JdaService.developerRoles)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "These test buttons are for developers only.");
            return;
        }
        if (!game.isFowMode()) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "This game is not in Fog of War mode.");
            return;
        }
        String action = buttonID.substring(PREFIX.length());
        switch (action) {
            case "core" -> buildLayout(game, player, ring("000", 2), "000");
            case "offCentre" -> buildLayout(game, player, ring("701", 2), "701");
            case "twoMaps" -> buildTwoMaps(game, player);
            case "overCap" -> buildLayout(game, player, union(ring("1201", 2), ring("1237", 2)), "1201");
            case "longStrip" -> buildLayout(game, player, longStrip(), "1201");
            case "fracture" -> buildFracture(game, player);
            case "autoSectors" -> buildAutoSectors(game, player);
            case "toggleAutoSectors" -> MapSegment.setAutoSectors(game, !MapSegment.isAutoSectors(game));
            case "toggleFractureOption" ->
                game.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, !game.getFowOption(FOWOption.FRACTURE_SEPARATE_MAP));
            case "revealSouth" -> placeCarrier(game, player, "1237");
            case "show" -> {
                ShowGameService.simpleShowGame(game, event);
                return;
            }
            case "wipe" -> wipe(game, player);
            default -> {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Unknown action `" + action + "`.");
                return;
            }
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "### `test_map_framing` → `" + action + "`\n" + describe(game));
    }

    private static void buildTwoMaps(Game game, Player player) {
        buildLayout(game, player, union(ring("000", 2), ring("1237", 2)), "000");
        MapSegment.put(game, new MapSegment("core", "000", 3));
        MapSegment.put(game, new MapSegment("south", "1237", 3));
        MapSegment.setDefault(game, "core");
    }

    private static void buildAutoSectors(Game game, Player player) {
        Set<String> positions = union(ring("000", 2), ring("1237", 2));
        positions.add("401");
        buildLayout(game, player, positions, "000");
        MapSegment.setAutoSectors(game, true);
    }

    private static void buildFracture(Game game, Player player) {
        buildLayout(game, player, ring("000", 2), "000");
        for (int index = 1; index <= 7; index++) {
            AddTileService.addTile(game, new Tile(SYSTEM_TILE_IDS.get(index), "frac" + index));
        }
        placeCarrier(game, player, "frac1");
        game.setFowOption(FOWOption.FRACTURE_SEPARATE_MAP, true);
    }

    private static void buildLayout(Game game, Player player, Set<String> positions, String home) {
        wipe(game, player);
        int index = 0;
        for (String position : positions) {
            String tileId = SYSTEM_TILE_IDS.get(index++ % SYSTEM_TILE_IDS.size());
            AddTileService.addTile(game, new Tile(tileId, position));
        }
        if (game.getStoredValue(PREVIOUS_HOME_KEY).isEmpty()) {
            game.setStoredValue(PREVIOUS_HOME_KEY, String.valueOf(player.getHomeSystemPosition()));
        }
        player.setHomeSystemPosition(home);
        placeCarrier(game, player, home);
    }

    private static void placeCarrier(Game game, Player player, String position) {
        Tile tile = game.getTileByPosition(position);
        if (tile == null || player.getColorID() == null) return;
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, player.getColorID()), 1);
    }

    private static void wipe(Game game, Player player) {
        new ArrayList<>(game.getTileMap().keySet()).forEach(game::removeTile);
        for (Player gamePlayer : game.getPlayers().values()) {
            gamePlayer.getFogTiles().clear();
            gamePlayer.getFogLabels().clear();
        }
        MapSegment.stored(game).forEach(segment -> MapSegment.remove(game, segment.name()));
        MapSegment.clearDefault(game);
        MapSegment.setAutoSectors(game, false);
        MapSegment.setGap(game, 0);
        String previousHome = game.getStoredValue(PREVIOUS_HOME_KEY);
        if (!previousHome.isEmpty()) {
            player.setHomeSystemPosition("null".equals(previousHome) ? null : previousHome);
            game.removeStoredValue(PREVIOUS_HOME_KEY);
        }
    }

    private static Set<String> ring(String centre, int radius) {
        return MapSegment.positionsAround(centre, radius);
    }

    private static Set<String> longStrip() {
        Set<String> strip = new LinkedHashSet<>();
        for (int ring = 12; ring >= 1; ring--) {
            strip.add(ring + "01");
        }
        strip.add("000");
        for (int ring = 1; ring <= 12; ring++) {
            strip.add(ring + String.format("%02d", 3 * ring + 1));
        }
        return strip;
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> union = new LinkedHashSet<>(first);
        union.addAll(second);
        return union;
    }

    private static String describe(Game game) {
        List<MapSegment> segments = MapSegment.all(game);
        String defaultName =
                MapSegment.defaultSegment(game).map(MapSegment::name).orElse("none");
        return "Fog framing test panel. Tiles on map: **" + game.getTileMap().size() + "**, segments: **"
                + segments.size() + "** (default: " + defaultName + "), separate Fracture: **"
                + game.getFowOption(FOWOption.FRACTURE_SEPARATE_MAP) + "**.\n"
                + "Each layout wipes the map, places blue-back systems, sets your home system and a carrier there.\n"
                + "Check with `/show_game`, `/show_game display_type:map`, and `/fow map_segment` as GM.";
    }
}
