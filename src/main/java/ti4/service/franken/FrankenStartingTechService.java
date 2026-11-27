package ti4.service.franken;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.DeepReachesStartingTechHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.LostLegaciesStartingTechsHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.tech.ListTechService;

@UtilityClass
public class FrankenStartingTechService {

    public void addStartingTech(GenericInteractionCreateEvent event, Player player, String itemID) {
        FactionModel faction = Mapper.getFaction(itemID);
        List<String> startingTech = faction.getStartingTech();
        if (startingTech != null) {
            for (String tech : startingTech) {
                addTech(event, player, tech);
            }
        }

        // Handle Non-Standard Starting Techs
        if ("arcanum".equalsIgnoreCase(itemID)) {
            LostLegaciesStartingTechsHandler.offerArcanumStartingTechs(player.getGame(), player);
        }
        if ("aeterna".equalsIgnoreCase(itemID)) {
            LostLegaciesStartingTechsHandler.offerAeternaStartingTechs(player.getGame(), player);
        }
        if ("revenant".equalsIgnoreCase(itemID)) {
            LostLegaciesStartingTechsHandler.offerRevenantStartingTechs(player.getGame(), player);
        }
        if ("scrapyard".equalsIgnoreCase(itemID)) {
            LostLegaciesStartingTechsHandler.gainRandomScrapyardStartTechs(player.getGame(), player);
        }
        if ("thurviali".equalsIgnoreCase(itemID)) {
            LostLegaciesStartingTechsHandler.offerThurvialiStartingTechs(player.getGame(), player);
        }
        if ("stoneborn".equalsIgnoreCase(itemID)) {
            LostLegaciesStartingTechsHandler.offerStonebornStartingTechs(player.getGame(), player);
        }
        if ("netrunners".equalsIgnoreCase(itemID)) {
            DeepReachesStartingTechHandler.offerNetrunnersStartingTechs(player.getGame(), player);
        }
        if ("keleres".equalsIgnoreCase(itemID)) {
            Button getTech = Buttons.green(
                    player.factionButtonChecker() + "getKeleresTechOptions", "Get Keleres Technology Options");
            String msg = player.getRepresentationUnfogged()
                    + " after every other faction gets their starting technologies,"
                    + " press this button to for Keleres to get their starting technologies.";
            MessageHelper.sendMessageToChannelWithButton(player.getCorrectChannel(), msg, getTech);
        }

        offerStartingTechOptions(player, faction);
    }

    private void offerStartingTechOptions(Player player, FactionModel faction) {
        Integer bonusOptions = faction.getStartingTechAmount();
        List<String> startingTechOptions = faction.getStartingTechOptions();
        if (startingTechOptions == null || bonusOptions == null || bonusOptions <= 0) return;

        Game game = player.getGame();
        List<TechnologyModel> techs = new ArrayList<>();
        if (!startingTechOptions.isEmpty()) {
            for (String tech : game.getTechnologyDeck()) {
                TechnologyModel model = Mapper.getTech(tech);
                boolean homebrewReplacesAnOption = model.getHomebrewReplacesID()
                        .map(startingTechOptions::contains)
                        .orElse(false);
                if (startingTechOptions.contains(model.getAlias()) || homebrewReplacesAnOption) {
                    techs.add(model);
                }
            }
        }

        String msg = player.getRepresentationUnfogged() + " use the buttons to choose your starting technology:";
        List<Button> buttons = techs.isEmpty()
                ? List.of(Buttons.GET_A_FREE_TECH, Buttons.DONE_DELETE_BUTTONS)
                : ListTechService.getTechButtons(techs, player, "free");
        for (int x = 0; x < bonusOptions; x++) {
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg, buttons);
        }
    }

    public void removeStartingTech(GenericInteractionCreateEvent event, Player player, String itemID) {
        FactionModel faction = Mapper.getFaction(itemID);
        List<String> startingTech = faction.getStartingTech();
        if (startingTech == null) return;
        for (String tech : startingTech) {
            removeTech(event, player, tech);
        }
        if ("netrunners".equalsIgnoreCase(itemID)) {
            DeepReachesStartingTechHandler.clearNetrunnersStartingTechs(player.getGame(), player);
        }
    }

    private void addTech(GenericInteractionCreateEvent event, Player player, String techID) {
        player.addTech(techID);
        String techRep = Mapper.getTech(techID).getRepresentation(false);
        String message = player.getRepresentation() + " added technology: " + techRep + ".";

        CommanderUnlockCheckService.checkPlayer(player, "mirveda", "jolnar", "nekro", "dihmohn");
        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
    }

    private void removeTech(GenericInteractionCreateEvent event, Player player, String techID) {
        player.removeTech(techID);
        String techRep = Mapper.getTech(techID).getRepresentation(false);
        String message = player.getRepresentation() + " removed technology: " + techRep + ".";

        MessageHelper.sendEphemeralMessageToEventChannel(event, message);
    }
}
