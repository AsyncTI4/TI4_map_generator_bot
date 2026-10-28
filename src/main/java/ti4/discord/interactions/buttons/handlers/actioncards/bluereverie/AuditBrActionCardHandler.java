package ti4.discord.interactions.buttons.handlers.actioncards.bluereverie;

import java.util.ArrayList;
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
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.service.actioncard.ForceGiveActionCardService;
import ti4.service.fow.PlanetTargetService;
import ti4.service.fow.PlanetTargetService.PlanetTargetSpec;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class AuditBrActionCardHandler {
    private static final String RESOLVE = "resolveAuditBr";
    private static final String SELECT_FIRST = "auditFirstPlayer_";
    private static final String SELECT_SECOND = "auditSecondPlayer_";
    private static final String GIVE_ACTION_CARD = "auditGiveActionCard_";
    private static final String PLACE_INFANTRY = "auditPlaceInfantry_";

    public static void offerResolution(Game game, Player auditPlayer, String message) {
        MessageHelper.sendMessageToChannelWithButtons(
                auditPlayer.getCorrectChannel(),
                message,
                List.of(Buttons.green(auditPlayer.factionButtonChecker() + RESOLVE, "Resolve Audit")));
    }

    @ButtonHandler(RESOLVE)
    public static void resolveAudit(ButtonInteractionEvent event, Game game, Player auditPlayer) {
        List<Button> buttons = getPlayerButtons(game, auditPlayer, null, SELECT_FIRST);
        if (buttons.size() < 2) {
            MessageHelper.sendMessageToChannel(
                    auditPlayer.getCorrectChannel(),
                    auditPlayer.getRepresentationNoPing() + " has fewer than two other players to select for _Audit_.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.editMessageWithButtons(
                event, selectFirstMessage(auditPlayer), paginatedPlayerButtons(auditPlayer, buttons, SELECT_FIRST, 0));
    }

    @ButtonHandler(SELECT_FIRST)
    public static void selectFirstPlayer(ButtonInteractionEvent event, Game game, Player auditPlayer, String buttonID) {
        List<Button> buttons = getPlayerButtons(game, auditPlayer, null, SELECT_FIRST);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                selectFirstMessage(auditPlayer),
                auditPlayer.factionButtonChecker() + SELECT_FIRST,
                buttonID)) {
            return;
        }
        Player firstPlayer = game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_FIRST.length()));
        if (!isEligibleTarget(auditPlayer, firstPlayer)) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That player is no longer eligible for _Audit_.");
            return;
        }
        List<Button> secondButtons =
                getPlayerButtons(game, auditPlayer, firstPlayer, SELECT_SECOND + firstPlayer.getFaction() + "|");
        MessageHelper.editMessageWithButtons(
                event,
                selectSecondMessage(auditPlayer),
                paginatedPlayerButtons(auditPlayer, secondButtons, SELECT_SECOND + firstPlayer.getFaction() + "|", 0));
    }

    @ButtonHandler(SELECT_SECOND)
    public static void selectSecondPlayer(
            ButtonInteractionEvent event, Game game, Player auditPlayer, String buttonID) {
        String[] values = buttonID.substring(SELECT_SECOND.length()).split("\\|", 2);
        Player firstPlayer = values.length == 2 ? game.getPlayerFromColorOrFaction(values[0]) : null;
        List<Button> buttons = getPlayerButtons(game, auditPlayer, firstPlayer, SELECT_SECOND + values[0] + "|");
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                selectSecondMessage(auditPlayer),
                auditPlayer.factionButtonChecker() + SELECT_SECOND + values[0] + "|",
                buttonID)) {
            return;
        }
        Player secondPlayer = values.length == 2 ? game.getPlayerFromColorOrFaction(values[1]) : null;
        if (!isEligibleTarget(auditPlayer, firstPlayer)
                || !isEligibleTarget(auditPlayer, secondPlayer)
                || firstPlayer == secondPlayer) {
            MessageHelper.sendEphemeralMessageToEventChannel(
                    event, "Those players are no longer eligible for _Audit_.");
            return;
        }
        sendChoiceButtons(game, auditPlayer, firstPlayer);
        sendChoiceButtons(game, auditPlayer, secondPlayer);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GIVE_ACTION_CARD)
    public static void giveActionCard(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        Player auditPlayer = game.getPlayerFromColorOrFaction(buttonID.substring(GIVE_ACTION_CARD.length()));
        if (auditPlayer == null
                || auditPlayer == target
                || target.getActionCards().isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        ForceGiveActionCardService.sendGiveACButtons(
                auditPlayer,
                target,
                game,
                target.getRepresentationNoPing() + ", choose 1 action card to give to "
                        + auditPlayer.getRepresentationNoPing() + " for _Audit_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(PLACE_INFANTRY)
    public static void selectInfantryPlanet(ButtonInteractionEvent event, Game game, Player target, String buttonID) {
        String payload = buttonID.substring(PLACE_INFANTRY.length());
        int separator = payload.indexOf('|');
        String auditPlayerID = separator < 0 ? payload : payload.substring(0, separator);
        String planetName = separator < 0 ? "" : payload.substring(separator + 1);
        Player auditPlayer = game.getPlayerFromColorOrFaction(auditPlayerID);
        if (auditPlayer == null || auditPlayer == target) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That planet is no longer eligible for _Audit_.");
            return;
        }
        PlanetTargetSpec planetTargetSpec = auditPlanetSpec(target, auditPlayerID);
        if (PlanetTargetService.handlePlanetPage(event, game, target, buttonID, planetTargetSpec)) {
            return;
        }
        if (planetName.startsWith("_")) {
            planetName = planetName.substring(1);
        }
        List<Button> buttons = getPlanetButtons(game, target, auditPlayer);
        String message = placeInfantryMessage(target, auditPlayer);
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                buttons,
                message,
                target.factionButtonChecker() + PLACE_INFANTRY + auditPlayerID + "|",
                buttonID)) {
            return;
        }
        if (planetName.isEmpty()) {
            MessageHelper.editMessageWithButtons(
                    event,
                    message,
                    NewStuffHelper.buttonPagination(
                            buttons, target.factionButtonChecker() + PLACE_INFANTRY + auditPlayerID + "|", 0));
            return;
        }
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null || tile == null) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "That planet is no longer eligible for _Audit_.");
            return;
        }
        String previousCoexistenceFlag = game.getStoredValue("coexistFlag");
        game.setStoredValue("coexistFlag", "yes");
        try {
            AddUnitService.addUnits(event, tile, game, auditPlayer.getColor(), "2 infantry " + planetName);
        } finally {
            if (previousCoexistenceFlag.isEmpty()) {
                game.removeStoredValue("coexistFlag");
            } else {
                game.setStoredValue("coexistFlag", previousCoexistenceFlag);
            }
        }
        ButtonHelperAbilities.oceanBoundCheck(game);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + " placed 2 of " + auditPlayer.getRepresentationNoPing()
                        + "'s infantry into coexistence on " + Helper.getPlanetRepresentation(planetName, game)
                        + " for _Audit_.");
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getPlayerButtons(Game game, Player auditPlayer, Player excludedPlayer, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (Player target : game.getRealPlayers()) {
            if (!isEligibleTarget(auditPlayer, target) || target == excludedPlayer) continue;
            buttons.add(FoWHelper.fogSafeTargetButton(
                    auditPlayer.factionButtonChecker() + prefix + target.getFaction(), "gray", target));
        }
        return buttons;
    }

    private static List<Button> paginatedPlayerButtons(
            Player auditPlayer, List<Button> buttons, String prefix, int page) {
        return NewStuffHelper.buttonPagination(buttons, auditPlayer.factionButtonChecker() + prefix, page);
    }

    private static void sendChoiceButtons(Game game, Player auditPlayer, Player target) {
        List<Button> buttons = new ArrayList<>();
        if (!target.getActionCards().isEmpty()) {
            buttons.add(Buttons.green(
                    target.factionButtonChecker() + GIVE_ACTION_CARD + auditPlayer.getFaction(), "Give 1 Action Card"));
        }
        if (!getPlanetButtons(game, target, auditPlayer).isEmpty()) {
            buttons.add(Buttons.blue(
                    target.factionButtonChecker() + PLACE_INFANTRY + auditPlayer.getFaction() + "|",
                    "Place 2 Infantry"));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    target.getCorrectChannel(),
                    target.getRepresentationNoPing() + " has no available way to resolve _Audit_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + ", choose how to resolve _Audit_ for "
                        + auditPlayer.getRepresentationNoPing() + ".",
                buttons);
    }

    private static List<Button> getPlanetButtons(Game game, Player target, Player auditPlayer) {
        if (auditPlayer == null) return List.of();
        List<Button> buttons = new ArrayList<>();
        for (String planetName : target.getPlanets()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet == null || game.getTileFromPlanet(planetName) == null) continue;
            buttons.add(Buttons.green(
                    target.factionButtonChecker() + PLACE_INFANTRY + auditPlayer.getFaction() + "|" + planetName,
                    Helper.getPlanetRepresentation(planetName, game)));
        }
        return PlanetTargetService.targetButtons(
                game, target, auditPlanetSpec(target, auditPlayer.getFaction()), buttons);
    }

    private static PlanetTargetSpec auditPlanetSpec(Player target, String auditPlayerID) {
        return PlanetTargetSpec.of(target.factionButtonChecker() + PLACE_INFANTRY + auditPlayerID + "|")
                .selfOnly();
    }

    private static boolean isEligibleTarget(Player auditPlayer, Player target) {
        return target != null && target != auditPlayer && !target.isEliminated();
    }

    private static String selectFirstMessage(Player auditPlayer) {
        return auditPlayer.getRepresentationNoPing() + ", choose the first player for _Audit_.";
    }

    private static String selectSecondMessage(Player auditPlayer) {
        return auditPlayer.getRepresentationNoPing() + ", choose the second player for _Audit_.";
    }

    private static String placeInfantryMessage(Player target, Player auditPlayer) {
        return target.getRepresentationNoPing() + ", choose a planet you control to place 2 of "
                + auditPlayer.getRepresentationNoPing() + "'s infantry into coexistence for _Audit_.";
    }
}
