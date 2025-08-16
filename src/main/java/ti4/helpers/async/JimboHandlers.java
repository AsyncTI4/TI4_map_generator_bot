package ti4.helpers.async;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import software.amazon.awssdk.utils.StringUtils;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RegexHelper;
import ti4.image.PositionMapper;
import ti4.image.TileHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.TileModel;
import ti4.service.map.AddTileService;

// Jazz's Interactive Map Builder
public class JimboHandlers {
    @ButtonHandler(JimboConst.exit)
    private static void exit(ButtonInteractionEvent event) {
        String msg = "🤙 cya later!";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        ButtonHelper.deleteMessage(event);
    }

    // weeee many buttons, will it work???
    @ButtonHandler(JimboConst.mainPage)
    @ButtonHandler(JimboConst.tileAction)
    @ButtonHandler(JimboConst.featureAction)
    @ButtonHandler(JimboConst.transformAction)
    @ButtonHandler(JimboConst.metaAction)
    private static void menu(GenericInteractionCreateEvent event, MessageChannel channel, Game game, String buttonID) {
        String message = "__Welcome to **\"JIMBO\"**, Jazz's Interactive Map Building " + JimboConst.o() + "!!!__";
        String menu;
        if (buttonID == null) buttonID = JimboConst.mainPage;
        if (buttonID.startsWith(JimboConst.tileAction)) {
            menu = JimboConst.tileAction;
            message += "\n> Add, remove, or move/swap tiles around the map.";
            message += "\n> If you have symmetry turned on, you can't add any tiles with planets";
        } else if (buttonID.startsWith(JimboConst.featureAction)) {
            menu = JimboConst.featureAction;
            message += "\n> Choose what you want to do.";
            message += "\n> - Tokens include attachments and wormholes and stuff like that.";
            message += "\n> - Border anomalies are a homebrew feature that can make it harder to move around the map";
        } else if (buttonID.startsWith(JimboConst.transformAction)) {
            menu = JimboConst.transformAction;
            message +=
                    "\n> Shift the map in any direction, or rotate the map centered on 000. (Hyperlanes will be rotated along with the map).";
        } else if (buttonID.startsWith(JimboConst.metaAction)) {
            menu = JimboConst.metaAction;
            message += " Add symmetry or export the map template for later use";
        } else {
            menu = JimboConst.mainPage;
            message += "\n> Choose an option to get started:";
        }

        List<Button> buttons = JimboButtons.getMenuButtons(menu);
        if (event instanceof ButtonInteractionEvent bevent) {
            MessageHelper.editMessageWithButtons(bevent, message, buttons);
        } else { // Post for the first time
            MessageHelper.sendMessageToChannelWithButtons(channel, message, buttons);
        }
    }

    public static void postMainMenu(GenericInteractionCreateEvent event, Game game) {
        ButtonHelper.deleteMessage(event);
        menu(null, event.getMessageChannel(), game, JimboConst.mainPage);
    }

    // Completed
    // - untested
    @ButtonHandler(JimboConst.tileAdd)
    private static void addTileBaseMenu(ButtonInteractionEvent event, Game game) {
        boolean symmetrical = false;
        String msg = "Choose a type of tile to add:";
        msg += "\n> Special tiles include mecatol, mallice, and several other tiles of a similar nature";
        msg +=
                "\n> Draft tiles are plain colored tiles used in map templates to mark where a player would place drafted tiles";
        if (symmetrical) {
            msg +=
                    "\n> - You have symmetry turned on. Green, blue, and red tiles will become random, and hyperlanes will rotate or flip accordingly.";
            msg +=
                    " Available \"draft tile\" colors will be heavily restricted while symmetry is turned on, and symmetrically-placed tiles will pull from the restricted portion of the list.";
        }
        List<Button> buttons = List.of(
                JimboButtons.MAIN_PAGE,
                Buttons.green(JimboConst.tileAdd + "_green_page0", "Green-back tile (home systems)"),
                Buttons.blue(JimboConst.tileAdd + "_blue_page0", "Blue-back tile"),
                Buttons.red(JimboConst.tileAdd + "_red_page0", "Red-back tile"),
                Buttons.gray(JimboConst.tileAdd + "_hyperlane_rot0_page0", "Hyperlane tile"),
                Buttons.gray(JimboConst.tileAdd + "_draft_rot0_page0", "Draft tile"),
                Buttons.gray(JimboConst.tileAdd + "_other_page0", "Other special tile"));
        MessageHelper.editMessageWithButtons(event, msg, buttons);
    }

