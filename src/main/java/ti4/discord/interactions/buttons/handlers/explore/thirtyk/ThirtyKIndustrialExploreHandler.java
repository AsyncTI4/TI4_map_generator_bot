package ti4.discord.interactions.buttons.handlers.explore.thirtyk;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.AttachmentModel;
import ti4.model.Mapper;
import ti4.service.explore.ExploreService;
import ti4.service.unit.AddUnitService;
import ti4.service.unit.RemoveUnitService;

@UtilityClass
public class ThirtyKIndustrialExploreHandler {

    private static final String REPURPOSE_DOCK = "thirtyKRepurposeDock_";
    private static final String REPURPOSE_TG = "thirtyKRepurposeTg";
    private static final String MILITARY_DOCUMENTS_MECH = "thirtyKMilitaryDocsMech_";
    private static final String FORESIGHT_COMMODITIES = "thirtyKForesightComms";
    private static final String FORESIGHT_REVEAL = "thirtyKForesightReveal_";
    private static final String INDUSTRIAL = "industrial";

    public static void resolveRepurposedFacilities(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        List<Button> buttons = new ArrayList<>();
        Planet planet = game.getUnitHolderFromPlanet(planetID);
        if (planet != null && planet.getUnitCount(UnitType.Spacedock, player) == 0) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + REPURPOSE_DOCK + planetID, "Spend 5 Resources For A Space Dock"));
        }
        buttons.add(Buttons.blue(player.factionButtonChecker() + REPURPOSE_TG, "Gain 1 Trade Good"));
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + ", you may spend 5 resources to place a space dock on "
                        + Helper.getPlanetRepresentation(planetID, game) + "; if you don't, gain 1 trade good.",
                buttons);
    }

    @ButtonHandler(REPURPOSE_DOCK)
    public static void repurposeDock(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetID = buttonID.substring(REPURPOSE_DOCK.length());
        Tile tile = game.getTileFromPlanet(planetID);
        if (tile == null) return;
        AddUnitService.addUnits(event, tile, game, player.getColor(), "sd " + planetID);
        ButtonHelper.deleteMessage(event);
        sendSpendButtons(
                event,
                game,
                player,
                "res",
                "The old facilities roar back to life: " + player.getRepresentationNoPing() + " places a space dock on "
                        + Helper.getPlanetRepresentation(planetID, game)
                        + " (_Repurposed Facilities_). Please spend 5 resources.");
    }

    @ButtonHandler(REPURPOSE_TG)
    public static void repurposeTradeGood(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainTGs(event, game, player, 1, false);
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveMilitaryProceduralDocuments(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        Planet planet = game.getUnitHolderFromPlanet(planetID);
        if (planet == null) return;
        boolean anyMech = planet.getUnitKeys().stream()
                .anyMatch(key -> key.unitType() == UnitType.Mech && planet.getUnitCount(key) > 0);
        if (anyMech) {
            ButtonHelperStats.gainComms(event, game, player, 1, false);
            return;
        }
        if (planet.getUnitCount(UnitType.Infantry, player) == 0) {
            MessageHelper.sendMessageToEventChannel(
                    event, "There is no mech on this planet, but you have no infantry there to replace.");
            return;
        }
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation() + ", you may replace 1 infantry on "
                        + Helper.getPlanetRepresentation(planetID, game) + " with 1 mech.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + MILITARY_DOCUMENTS_MECH + planetID,
                                "Replace 1 Infantry With 1 Mech"),
                        ThirtyKExploreSupport.decline(player)));
    }

    @ButtonHandler(MILITARY_DOCUMENTS_MECH)
    public static void militaryDocumentsMech(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetID = buttonID.substring(MILITARY_DOCUMENTS_MECH.length());
        Tile tile = game.getTileFromPlanet(planetID);
        if (tile == null) return;
        RemoveUnitService.removeUnits(event, tile, game, player.getColor(), "1 inf " + planetID);
        AddUnitService.addUnits(event, tile, game, player.getColor(), "mech " + planetID);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Following the manual to the letter, " + player.getRepresentationNoPing()
                        + " refits 1 infantry into 1 mech on " + Helper.getPlanetRepresentation(planetID, game)
                        + " (_Military Procedural Documents_).");
        ButtonHelper.deleteMessage(event);
    }

    public static void resolveForesightOfIndustry(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        MessageHelper.sendMessageToEventChannelWithButtons(
                event,
                player.getRepresentation()
                        + ", gain 2 commodities, or spend 1 influence to reveal and resolve the next industrial"
                        + " exploration card on " + Helper.getPlanetRepresentation(planetID, game) + ".",
                List.of(
                        Buttons.green(player.factionButtonChecker() + FORESIGHT_COMMODITIES, "Gain 2 Commodities"),
                        Buttons.blue(
                                player.factionButtonChecker() + FORESIGHT_REVEAL + planetID,
                                "Spend 1 Influence To Explore Again")));
    }

    @ButtonHandler(FORESIGHT_COMMODITIES)
    public static void foresightCommodities(ButtonInteractionEvent event, Game game, Player player) {
        ButtonHelperStats.gainComms(event, game, player, 2, false);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(FORESIGHT_REVEAL)
    public static void foresightReveal(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetID = buttonID.substring(FORESIGHT_REVEAL.length());
        ButtonHelper.deleteMessage(event);
        sendSpendButtons(event, game, player, "inf", player.getRepresentationNoPing() + ", please spend 1 influence.");
        revealNextIndustrial(event, game, player, planetID);
    }

    public static void resolveEstablishedSurveying(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, String planetID) {
        int attachments = 0;
        for (Tile nearby : ThirtyKExploreSupport.tilesInOrAdjacent(game, player, tile)) {
            for (Planet planet : nearby.getPlanetUnitHolders()) {
                if (planet.getName().equals(planetID)) continue;
                attachments += countAttachments(planet);
            }
        }
        if (attachments > 0) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "Survey reports pour in from " + attachments + " attachment" + (attachments == 1 ? "" : "s")
                            + " on nearby planets: gain " + attachments + " commodit"
                            + (attachments == 1 ? "y" : "ies") + " (_Established Surveying_).");
            ButtonHelperStats.gainComms(event, game, player, attachments, false);
            return;
        }
        MessageHelper.sendMessageToEventChannel(
                event,
                "No nearby industry to report on, so the surveyors dig deeper: the next industrial exploration card is"
                        + " resolved (_Established Surveying_).");
        revealNextIndustrial(event, game, player, planetID);
    }

    private static int countAttachments(Planet planet) {
        int count = 0;
        for (String token : planet.getTokenList()) {
            AttachmentModel attachment = Mapper.getAttachmentInfo(token);
            if (attachment != null && attachment.isRealAttachment()) count++;
        }
        return count;
    }

    private static void revealNextIndustrial(
            GenericInteractionCreateEvent event, Game game, Player player, String planetID) {
        String next = game.drawExplore(INDUSTRIAL);
        if (next == null) {
            MessageHelper.sendMessageToEventChannel(event, "The industrial exploration deck is empty.");
            return;
        }
        Tile tile = game.getTileFromPlanet(planetID);
        String message = player.getRepresentationNoPing() + " revealed the next industrial exploration card for "
                + Helper.getPlanetRepresentation(planetID, game) + ":";
        ExploreService.resolveExplore(event, next, tile, planetID, message, player, game);
    }

    private static void sendSpendButtons(
            GenericInteractionCreateEvent event, Game game, Player player, String resOrInf, String message) {
        player.resetSpentThings();
        List<Button> buttons = new ArrayList<>(ButtonHelper.getExhaustButtonsWithTG(game, player, resOrInf));
        buttons.add(Buttons.DONE_EXHAUSTING_PLANETS_DELETE_BUTTONS);
        MessageHelper.sendMessageToEventChannelWithButtons(event, message, buttons);
    }
}
