package ti4.discord.interactions.buttons.handlers.facilities;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperSCs;
import ti4.message.MessageHelper;
import ti4.service.turn.StartTurnService;

@UtilityClass
class CoreFactoryButtonHandler {

    @ButtonHandler("corefacilityAction")
    public static void coreFactoryAction(ButtonInteractionEvent event, Player player, Game game) {
        ButtonHelper.deleteMessage(event);
        String tPlanet = "";
        for (String planet : player.getPlanets()) {
            Planet uH = game.getPlanet(planet);
            if (uH == null) {
                continue;
            }
            Set<String> tokens = new HashSet<>(uH.getTokenList());
            for (String token : tokens) {
                if (token.contains("corefactory")) {
                    uH.removeToken(token);
                    tPlanet = planet;
                }
            }
        }
        Planet uH = game.getPlanet(tPlanet);
        List<Button> facilities = new ArrayList<>();
        List<String> usedFacilities = ButtonHelperSCs.findUsedFacilities(game, player);

        String facilityID = "facilitytransitnode";
        if (!usedFacilities.contains(facilityID)) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Transit Node"));
        }
        facilityID = "facilityresearchlab";
        if (!usedFacilities.contains(facilityID) && !uH.getTechSpecialities().isEmpty()) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Research Lab"));
        }
        facilityID = "facilitynavalbase";
        Set<String> planetTypes = uH.getPlanetTypes();
        if (!usedFacilities.contains(facilityID)
                && (planetTypes.contains("industrial") || "mr".equalsIgnoreCase(tPlanet) || uH.isLegendary())) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Naval Base"));
        }
        facilityID = "facilitylogisticshub";
        if (!usedFacilities.contains(facilityID)
                && (planetTypes.contains("industrial") || planetTypes.contains("hazardous"))) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Logistics Hub"));
        }
        facilityID = "facilityembassy";
        boolean hasEmbassy = false;
        for (String fac : usedFacilities) {
            if (fac.contains("facilityembassy")) {
                hasEmbassy = true;
                break;
            }
        }
        if (!hasEmbassy && (planetTypes.contains("industrial") || "mr".equalsIgnoreCase(tPlanet))) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Embassy"));
        }
        int colonies = 0;
        facilityID = "facilitycolony";
        for (String fac : usedFacilities) {
            if (fac.contains(facilityID)) {
                colonies++;
            }
        }
        if (colonies < 2) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Colony"));
        }
        colonies = 0;
        facilityID = "facilityrefinery";
        for (String fac : usedFacilities) {
            if (fac.contains(facilityID)) {
                colonies++;
            }
        }
        if (colonies < 2) {
            facilities.add(Buttons.green("addFacility_" + tPlanet + "_" + facilityID + "_dont", "Refinery"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.toString() + ", please choose the facility you wish to replace the Core Factory.",
                facilities);

        String message = "Use buttons to end turn or do another action.";
        List<Button> systemButtons = StartTurnService.getStartOfTurnButtons(player, game, true, event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, systemButtons);
    }
}