    // Completed
    // - untested
    @ButtonHandler(JimboConst.tileAdd + "_")
    private static void addTileRoutine(ButtonInteractionEvent event, Game game, String buttonID) {
        Matcher matcher;
        String rotationAndPage =
                RegexHelper.optional("_rot" + RegexHelper.intRegex("index")) + "_" + RegexHelper.pageRegex();
        String regexPt1 = JimboConst.tileAdd + "_(?<type>(green|blue|red|hyperlane|draft|other))" + rotationAndPage;
        String regexPt2 = JimboConst.tileAdd + "_" + RegexHelper.tileIDRegex();
        String regexPt3 = regexPt2 + "_ring" + RegexHelper.oneOf(List.of(RegexHelper.intRegex("ring"), "corners"));
        String regexPt4 = regexPt3 + "_" + RegexHelper.pageRegex();
        String regexPt5 = regexPt3 + "_pos" + RegexHelper.posRegex();

        if ((matcher = Pattern.compile(regexPt1).matcher(buttonID)).matches()) {
            String msg = "Choose a tile that you want to place on the map:";
            String type = matcher.group("type");

            Function<TileModel, Button> toButton = tile -> JimboButtons.tileToButton(tile, JimboConst.tileAdd);
            int rotation = matcher.group("index") != null ? Integer.parseInt(matcher.group("index")) : 0;
            List<Button> bonusButtons = new ArrayList<>(List.of(JimboButtons.MAIN_PAGE));

            bonusButtons.addAll(getRotateTileButtons(type, matcher.group("page"), rotation));
            List<TileModel> tiles = getTilesForType(type, rotation, 0);

            JimboButtons.jimboPagination(
                    event, msg, tiles, toButton, JimboImageHelper::tilesImage, bonusButtons, 10, buttonID);

        } else if ((matcher = Pattern.compile(regexPt2).matcher(buttonID)).matches()) {
            TileModel model = TileHelper.getTileById(matcher.group("tileID"));
            String msg = "Choose a ring to place " + model.getName() + " (" + model.getAlias() + "):";
            List<Button> goBack = new ArrayList<>(
                    List.of(JimboButtons.MAIN_PAGE, Buttons.gray(JimboConst.tileAdd, "Pick a different tile")));
            pickRing(event, null, msg, buttonID, goBack);

        } else if ((matcher = Pattern.compile(regexPt3).matcher(buttonID)).matches()) {
            TileModel model = TileHelper.getTileById(matcher.group("tileID"));
            String msg = "Choose a location to place " + model.getName() + " (" + model.getAlias()
                    + "): \n> - Any existing tile will be overwritten";
            List<Button> bonus = new ArrayList<>(List.of(JimboButtons.MAIN_PAGE));
            bonus.add(Buttons.gray(JimboConst.tileAdd + "_" + model.getAlias(), "Pick a different ring"));
            bonus.add(Buttons.gray("showGameEphemeral", "Show map"));
            List<String> locations = PositionMapper.getPositionsInRing(matcher.group("ring"), null);
            String buttonatorPrefix = JimboConst.tileAdd + "_" + model.getAlias() + "_ring" + matcher.group("ring");
            Function<String, Button> buttonator = pos -> JimboButtons.positionToButton(pos, buttonatorPrefix, game);
            JimboButtons.jimboPagination(event, msg, locations, buttonator, null, bonus, 15, buttonID);

        } else if ((matcher = Pattern.compile(regexPt4).matcher(buttonID)).matches()) {
            TileModel model = TileHelper.getTileById(matcher.group("tileID"));
            String msg = "Choose a location to place " + model.getName() + " (" + model.getAlias()
                    + "): \n> - Any existing tile will be overwritten";
            List<Button> bonus = new ArrayList<>(List.of(JimboButtons.MAIN_PAGE));
            bonus.add(Buttons.gray(JimboConst.tileAdd + "_" + model.getAlias(), "Pick a different ring"));
            bonus.add(Buttons.gray("showGameEphemeral", "Show map"));
            List<String> locations = PositionMapper.getPositionsInRing(matcher.group("ring"), null);
            String buttonatorPrefix = JimboConst.tileAdd + "_" + model.getAlias() + "_ring" + matcher.group("ring");
            Function<String, Button> buttonator = pos -> JimboButtons.positionToButton(pos, buttonatorPrefix, game);
            JimboButtons.jimboPagination(event, msg, locations, buttonator, null, bonus, 15, buttonID);

        } else if ((matcher = Pattern.compile(regexPt5).matcher(buttonID)).matches()) {
            TileModel model = TileHelper.getTileById(matcher.group("tileID"));
            String pos = matcher.group("pos");
            AddTileService.addTile(game, new Tile(model.getAlias(), pos));

            String msg = model.getName() + " (" + model.getAlias() + ") has been placed in location " + pos + ".";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            postMainMenu(event, game);
        }
    }

