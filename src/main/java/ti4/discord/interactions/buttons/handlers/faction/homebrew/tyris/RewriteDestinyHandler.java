package ti4.discord.interactions.buttons.handlers.faction.homebrew.tyris;

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
import ti4.service.emoji.CardEmojis;
import ti4.service.objectives.RevealPublicObjectiveService;

@UtilityClass
public class RewriteDestinyHandler {

    public static void offerRewriteDestiny(Game game, Player player, String poID, int stage) {
        if (!game.getStoredValue("rewriteDestinyUsed" + game.getRound()).isEmpty()) return;
        if (player.getStrategicCC() < 1) return;
        String poName = Mapper.getPublicObjective(poID) != null
                ? Mapper.getPublicObjective(poID).getName()
                : poID;
        String msg = player.getRepresentationUnfogged()
                + " you may use **Rewrite Destiny** to discard the newly revealed objective _" + poName
                + "_ and have the speaker reveal a new stage " + stage + " public objective."
                + " This will cost 1 command token from your strategy pool.";
        List<Button> buttons = new ArrayList<>();
        CardEmojis emoji = stage == 1 ? CardEmojis.Public1 : CardEmojis.Public2;
        buttons.add(Buttons.green("rewriteDestiny;" + poID + ";" + stage, "Use Rewrite Destiny", emoji));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    @ButtonHandler("rewriteDestiny")
    public static void resolveRewriteDestiny(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String poID = buttonID.split(";")[1];
        int stage = Integer.parseInt(buttonID.split(";")[2]);
        Integer idNumber = game.getRevealedPublicObjectives().get(poID);
        if (idNumber == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentation() + " the objective was not found — it may have already been discarded.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setStrategicCC(player.getStrategicCC() - 1);
        game.setStoredValue("rewriteDestinyUsed" + game.getRound(), "true");
        String poName = Mapper.getPublicObjective(poID) != null
                ? Mapper.getPublicObjective(poID).getName()
                : poID;
        game.shuffleObjectiveBackIntoDeck(idNumber);
        String msg = "## " + game.getPing() + " " + player.getRepresentation()
                + " is using **Rewrite Destiny**, spending 1 strategy token, to discard _" + poName
                + "_ and have the speaker reveal a new stage " + stage + " public objective.";
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
        if (stage == 1) {
            RevealPublicObjectiveService.revealS1(game, event, true);
        } else {
            RevealPublicObjectiveService.revealS2(game, event, true);
        }
        ButtonHelper.deleteMessage(event);
    }
}
