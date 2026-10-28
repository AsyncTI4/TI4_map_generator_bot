package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.message.MessageHelper;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class DubiousOccultistBRActionCardHandler {
    private static final String RESOLVE = "resolveOccultistsBR";
    private static final String GAIN_COMMODITIES = "occultistGainCommodities";
    private static final String SELECT_SYSTEM = "occultistSystem_";
    private static final String SELECT_UNIT = "occultistUnit_";
    private static final String SELECT_INGRESS = "occultistIngress_";

    public static void offerResolution(Game game, Player player, String message) {
        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                message,
                Buttons.green(player.factionButtonChecker() + RESOLVE, "Resolve Dubious Occultist"));
    }

    @ButtonHandler(RESOLVE)
    public static void resolveOccultist(ButtonInteractionEvent event, Game game, Player player) {
        List<Button> buttons = getSystemButtons(game, player);
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " has no units to destroy for _Dubious Occultist_.");
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", choose a system containing a unit to destroy for _Dubious Occultist_:",
                NewStuffHelper.buttonPagination(buttons, player.factionButtonChecker() + SELECT_SYSTEM, 0));
    }

    @ButtonHandler(GAIN_COMMODITIES)
    public static void gainCommodities(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(GAIN_COMMODITIES.length()).split("\\|", 4);
        UnitSelection selection = getUnitSelection(game, player, values);
        if (selection == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        DestroyUnitService.destroyUnit(
                event, selection.tile(), game, selection.unitKey(), 1, selection.unitHolder(), false);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " destroyed 1 "
                        + selection.unitKey().humanReadableName() + " for _Dubious Occultist_.");
        ButtonHelperStats.gainComms(event, game, player, 2, true);
    }

    @ButtonHandler(SELECT_SYSTEM)
    public static void selectSystem(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        List<Button> buttons = getSystemButtons(game, player);
        String message = player.getRepresentationNoPing()
                + ", choose a system containing a unit to destroy for _Dubious Occultist_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + SELECT_SYSTEM,
                buttonID)) {
            return;
        }
        String position = buttonID.substring(SELECT_SYSTEM.length());
        Tile tile = game.getTileByPosition(position);
        if (tile == null || !hasPlayerUnit(tile, player)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> unitButtons = getUnitButtons(player, tile);
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose a unit to destroy in "
                        + tile.getRepresentationForButtons(game, player) + " for _Dubious Occultist_:",
                NewStuffHelper.buttonPagination(
                        unitButtons, player.factionButtonChecker() + SELECT_UNIT + position + "|", 0));
    }

    @ButtonHandler(SELECT_UNIT)
    public static void selectUnit(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_UNIT.length()).split("\\|", 4);
        Tile tile = values.length == 4 ? game.getTileByPosition(values[0]) : null;
        List<Button> buttons = tile == null ? List.of() : getUnitButtons(player, tile);
        String message = tile == null
                ? player.getRepresentationNoPing() + ", choose a unit to destroy for _Dubious Occultist_."
                : player.getRepresentationNoPing() + ", choose a unit to destroy in "
                        + tile.getRepresentationForButtons(game, player) + " for _Dubious Occultist_.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                player.factionButtonChecker() + SELECT_UNIT + values[0] + "|",
                buttonID)) {
            return;
        }
        UnitSelection selection = getUnitSelection(game, player, values);
        if (selection == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> ingressButtons = getIngressButtons(game, player, values);
        List<Button> resolutionButtons = new ArrayList<>();
        resolutionButtons.add(Buttons.green(
                player.factionButtonChecker() + GAIN_COMMODITIES + String.join("|", values),
                "Destroy Unit and Gain 2 Commodities"));
        resolutionButtons.addAll(ingressButtons);
        if (resolutionButtons.size() == 1) {
            MessageHelper.editMessageWithButtons(
                    event,
                    player.getRepresentationNoPing()
                            + ", there are no eligible ingress tokens. Destroy the selected unit to gain 2 commodities:",
                    resolutionButtons);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing()
                        + ", choose how to resolve _Dubious Occultist_ after destroying the selected unit:",
                resolutionButtons);
    }

    @ButtonHandler(SELECT_INGRESS)
    public static void moveIngressAndDestroyUnit(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_INGRESS.length()).split("\\|", 5);
        UnitSelection selection = getUnitSelection(game, player, values);
        Tile ingressTile = values.length == 5 ? game.getTileByPosition(values[4]) : null;
        if (selection == null
                || ingressTile == null
                || !ingressTile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        DestroyUnitService.destroyUnit(
                event, selection.tile(), game, selection.unitKey(), 1, selection.unitHolder(), false);
        ingressTile.getSpaceUnitHolder().removeToken(Constants.TOKEN_INGRESS);
        selection.tile().getSpaceUnitHolder().addToken(Constants.TOKEN_INGRESS);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " destroyed 1 "
                        + selection.unitKey().humanReadableName()
                        + " and moved an ingress token into "
                        + selection.tile().getRepresentationForButtons(game, player) + " for _Dubious Occultist_.");
    }

    private static List<Button> getSystemButtons(Game game, Player player) {
        return game.getTileMap().values().stream()
                .filter(tile -> hasPlayerUnit(tile, player))
                .map(tile -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_SYSTEM + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static List<Button> getUnitButtons(Player player, Tile tile) {
        List<Button> buttons = new ArrayList<>();
        for (UnitHolder holder : tile.getUnitHolders().values()) {
            for (UnitKey unitKey : holder.getUnitKeysForPlayer(player)) {
                if (holder.getUnitCount(unitKey) < 1) {
                    continue;
                }
                buttons.add(Buttons.red(
                        player.factionButtonChecker() + SELECT_UNIT + tile.getPosition() + "|" + holder.getName() + "|"
                                + unitKey.colorID() + "|" + unitKey.asyncID(),
                        "Destroy " + unitKey.humanReadableName(),
                        unitKey.unitEmoji()));
            }
        }
        return buttons;
    }

    private static List<Button> getIngressButtons(Game game, Player player, String[] unitValues) {
        if (getUnitSelection(game, player, unitValues) == null) {
            return List.of();
        }
        return game.getTileMap().values().stream()
                .filter(tile -> tile.getSpaceUnitHolder().getTokenList().contains(Constants.TOKEN_INGRESS))
                .filter(tile ->
                        !game.isFowMode() || FoWHelper.fowFilter(game, player).contains(tile.getPosition()))
                .map(tile -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_INGRESS + String.join("|", unitValues) + "|"
                                + tile.getPosition(),
                        tile.getRepresentationForButtons(game, player)))
                .toList();
    }

    private static UnitSelection getUnitSelection(Game game, Player player, String[] values) {
        if (values.length < 4) {
            return null;
        }
        Tile tile = game.getTileByPosition(values[0]);
        UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
        UnitKey unitKey = Units.getUnitKey(values[3], values[2]);
        if (holder == null
                || unitKey == null
                || !player.unitBelongsToPlayer(unitKey)
                || holder.getUnitCount(unitKey) < 1) {
            return null;
        }
        return new UnitSelection(tile, holder, unitKey);
    }

    private static boolean hasPlayerUnit(Tile tile, Player player) {
        return tile.getUnitHolders().values().stream()
                .flatMap(holder -> holder.getUnitKeysForPlayer(player).stream())
                .anyMatch(unitKey -> player.unitBelongsToPlayer(unitKey));
    }

    private record UnitSelection(Tile tile, UnitHolder unitHolder, UnitKey unitKey) {}
}
