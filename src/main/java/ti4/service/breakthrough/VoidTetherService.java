package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.image.PositionMapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel.BorderAnomalyType;
import ti4.service.emoji.FactionEmojis;
import ti4.service.regex.RegexService;

@UtilityClass
public class VoidTetherService {

    private String tetherRep() {
        return Mapper.getBreakthrough("empyreanbt").getNameRepresentation();
    }

    private String directionStr(int dir) {
        return switch (dir) {
            case 0 -> "North";
            case 1 -> "Northeast";
            case 2 -> "Southeast";
            case 3 -> "South";
            case 4 -> "Southwest";
            case 5 -> "Northwest";
            default -> "";
        };
    }

    public boolean meetsCriteria(Game game, Player player, Tile activeSystem) {
        if (!player.hasUnlockedBreakthrough("empyreanbt")) return false;

        Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, activeSystem.getPosition(), player, false, true);
        for (String pos : adjTiles) {
            Tile t = game.getTileByPosition(pos);
            if (FoWHelper.playerHasUnitsInSystem(player, t)) {
                return true;
            }
            for (Planet planet : t.getPlanetUnitHolders()) {
                if (player.getPlanetsAllianceMode().contains(planet.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    public void postInitialButtons(Game game, Player player, Tile activeSystem) {
        List<Button> buttons = getInitialVoidTetherButtons(game, player, activeSystem.getPosition());
        String message = player.getRepresentationNoPing()
                + " you can use these buttons to place or move a Void Tether token using your breakthrough:";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    private List<BorderAnomalyHolder> getTethersOnMap(Game game) {
        List<BorderAnomalyHolder> tethers = new ArrayList<>();
        for (BorderAnomalyHolder b : game.getBorderAnomalies()) {
            if (b != null && b.getType() == BorderAnomalyType.VOID_TETHER) {
                tethers.add(b);
            }
        }
        return tethers;
    }

    public List<Button> getRemoveVoidTetherButtons(Game game, Player player, String pos) {
        List<Button> buttons = new ArrayList<>();
        String prefixRemove = player.finChecker() + "moveVoidTether_" + pos;
        if (pos == null) {
            prefixRemove = player.finChecker() + "removeVoidTether";
        }
        for (BorderAnomalyHolder border : getTethersOnMap(game)) {
            String dir = "(" + directionStr(border.getDirection()) + ")";
            String id = prefixRemove + "_" + border.getTile() + "_" + border.getDirection();
            buttons.add(Buttons.red(id, "Remove from " + border.getTile() + " " + dir));
        }
        return buttons;
    }

    public List<Button> getInitialVoidTetherButtons(Game game, Player player, String pos) {
        List<Button> buttons = getRemoveVoidTetherButtons(game, player, pos);
        // If there are Tethers left in reinforcements...
        if (buttons.size() < 2) {
            String prefixNew = player.finChecker() + "newVoidTether_" + pos;
            buttons.add(Buttons.green(prefixNew, "Use new token"));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("No thanks"));
        return buttons;
    }

    public void fixVoidTether(Game game, Player empyrean) {
        if (!empyrean.hasUnlockedBreakthrough("empyreanbt")) return;
        String finChecker = empyrean.finChecker();

        List<Button> buttons = new ArrayList<>();
        buttons.addAll(getRemoveVoidTetherButtons(game, empyrean, null));
        buttons.add(Buttons.blue(finChecker + "addVoidTetherStep1", "Add a void tether", FactionEmojis.Empyrean));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);

        String message = "Use the buttons to interact with Void Tether.";
        MessageHelper.sendMessageToChannelWithButtons(empyrean.getCorrectChannel(), message, buttons);
    }

    private List<Button> getPlaceVoidTetherButtons(Game game, Player player, String pos) {
        List<Button> buttons = new ArrayList<>();
        String prefix = player.finChecker() + "placeVoidTether_" + pos + "_";

        List<String> directlyAdjacentTiles = PositionMapper.getAdjacentTilePositions(pos);
        if (directlyAdjacentTiles == null || directlyAdjacentTiles.size() != 6) {
            return buttons; // this tile doesn't have anything map-adjacent
        }
        for (int dir = 0; dir < 6; dir++) {
            int dirFrom = (dir + 3) % 6;
            String position_ = directlyAdjacentTiles.get(dir);
            boolean alreadyHas = false;
            for (BorderAnomalyHolder b : getTethersOnMap(game)) {
                if (b == null || b.getTile() == null) continue;
                if (b.getTile().equals(pos) && b.getDirection() == dir) alreadyHas = true;
                if (b.getTile().equals(position_) && b.getDirection() == dirFrom) alreadyHas = true;
            }
            if (alreadyHas) continue;

            String override = game.getAdjacentTileOverride(pos, dir);
            if (override != null) position_ = override;
            if ("x".equals(position_)) continue;
            if (game.getTileByPosition(position_) == null) continue;
            String tileBlocked = game.getTileByPosition(position_).getRepresentationForButtons(game, player);
            // there is a tile that exists in that direction
            buttons.add(Buttons.blue(prefix + dir, directionStr(dir) + ", blocking " + tileBlocked));
        }
        return buttons;
    }

    private String getVoidTetherBetweenText(Game game, Player player, String position, int direction) throws Exception {
        Tile t1 = game.getTileByPosition(position);
        Tile t2 = game.getTileByPosition(
                PositionMapper.getAdjacentTilePositions(position).get(direction));
        if (t1 == null || t2 == null) {
            RegexService.throwFailure();
            return null;
        }
        return t1.getRepresentationForButtons(game, player) + " and " + t2.getRepresentationForButtons(game, player);
    }

    @ButtonHandler("moveVoidTether_")
    private void moveVoidTether(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String regex = "moveVoidTether_" + RegexHelper.posRegex(game, "pos") + "_"
                + RegexHelper.posRegex(game, "removepos") + "_" + RegexHelper.intRegex("removedir");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String position = matcher.group("pos");
            String removePos = matcher.group("removepos");
            int removeDir = Integer.parseInt(matcher.group("removedir"));
            game.removeBorderAnomaly(removePos, removeDir);
            List<Button> buttons = getPlaceVoidTetherButtons(game, player, position);
            String message = "Removed Void Tether token from between "
                    + getVoidTetherBetweenText(game, player, removePos, removeDir) + ".";
            message += "\nUse the buttons to decide where to put your new token, relative to the active system ("
                    + position + "):";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("newVoidTether_")
    private void newVoidTether(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String regex = "newVoidTether_" + RegexHelper.posRegex(game, "pos");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String position = matcher.group("pos");
            List<Button> buttons = getPlaceVoidTetherButtons(game, player, position);
            String message = "Use buttons to decide where to put your new token, relative to the active system ("
                    + position + "):";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("placeVoidTether_")
    private void placeVoidTether(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String regex = "placeVoidTether_" + RegexHelper.posRegex("pos") + "_" + RegexHelper.intRegex("dir");
        Pattern pattern = Pattern.compile(regex);
        RegexService.runMatcher(pattern, buttonID, matcher -> {
            String position = matcher.group("pos");
            int direction = Integer.parseInt(matcher.group("dir"));
            game.addBorderAnomaly(position, direction, BorderAnomalyType.VOID_TETHER);
            String msg = String.format(
                    "%s placed a %s token between %s.",
                    player.getRepresentationNoPing(),
                    tetherRep(),
                    getVoidTetherBetweenText(game, player, position, direction));
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            ButtonHelper.deleteMessage(event);
        });
    }

    // Commands for manual interaction

    @ButtonHandler("removeVoidTether_")
    private void removeVoidTether(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex =
                "removeVoidTether_" + RegexHelper.posRegex(game, "removepos") + "_" + RegexHelper.intRegex("removedir");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String removePos = matcher.group("removepos");
            int removeDir = Integer.parseInt(matcher.group("removedir"));
            game.removeBorderAnomaly(removePos, removeDir);
            String message = "Removed Void Tether token from between "
                    + getVoidTetherBetweenText(game, player, removePos, removeDir) + ".";

            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("addVoidTetherStep1")
    private void addVoidTether(ButtonInteractionEvent event, Game game, Player player) {
        String prefix = "addVoidTetherStep2";
        List<Button> buttons = ButtonHelper.getTilesWithShipsForAction(player, game, prefix, false);
        String message = "Choose a system with your ships that you want to place a Void Tether token near:";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("addVoidTetherStep2_")
    private void addVoidTether(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "addVoidTetherStep2_" + RegexHelper.posRegex(game, "pos");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String position = matcher.group("pos");
            List<Button> buttons = new ArrayList<>();
            for (String pos : FoWHelper.getAdjacentTiles(game, position, player, false, true)) {
                Tile tile = game.getTileByPosition(pos);
                if (tile == null) continue;
                String id = "addVoidTetherStep3_" + pos;
                String label = tile.getRepresentationForButtons(game, player);
                buttons.add(Buttons.blue(id, label, FactionEmojis.Empyrean));
            }
            String message = "Use the buttons to decide where to put your Void Tether token:";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("addVoidTetherStep3_")
    private void addVoidTetherPos(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "addVoidTetherStep3_" + RegexHelper.posRegex(game, "pos");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String position = matcher.group("pos");
            Tile tile = game.getTileByPosition(position);
            if (tile != null) {
                postInitialButtons(game, player, tile);
            }
            ButtonHelper.deleteMessage(event);
        });
    }
}
