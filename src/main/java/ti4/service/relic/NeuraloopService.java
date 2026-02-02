package ti4.service.relic;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.objectives.DrawSecretService;
import ti4.service.objectives.RevealPublicObjectiveService;

@UtilityClass
public class NeuraloopService {

    public static void offerInitialNeuraloopChoice(Game game, String poID) {
        for (Player player : game.getRealPlayers()) {
            if (player.hasAbility("incomprehensible")) {
                DrawSecretService.drawSO(null, game, player);
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(),
                        "## " + player.getRepresentation()
                                + " draws a secret objective due to their Incomprehensible ability.");
            }
            if (player.hasRelic("neuraloop")) {
                String name;
                if (Mapper.getPublicObjective(poID) != null) {
                    name = Mapper.getPublicObjective(poID).getName();
                } else {
                    if (Mapper.getSecretObjective(poID) != null) {
                        name = Mapper.getSecretObjective(poID).getName();
                    } else {
                        name = poID;
                    }
                }
                String msg = player.getRepresentationUnfogged()
                        + " you have the opportunity to use the _Neuraloop_ relic to replace the objective " + name
                        + " with a random objective from __any__ of the objective decks. Doing so will cause you to purge one of your relics."
                        + " Use buttons to decide which objective deck, if any, you wish to draw the new objective from.";
                List<Button> buttons = new ArrayList<>();

                String pre = "neuraloopPart1;" + poID + ";";
                buttons.add(Buttons.gray(pre + "stage1", "Replace with Stage 1 Public Objective", CardEmojis.Public1));
                buttons.add(Buttons.gray(pre + "stage2", "Replace with Stage 2 Public Objective", CardEmojis.Public2));
                buttons.add(Buttons.gray(pre + "secret", "Replace with Secret Objective", CardEmojis.SecretObjective));
                buttons.add(Buttons.red("deleteButtons", "Delete These Buttons"));
                MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
            }
        }
    }

    private static List<Button> getNeuraloopButton(Player player, String poID, String type) {
        List<Button> buttons = new ArrayList<>();

        for (String relic : player.getRelics()) {
            if (Mapper.getRelic(relic) == null || Mapper.getRelic(relic).isFakeRelic()) {
                continue;
            }
            buttons.add(Buttons.gray(
                    "neuraloopPart2;" + poID + ";" + type + ";" + relic,
                    Mapper.getRelic(relic).getName()));
        }
        return buttons;
    }

    @ButtonHandler("neuraloopPart1")
    private void neuraloopPart1(ButtonInteractionEvent event, Player player, String buttonID) {
        String poID = buttonID.split(";")[1];
        String type = buttonID.split(";")[2];
        String deck = type;
        switch (type) {
            case "stage1" -> deck = "stage 1 public objective";
            case "stage2" -> deck = "stage 2 public objective";
            case "secret" -> deck = "secret objective";
        }
        String msg = player.getRepresentation()
                + ", please choose the relic you wish to purge in order to replace the objective with a " + deck + ".";
        List<Button> buttons = getNeuraloopButton(player, poID, type);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("neuraloopPart2")
    private void neuraloopPart2(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String poID = buttonID.split(";")[1];
        String type = buttonID.split(";")[2];
        String relic = buttonID.split(";")[3];
        String deck = type;
        switch (type) {
            case "stage1" -> deck = "stage 1 public objective";
            case "stage2" -> deck = "stage 2 public objective";
            case "secret" -> deck = "secret objective";
        }
        player.removeRelic(relic);
        player.removeExhaustedRelic(relic);
        RelicHelper.resolveRelicLossEffects(game, player, relic);
        game.removeRevealedObjective(poID);
        String msg = "## " + game.getPing() + " " + player.getRepresentation() + " is using _Neuraloop_, purging "
                + ("neuraloop".equals(relic) ? "itself" : Mapper.getRelic(relic).getName())
                + ", to replace the recently revealed objective with a random " + deck + ".";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
        if (game.isFowMode()) {
            MessageHelper.sendMessageToChannel(
                    game.getMainGameChannel(), game.getPing() + ", revealed objective `" + poID + "` was replaced.");
        }
        if ("stage1".equalsIgnoreCase(type)) {
            RevealPublicObjectiveService.revealS1(game, event, true);
        } else if ("stage2".equalsIgnoreCase(type)) {
            RevealPublicObjectiveService.revealS2(game, event, true);
        } else {
            RevealPublicObjectiveService.revealSO(game, game.getActionsChannel());
        }
        ButtonHelper.deleteMessage(event);
    }
}
