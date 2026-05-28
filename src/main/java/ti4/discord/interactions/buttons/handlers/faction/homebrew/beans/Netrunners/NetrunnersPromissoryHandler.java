package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Netrunners;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
import ti4.helpers.Helper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.UnitEmojis;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class NetrunnersPromissoryHandler {

    private static final String SHARED_NETWORK_ACCESS_ID = "bepnnetrunners";
    private static final String START_BUTTON_ID = "netrunnersSharedNetworkAccessStart";
    private static final String CHOOSE_SYSTEM_PREFIX = "netrunnersSharedNetworkAccessChoose_";
    private static final String PLACE_UNIT_PREFIX = "netrunnersSharedNetworkAccessPlace_";
    private static final String FINISH_BUTTON_ID = "netrunnersSharedNetworkAccessFinish";

    public static boolean shouldOfferSharedNetworkAccessButtons(Player activePlayer, Game game) {
        Player owner = game.getPNOwner(SHARED_NETWORK_ACCESS_ID);
        if (owner != activePlayer) {
            return false;
        }

        return game.getRealPlayers().stream()
                .anyMatch(holder -> holder != activePlayer && holder.getPromissoryNotes().containsKey(SHARED_NETWORK_ACCESS_ID));
    }

    public static void offerSharedNetworkAccessButtons(Player activePlayer, Game game) {
        for (Player holder : game.getRealPlayers()) {
            if (holder == activePlayer || !holder.getPromissoryNotes().containsKey(SHARED_NETWORK_ACCESS_ID)) {
                continue;
            }

            String msg = holder.getRepresentationUnfogged() + ", the "
                    + (game.isFrankenGame() ? "_Shared Network Access_ owner" : "Net-Runners player")
                    + " has started their turn. You may choose 1 eligible system, place up to 2 infantry and up to 2 fighters there, then return _Shared Network Access_.";
            List<Button> buttons = new ArrayList<>();
            buttons.add(Buttons.green(
                    holder.factionButtonChecker() + START_BUTTON_ID,
                    "Resolve Shared Network Access",
                    FactionEmojis.netrunners));
            buttons.add(Buttons.red(holder.factionButtonChecker() + FINISH_BUTTON_ID, "Return Shared Network Access"));
            MessageHelper.sendMessageToChannelWithButtons(holder.getCardsInfoThread(), msg, buttons);
        }
    }

    @ButtonHandler(START_BUTTON_ID)
    public static void startSharedNetworkAccess(ButtonInteractionEvent event, Player player, Game game) {
        if (!canResolveSharedNetworkAccess(player, game)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You cannot resolve _Shared Network Access_ right now.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        ButtonHelper.deleteMessage(event);
        showSystemButtons(event.getMessageChannel(), player, game, 2, 2);
    }

    @ButtonHandler(CHOOSE_SYSTEM_PREFIX)
    public static void chooseSharedNetworkAccessSystem(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (!canResolveSharedNetworkAccess(player, game)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You cannot resolve _Shared Network Access_ right now.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(CHOOSE_SYSTEM_PREFIX.length());
        String[] parts = payload.split("_", 3);
        int remainingInfantry = Integer.parseInt(parts[0]);
        int remainingFighters = Integer.parseInt(parts[1]);
        String position = parts[2];
        Tile tile = game.getTileByPosition(position);

        ButtonHelper.deleteMessage(event);
        if (tile == null) {
            showSystemButtons(event.getMessageChannel(), player, game, remainingInfantry, remainingFighters);
            return;
        }

        showPlacementButtons(event.getMessageChannel(), player, game, tile, remainingInfantry, remainingFighters);
    }

    @ButtonHandler(PLACE_UNIT_PREFIX)
    public static void placeSharedNetworkAccessUnit(
            ButtonInteractionEvent event, Player player, Game game, String buttonID) {
        if (!canResolveSharedNetworkAccess(player, game)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "You cannot resolve _Shared Network Access_ right now.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String payload = buttonID.substring(PLACE_UNIT_PREFIX.length());
        String[] parts = payload.split("_", 4);
        int remainingInfantry = Integer.parseInt(parts[0]);
        int remainingFighters = Integer.parseInt(parts[1]);
        String position = parts[2];
        String targetAndUnitType = parts[3];
        int splitIndex = targetAndUnitType.lastIndexOf('_');
        String target = splitIndex > -1 ? targetAndUnitType.substring(0, splitIndex) : "space";
        String unitType = splitIndex > -1 ? targetAndUnitType.substring(splitIndex + 1) : targetAndUnitType;
        Tile tile = game.getTileByPosition(position);

        ButtonHelper.deleteMessage(event);
        if (tile == null) {
            returnSharedNetworkAccess(player, game, event.getMessageChannel());
            return;
        }

        if ("infantry".equals(unitType) && remainingInfantry > 0) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 gf " + target);
            remainingInfantry--;
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getFactionEmoji() + " placed 1 infantry on " + Helper.getPlanetRepresentation(target, game)
                            + " using _Shared Network Access_.");
        } else if ("fighter".equals(unitType) && remainingFighters > 0) {
            AddUnitService.addUnits(event, tile, game, player.getColor(), "1 ff");
            remainingFighters--;
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getFactionEmoji() + " placed 1 fighter in " + tile.getRepresentationForButtons(game, player)
                            + " using _Shared Network Access_.");
        }

        if (remainingInfantry == 0 && remainingFighters == 0) {
            returnSharedNetworkAccess(player, game, event.getMessageChannel());
            return;
        }

        showPlacementButtons(event.getMessageChannel(), player, game, tile, remainingInfantry, remainingFighters);
    }

    @ButtonHandler(FINISH_BUTTON_ID)
    public static void finishSharedNetworkAccess(ButtonInteractionEvent event, Player player, Game game) {
        if (!player.getPromissoryNotes().containsKey(SHARED_NETWORK_ACCESS_ID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        returnSharedNetworkAccess(player, game, event.getMessageChannel());
    }

    private static void showSystemButtons(
            net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel,
            Player player,
            Game game,
            int remainingInfantry,
            int remainingFighters) {
        List<Tile> eligibleTiles = getEligibleStructureTiles(game, player);
        if (eligibleTiles.isEmpty()) {
            returnSharedNetworkAccess(player, game, channel);
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (Tile tile : eligibleTiles) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + CHOOSE_SYSTEM_PREFIX + remainingInfantry + "_" + remainingFighters
                            + "_" + tile.getPosition(),
                    tile.getRepresentationForButtons(game, player),
                    FactionEmojis.netrunners));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + FINISH_BUTTON_ID, "Return Shared Network Access"));

        String msg = player.getRepresentationUnfogged() + ", choose a system containing one of your structures."
                + " Remaining: " + remainingInfantry + " infantry, " + remainingFighters + " fighters.";
        MessageHelper.sendMessageToChannelWithButtons(channel, msg, buttons);
    }

    private static void showPlacementButtons(
            net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel,
            Player player,
            Game game,
            Tile tile,
            int remainingInfantry,
            int remainingFighters) {
        List<Button> buttons = new ArrayList<>();
        if (remainingInfantry > 0) {
            for (String planet : getEligibleStructurePlanets(tile, player)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + PLACE_UNIT_PREFIX + remainingInfantry + "_" + remainingFighters
                                + "_" + tile.getPosition() + "_" + planet + "_infantry",
                        "Place 1 Infantry on " + Helper.getPlanetRepresentation(planet, game),
                        UnitEmojis.infantry));
            }
        }
        if (remainingFighters > 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PLACE_UNIT_PREFIX + remainingInfantry + "_" + remainingFighters
                            + "_" + tile.getPosition() + "_space_fighter",
                    "Place 1 Fighter",
                    UnitEmojis.fighter));
        }
        buttons.add(Buttons.red(player.factionButtonChecker() + FINISH_BUTTON_ID, "Return Shared Network Access"));

        String msg = player.getRepresentationUnfogged() + ", choose what to place in "
                + tile.getRepresentationForButtons(game, player) + ". Remaining: " + remainingInfantry + " infantry, "
                + remainingFighters + " fighters.";
        MessageHelper.sendMessageToChannelWithButtons(channel, msg, buttons);
    }

    private static List<Tile> getEligibleStructureTiles(Game game, Player player) {
        return ButtonHelper.getTilesOfPlayersSpecificUnits(game, player, UnitType.Pds, UnitType.Spacedock).stream()
                .sorted(Comparator.comparing(Tile::getPosition))
                .toList();
    }

    private static List<String> getEligibleStructurePlanets(Tile tile, Player player) {
        return tile.getPlanetUnitHolders().stream()
                .filter(planet -> planet.getUnitCount(UnitType.Pds, player.getColor()) > 0
                        || planet.getUnitCount(UnitType.Spacedock, player.getColor()) > 0)
                .map(Planet::getName)
                .sorted()
                .toList();
    }

    private static boolean canResolveSharedNetworkAccess(Player player, Game game) {
        Player owner = game.getPNOwner(SHARED_NETWORK_ACCESS_ID);
        return owner != null
                && game.getActivePlayer() == owner
                && player != owner
                && player.getPromissoryNotes().containsKey(SHARED_NETWORK_ACCESS_ID);
    }

    private static void returnSharedNetworkAccess(
            Player holder, Game game, net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel) {
        Player owner = game.getPNOwner(SHARED_NETWORK_ACCESS_ID);
        if (owner == null) {
            MessageHelper.sendMessageToChannel(
                    channel, "Could not return _Shared Network Access_ because it has no owner.");
            return;
        }

        holder.removePromissoryNote(SHARED_NETWORK_ACCESS_ID);
        owner.setPromissoryNote(SHARED_NETWORK_ACCESS_ID);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, holder, false);
        PromissoryNoteHelper.sendPromissoryNoteInfo(game, owner, false);

        MessageHelper.sendMessageToChannel(
                channel,
                holder.getRepresentationUnfogged() + " returned _Shared Network Access_ to "
                        + owner.getRepresentationNoPing() + ".");
    }
}
