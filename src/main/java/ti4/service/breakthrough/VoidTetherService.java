package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.RegexHelper;
import ti4.helpers.adjacency.Adjacency;
import ti4.message.MessageHelper;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyIds;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;
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

        Set<String> adjTiles = Adjacency.of(game, player).withSelf().into(activeSystem.getPosition());
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
                + ", please use these buttons to place or move a Void Tether token using your breakthrough.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    private List<BorderAnomalyHolder> getTethersOnMap(Game game) {
        List<BorderAnomalyHolder> tethers = new ArrayList<>();
        for (BorderAnomalyHolder b : game.getBorderAnomalies()) {
            if (b != null && b.isType(BorderAnomalyIds.VOID_TETHER)) {
                tethers.add(b);
            }
        }
        return tethers;
    }

    public List<Button> getRemoveVoidTetherButtons(Game game, Player player, String pos) {
        List<Button> buttons = new ArrayList<>();
        String prefixRemove = player.factionButtonChecker() + "moveVoidTether_" + pos;
        if (pos == null) {
            prefixRemove = player.factionButtonChecker() + "removeVoidTether";
        }
        for (BorderAnomalyHolder border : getTethersOnMap(game)) {
            String dir = "(" + directionStr(border.getDirection()) + ")";
            String id = prefixRemove + "_" + border.getTile() + "_" + border.getDirection();
            buttons.add(Buttons.red(id, "Remove From " + border.getTile() + " " + dir));
        }
        return buttons;
    }

    public List<Button> getInitialVoidTetherButtons(Game game, Player player, String pos) {
        List<Button> buttons = getRemoveVoidTetherButtons(game, player, pos);
        // If there are Tethers left in reinforcements...
        if (buttons.size() < 2) {
            String prefixNew = player.factionButtonChecker() + "newVoidTether_" + pos;
            buttons.add(Buttons.green(prefixNew, "Use New Token"));
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS.withLabel("No Thanks"));
        return buttons;
    }

    public void fixVoidTether(Game game, Player empyrean) {
        if (!empyrean.hasUnlockedBreakthrough("empyreanbt")) return;
        String factionChecker = empyrean.factionButtonChecker();

        List<Button> buttons = new ArrayList<>(getRemoveVoidTetherButtons(game, empyrean, null));
        buttons.add(Buttons.blue(factionChecker + "addVoidTetherStep1", "Add Void Tether", FactionEmojis.Empyrean));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);

        String message = "Use the buttons to interact with your Void Tether tokens.";
        MessageHelper.sendMessageToChannelWithButtons(empyrean.getCorrectChannel(), message, buttons);
    }

    private List<Button> getPlaceVoidTetherButtons(Game game, Player player, String pos) {
        List<Button> buttons = new ArrayList<>();
        String prefix = player.factionButtonChecker() + "placeVoidTether_" + pos + "_";

        List<String> directlyAdjacentTiles = MapPositionMapper.getAdjacentTilePositions(pos);
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
            buttons.add(Buttons.blue(prefix + dir, directionStr(dir) + ", Blocking " + tileBlocked));
        }
        return buttons;
    }

    private String getVoidTetherBetweenText(Game game, Player player, String position, int direction) throws Exception {
        Tile t1 = game.getTileByPosition(position);
        Tile t2 = game.getTileByPosition(
                MapPositionMapper.getAdjacentTilePositions(position).get(direction));
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
            message +=
                    "\nPlease use these buttons to choose where to put your new token, relative to the active system ("
                            + position + ").";
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
            String message =
                    "Please use these buttons to choose where to put your new token, relative to the active system ("
                            + position + ").";
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
            game.addBorderAnomaly(position, direction, BorderAnomalyIds.VOID_TETHER);
            String msg = String.format(
                    "%s placed a %s token between %s.",
                    player.getRepresentationNoPing(),
                    tetherRep(),
                    getVoidTetherBetweenText(game, player, position, direction));
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
            BorderAnomalyHolder placed = findTether(game, position, direction);
            if (placed == null || !placed.isEnforced(game)) {
                ButtonHelper.deleteMessage(event);
                return;
            }
            List<Button> buttons = List.of(
                    Buttons.gray(
                            player.factionButtonChecker() + "voidTetherAccess_" + position + "_" + direction,
                            "Allow Players Through"),
                    Buttons.DONE_DELETE_BUTTONS.withLabel("No Thanks"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    "Other players treat those systems as not adjacent unless you allow them through.",
                    buttons);
            ButtonHelper.deleteMessage(event);
        });
    }

    private List<Button> getAccessButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (BorderAnomalyHolder border : getTethersOnMap(game)) {
            if (!border.isEnforced(game)) continue;
            String id = player.factionButtonChecker() + "voidTetherAccess_" + border.getTile() + "_"
                    + border.getDirection();
            buttons.add(
                    Buttons.gray(id, "Access " + border.getTile() + " (" + directionStr(border.getDirection()) + ")"));
        }
        return buttons;
    }

    public boolean hasEnforcedTether(Game game) {
        return getTethersOnMap(game).stream().anyMatch(border -> border.isEnforced(game));
    }

    @ButtonHandler("voidTetherAccessMenu")
    private void voidTetherAccessMenu(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getAccessButtons(game, player);
        if (buttons.isEmpty()) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "You have no enforced Void Tether tokens on the map.");
            return;
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(), "Choose a " + tetherRep() + " token to open or close.", buttons);
    }

    private BorderAnomalyHolder findTether(Game game, String position, int direction) {
        return getTethersOnMap(game).stream()
                .filter(border -> position.equals(border.getTile()) && border.getDirection() == direction)
                .findFirst()
                .orElse(null);
    }

    private void postAccessButtons(Game game, Player player, BorderAnomalyHolder tether) throws Exception {
        String suffix = tether.getTile() + "_" + tether.getDirection() + "_";
        List<Button> buttons = new ArrayList<>();
        for (Player other : game.getRealPlayersExcludingThis(player)) {
            String label = other.getFactionNameOrColor();
            if (tether.isAllowed(other.getColor())) {
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + "voidTetherRevoke_" + suffix + other.getColor(),
                        "Revoke " + label));
            } else {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + "voidTetherAllow_" + suffix + other.getColor(),
                        "Allow " + label));
            }
        }
        buttons.add(Buttons.DONE_DELETE_BUTTONS);
        String message = "Choose who may treat "
                + getVoidTetherBetweenText(game, player, tether.getTile(), tether.getDirection())
                + " as adjacent. Allowances end when this " + tetherRep() + " token is moved or removed.";
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }

    @ButtonHandler("voidTetherAccess_")
    private void voidTetherAccess(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "voidTetherAccess_" + RegexHelper.posRegex(game, "pos") + "_" + RegexHelper.intRegex("dir");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            BorderAnomalyHolder tether = findTether(game, matcher.group("pos"), Integer.parseInt(matcher.group("dir")));
            if (tether == null) {
                MessageHelper.sendEphemeralMessageToEventChannel(event, "That Void Tether token is no longer there.");
                return;
            }
            postAccessButtons(game, player, tether);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("voidTetherAllow_")
    private void voidTetherAllow(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        changeAccess(event, game, player, buttonID, "voidTetherAllow_", true);
    }

    @ButtonHandler("voidTetherRevoke_")
    private void voidTetherRevoke(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        changeAccess(event, game, player, buttonID, "voidTetherRevoke_", false);
    }

    private void changeAccess(
            ButtonInteractionEvent event, Game game, Player player, String buttonID, String prefix, boolean allow) {
        String regex = prefix + RegexHelper.posRegex(game, "pos") + "_" + RegexHelper.intRegex("dir") + "_"
                + RegexHelper.colorRegex(game, "color");
        RegexService.runMatcher(regex, buttonID, matcher -> {
            BorderAnomalyHolder tether = findTether(game, matcher.group("pos"), Integer.parseInt(matcher.group("dir")));
            Player other = game.getPlayerFromColorOrFaction(matcher.group("color"));
            if (tether == null || other == null) {
                MessageHelper.sendEphemeralMessageToEventChannel(event, "That Void Tether token is no longer there.");
                return;
            }
            if (allow) {
                tether.allow(other.getColor());
            } else {
                tether.revoke(other.getColor());
            }
            String verb = allow ? "allowed" : "no longer allows";
            String between = getVoidTetherBetweenText(game, player, tether.getTile(), tether.getDirection());
            String otherName = game.isFowMode() ? other.getFactionNameOrColor() : other.getRepresentation();
            String holderMessage = player.getRepresentationNoPing() + " " + verb + " " + otherName + " through the "
                    + tetherRep() + " between " + between + ".";
            String otherMessage = other.getRepresentation() + ", "
                    + FoWHelper.identityOrColorIfCanSeeStats(game, player, other, player.getRepresentationNoPing())
                    + " " + verb + " you through the " + tetherRep() + " between "
                    + getVoidTetherBetweenText(game, other, tether.getTile(), tether.getDirection()) + ".";
            FoWHelper.notifyPlayerAndAffectedInFog(game, player, holderMessage, other, otherMessage);
            postAccessButtons(game, player, tether);
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
        String message = "Please choose a system with your ships that you want to place a Void Tether token near.";
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
            String message = "Please use these buttons to decide where to put your Void Tether token.";
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
