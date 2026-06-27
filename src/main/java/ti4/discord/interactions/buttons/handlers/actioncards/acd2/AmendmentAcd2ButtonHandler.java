package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import ti4.model.ActionCardModel;
import ti4.model.PublicObjectiveModel;
import ti4.service.objectives.RevealPublicObjectiveService;

@UtilityClass
class AmendmentAcd2ButtonHandler {

    @ButtonHandler("resolveAmendmentStep1")
    public static void resolveAmendmentStep1(Player player, Game game, ButtonInteractionEvent event) {
        int acIndex = -1;
        for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
            if ("amendment".equals(ac.getKey())) {
                acIndex = ac.getValue();
                break;
            }
        }
        if (acIndex == -1) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " does not have _Amendment_ in hand.");
            return;
        }

        game.discardActionCard(player.getUserID(), acIndex);
        ActionCardModel actionCard = Mapper.getActionCard("amendment");
        String actionCardPlayMessage = game.isFowMode()
                ? "Someone played the action card _Amendment_."
                : player.getRepresentation() + " played the action card _Amendment_.";
        MessageHelper.sendMessageToChannelWithEmbed(
                game.getMainGameChannel(), actionCardPlayMessage, actionCard.getRepresentationEmbed(false, true, game));

        List<Button> buttons = new ArrayList<>();
        for (Map.Entry<String, Integer> po : game.getRevealedPublicObjectives().entrySet()) {
            String poID = po.getKey();
            List<String> scorers = game.getScoredPublicObjectives().getOrDefault(poID, List.of());
            if (scorers.contains(player.getUserID())) {
                PublicObjectiveModel poModel = Mapper.getPublicObjective(poID);
                if (poModel != null) {
                    buttons.add(Buttons.gray(
                            player.factionButtonChecker() + "amendmentChooseObjective_" + poID,
                            "Purge: " + poModel.getName()));
                }
            }
        }

        ButtonHelper.deleteMessage(event);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentationUnfogged() + " has no scored public objectives to purge for _Amendment_.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose a public objective you have scored to purge.",
                buttons);
    }

    @ButtonHandler("amendmentChooseObjective_")
    public static void amendmentChooseObjective(
            Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String poID = buttonID.replace("amendmentChooseObjective_", "");
        PublicObjectiveModel poModel = Mapper.getPublicObjective(poID);
        String poName = poModel != null ? poModel.getName() : poID;
        int poPoints =
                poModel != null ? poModel.getPoints() : game.getCustomPublicVP().getOrDefault(poID, 0);

        List<String> scorers = new ArrayList<>(game.getScoredPublicObjectives().getOrDefault(poID, List.of()));
        if (!scorers.isEmpty()) {
            String purgedObjectiveName = poName + " (PURGED)";
            Integer purgedObjectiveId = game.addCustomPO(purgedObjectiveName, poPoints);
            for (String userID : scorers) {
                game.scorePublicObjective(userID, purgedObjectiveId);
            }
        }
        game.removeRevealedObjective(poID);

        String objectivePurgeMessage = game.isFowMode()
                ? "A public objective was purged using _Amendment_. Players do not lose points from this purge."
                : player.getRepresentationNoPing() + " purged the public objective _" + poName
                        + "_ using _Amendment_. Players do not lose points from this purge.";
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), objectivePurgeMessage);

        List<Button> stageButtons = new ArrayList<>();
        stageButtons.add(
                Buttons.green(player.factionButtonChecker() + "amendmentRevealStage1", "Reveal Stage 1 Objective"));
        stageButtons.add(
                Buttons.blue(player.factionButtonChecker() + "amendmentRevealStage2", "Reveal Stage 2 Objective"));

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose which stage public objective to draw and reveal.",
                stageButtons);
    }

    @ButtonHandler("amendmentRevealStage1")
    public static void amendmentRevealStage1(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        RevealPublicObjectiveService.revealS1(game, event);
    }

    @ButtonHandler("amendmentRevealStage2")
    public static void amendmentRevealStage2(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelper.deleteMessage(event);
        RevealPublicObjectiveService.revealS2(game, event);
    }
}