    // Completed
    // - untested
    @ButtonHandler(JimboConst.tileMove)
    private static void moveTileRoutine(ButtonInteractionEvent event, Game game, String buttonID) {
        Matcher matcher;

        String regexPt1 = JimboConst.tileMove;
        String regexPt2 = regexPt1 + "_ring" + RegexHelper.oneOf(List.of(RegexHelper.intRegex("ring"), "corners"));
        String regexPt3 = regexPt1 + "_tile" + RegexHelper.posRegex("posFrom");
        String regexPt4 = regexPt3 + "_ring" + RegexHelper.oneOf(List.of(RegexHelper.intRegex("ring"), "corners"));
        String regexPt5 = regexPt3 + "_tile" + RegexHelper.posRegex("posTo");

        if (Pattern.compile(regexPt1).matcher(buttonID).matches()) {
            String msg = "Choose a ring to move a tile from:";
            List<Button> goBack = new ArrayList<>(List.of(JimboButtons.MAIN_PAGE));
            pickRing(event, game, msg, buttonID, goBack);

        } else if ((matcher = Pattern.compile(regexPt2).matcher(buttonID)).matches()) {
            String msg = "Choose a tile to move:";
            String ring = matcher.group("ring");
            List<Button> bonus = new ArrayList<>(
                    List.of(JimboButtons.MAIN_PAGE, Buttons.gray(JimboConst.tileMove, "Pick a different ring")));
            List<String> locations = PositionMapper.getPositionsInRing(ring, null);
            Function<String, Button> buttonator = pos -> JimboButtons.positionToButton(pos, JimboConst.tileMove, game);
            JimboButtons.jimboPagination(event, msg, locations, buttonator, null, bonus, 15, buttonID);

        } else if ((matcher = Pattern.compile(regexPt3).matcher(buttonID)).matches()) {
            String tile = game.getTileByPosition(matcher.group("posFrom")).getRepresentationForButtons(null, null);
            String msg = "Choose a ring to move " + tile
                    + " to:\n> - If there is already a tile in the destination, they will be swapped";
            List<Button> goBack = new ArrayList<>(
                    List.of(JimboButtons.MAIN_PAGE, Buttons.gray(JimboConst.tileMove, "Move a different tile")));
            pickRing(event, null, msg, buttonID, goBack);

        } else if ((matcher = Pattern.compile(regexPt4).matcher(buttonID)).matches()) {
            String tile = game.getTileByPosition(matcher.group("posFrom")).getRepresentationForButtons(null, null);
            String msg = "Choose a destination to move or swap " + tile + " to:";
            String ring = matcher.group("ring");
            List<Button> bonus = new ArrayList<>(
                    List.of(JimboButtons.MAIN_PAGE, Buttons.gray(JimboConst.tileMove, "Pick a different ring")));
            List<String> locations = PositionMapper.getPositionsInRing(ring, null);
            Function<String, Button> buttonator = pos -> JimboButtons.positionToButton(pos, JimboConst.tileMove, game);
            JimboButtons.jimboPagination(event, msg, locations, buttonator, null, bonus, 15, buttonID);

        } else if ((matcher = Pattern.compile(regexPt5).matcher(buttonID)).matches()) {
            String pos1 = matcher.group("posFrom");
            String pos2 = matcher.group("posTo");
            Tile t1 = game.getTileByPosition(pos1);
            Tile t2 = game.getTileByPosition(pos2);
            if (t1 != null) {
                t1.setPosition(pos2);
                game.setTile(t1);
            }
            if (t2 != null) {
                t2.setPosition(pos1);
                game.setTile(t2);
            }
            String msg = "Successfully swapped the tiles at positions [] and []";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
            postMainMenu(event, game);
        }
    }

