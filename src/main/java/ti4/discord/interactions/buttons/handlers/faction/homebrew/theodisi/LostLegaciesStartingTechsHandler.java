package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.model.TechnologyModel.TechnologyType;
import ti4.service.tech.ListTechService;

@UtilityClass
public class LostLegaciesStartingTechsHandler {
    public static boolean offerStartingTechButtons(Game game, Player player, String startingTechFaction) {
        if (game == null || player == null) {
            return false;
        }

        String factionToCheck = startingTechFaction;
        if (factionToCheck == null || factionToCheck.isBlank()) {
            factionToCheck = player.getFaction();
        }
        if (factionToCheck == null || factionToCheck.isBlank()) {
            return false;
        }

        if (!isSupportedFaction(factionToCheck)) {
            return false;
        }

        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " press this button to choose your starting technology.",
                Buttons.green(
                        player.factionButtonChecker() + "getLostLegaciesStartingTechOptions", "Get Starting Tech"));
        return true;
    }

    @ButtonHandler("getLostLegaciesStartingTechOptions")
    public static void handleStartingTechButton(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }

        ButtonHelper.deleteMessage(event);
        String factionToCheck = player.getFaction();
        if (factionToCheck == null || factionToCheck.isBlank()) {
            return;
        }

        switch (factionToCheck.toLowerCase()) {
            case "arcanum" -> offerArcanumStartingTechs(game, player);
            case "aeterna" -> offerAeternaStartingTechs(game, player);
            case "revenant" -> offerRevenantStartingTechs(game, player);
            default -> {}
        }
    }

    public static boolean isSupportedFaction(String faction) {
        if (faction == null || faction.isBlank()) {
            return false;
        }
        return switch (faction.toLowerCase()) {
            case "arcanum", "aeterna", "revenant" -> true;
            default -> false;
        };
    }

    private static void offerArcanumStartingTechs(Game game, Player player) {
        List<TechnologyModel> techs = eligibleTechnologies(game, player, 0);
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged()
                        + " choose your first starting technology. You must choose **2 technologies in the same color with no prerequisites**.",
                false);
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged()
                        + " choose your second starting technology. It must have the **same color** as your first choice and have no prerequisites.",
                false);
    }

    private static void offerAeternaStartingTechs(Game game, Player player) {
        List<TechnologyModel> techs = eligibleTechnologies(game, player, 0);
        String rule = "You may choose up to **2 technologies with no prerequisites owned by no other player**. "
                + "All zero-prerequisite technologies are listed, so verify that no other player owns your choice.";
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged() + " choose your first starting technology, or press **Done**. "
                        + rule,
                true);
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged() + " choose your second starting technology, or press **Done**. "
                        + rule,
                true);
    }

    private static void offerRevenantStartingTechs(Game game, Player player) {
        List<TechnologyModel> techs = eligibleTechnologies(game, player, 1);
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged()
                        + " choose your first starting technology. You must choose **2 technologies in different colors with 1 total prerequisite**. Choose one zero-prerequisite technology and one one-prerequisite technology.",
                false);
        sendTechPrompt(
                player,
                techs,
                player.getRepresentationUnfogged()
                        + " choose your second starting technology. It must have a **different color** from your first choice, and the two choices must have **1 total prerequisite**.",
                false);
    }

    private static List<TechnologyModel> eligibleTechnologies(Game game, Player player, int maxPrerequisites) {
        return game.getTechnologyDeck().stream()
                .map(Mapper::getTech)
                .filter(tech -> tech != null && tech.getFaction().isEmpty())
                .filter(tech -> TechnologyType.mainFour.contains(tech.getFirstType()))
                .filter(tech -> tech.getRequirements().orElse("").length() <= maxPrerequisites)
                .filter(tech -> !player.hasTech(tech.getAlias()))
                .toList();
    }

    private static void sendTechPrompt(
            Player player, List<TechnologyModel> eligibleTechs, String message, boolean optional) {
        List<Button> buttons =
                new ArrayList<>(ListTechService.getTechButtons(new ArrayList<>(eligibleTechs), player, "free"));
        if (optional) {
            buttons.add(Buttons.DONE_DELETE_BUTTONS);
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
    }
}