    // Completed
    // - untested
    @ButtonHandler(JimboConst.tileRemove)
    private static void removeTileRoutine(ButtonInteractionEvent event, Game game, String buttonID) {
        Matcher matcher;

        String regexPt1 = JimboConst.tileRemove;
        String regexPt2 = regexPt1 + "_ring" + RegexHelper.oneOf(List.of(RegexHelper.intRegex("ring"), "corners"));
        String regexPt3 = regexPt1 + "_tile" + RegexHelper.posRegex("pos");

        if (Pattern.compile(regexPt1).matcher(buttonID).matches()) {
            String msg = "Choose a ring to remove a tile from:";
            List<Button> goBack = new ArrayList<>(List.of(JimboButtons.MAIN_PAGE));
            pickRing(event, game, msg, JimboConst.tileRemove, goBack);

        } else if ((matcher = Pattern.compile(regexPt2).matcher(buttonID)).matches()) {
            String msg = "Choose a tile to remove:";
            String ring = matcher.group("ring");
            List<Button> bonus = new ArrayList<>(
                    List.of(JimboButtons.MAIN_PAGE, Buttons.gray(JimboConst.tileRemove, "Pick a different ring")));
            List<String> locations = PositionMapper.getPositionsInRing(ring, null);
            Function<String, Button> buttonator =
                    pos -> JimboButtons.positionToButton(pos, JimboConst.tileRemove, game);
            JimboButtons.jimboPagination(event, msg, locations, buttonator, null, bonus, 15, buttonID);

        } else if ((matcher = Pattern.compile(regexPt3).matcher(buttonID)).matches()) {
            String pos = matcher.group("pos");
            TileModel model = game.getTileByPosition(pos).getTileModel();
            String msg = "Removed tile " + model.getName() + " (" + model.getAlias() + ") from position " + pos + ":";
            game.removeTile(pos);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);

            // DONE
            postMainMenu(event, game);
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------------------------------
    private static void pickRing(
            ButtonInteractionEvent event, Game game, String msg, String buttonPrefix, List<Button> goBack) {
        if (StringUtils.isBlank(msg)) msg = "Choose a ring:";

        List<Button> rings = new ArrayList<>(goBack);
        for (int ring = 0; ring <= 13; ++ring) {
            String extra = ring == 0 ? " (Mecatol)" : "";
            List<String> positions = PositionMapper.getPositionsInRing(Integer.toString(ring), game);
            if (positions.size() == 1) {
                rings.add(Buttons.green(buttonPrefix + "_tile" + positions.getFirst(), "Tile " + positions.getFirst()));
            } else if (!positions.isEmpty()) {
                rings.add(Buttons.green(buttonPrefix + "_ring" + ring + "_page0", "Ring " + ring + extra));
            }
        }
        if (!PositionMapper.getPositionsInRing("corners", game).isEmpty()) {
            rings.add(Buttons.green(buttonPrefix + "_ringcorners_page0", "Corners"));
        }
        MessageHelper.editMessageWithButtons(event, msg, rings);
    }

    private static List<TileModel> getTilesForType(String type, int tileNum, int numSymmetries) {
        JimboConst.setupTileStuff();
        List<TileModel> output = new ArrayList<>();
        switch (type) {
            case "blue" -> output.addAll(JimboConst.blueTiles);
            case "red" -> output.addAll(JimboConst.redTiles);
            case "green" -> output.addAll(JimboConst.greenTiles);
            case "hyperlane" -> {
                tileNum = (tileNum + 6) % 6;
                output.addAll(JimboConst.hyperlanesByRotation.get(tileNum));
            }
            case "draft" -> {
                tileNum = tileNum < -1 ? -1 : (Math.min(tileNum, 12));
                output.addAll(JimboConst.draftTilesByNumber.get(tileNum));
            }
            case "other" -> output.addAll(JimboConst.otherTiles);
        }
        return output;
    }

    private static List<Button> getRotateTileButtons(String type, String pageNum, int rotation) {
        List<Button> bonusButtons = new ArrayList<>();
        if ("hyperlane".equals(type)) {
            int prev = (rotation + 5) % 6, next = (rotation + 1) % 6;
            String page = "_page" + pageNum;
            bonusButtons.add(
                    Buttons.gray(JimboConst.tileAdd + "_hyperlane_rot" + prev + page, "Rotate CCW")); // TODO: emoji
            bonusButtons.add(
                    Buttons.gray(JimboConst.tileAdd + "_hyperlane_rot" + next + page, "Rotate CW")); // TODO: emoji
        } else if ("draft".equals(type)) {
            int prev = rotation - 1, next = rotation + 1;
            String page = "_page" + pageNum;
            String decString = prev == -1 ? "Get Home Tile" : "Decrement Tile Number";
            if (prev > -2)
                bonusButtons.add(
                        Buttons.gray(JimboConst.tileAdd + "_draft_rot" + prev + page, decString)); // TODO: emoji
            if (next < 11)
                bonusButtons.add(Buttons.gray(
                        JimboConst.tileAdd + "_draft_rot" + next + page, "Increment Tile Number")); // TODO: emoji
        }
        return bonusButtons;
    }
}
