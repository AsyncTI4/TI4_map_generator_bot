package ti4.buttons.handlers.agenda.resolver;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AbsolSeedsAgendaResolver implements AgendaResolver {
    @Override
    public String getAgendaId() {
        return "absol_seeds";
    }

    @Override
    public void handle(Game game, ButtonInteractionEvent event, int agendaNumericId, String winner) {
        var winOrLose = "for".equalsIgnoreCase(winner)
                ? AgendaHelper.getPlayersWithMostPoints(game)
                : AgendaHelper.getPlayersWithLeastPoints(game);

        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(), "Custom public objective _Seed of an Empire_ has been added.");
        if (winOrLose.size() == 1) {
            Player playerWL = winOrLose.getFirst();
            Integer poIndex = game.addCustomPO("Seed of an Empire", 1);
            game.scorePublicObjective(playerWL.getUserID(), poIndex);
            MessageHelper.sendMessageToChannel(
                    playerWL.getCorrectChannel(), playerWL.getRepresentation() + " scored _Seed of an Empire_.");
            Helper.checkEndGame(game, playerWL);
            if ("for".equalsIgnoreCase(winner)) {
                game.setSpeakerUserID(playerWL.getUserID());
                MessageHelper.sendMessageToChannel(
                        playerWL.getCorrectChannel(),
                        playerWL.getRepresentation()
                                + " was made speaker and so must give each other player that voted \"for\" a promissory note.");
                for (Player p2 : AgendaHelper.getWinningVoters(winner, game)) {
                    if (p2 != playerWL) {
                        MessageHelper.sendMessageToChannelWithButtons(
                                playerWL.getCardsInfoThread(),
                                "You owe " + p2.getRepresentation() + "a promissory note.",
                                ButtonHelper.getForcedPNSendButtons(game, p2, playerWL));
                    }
                }
            } else {
                ActionCardHelper.drawActionCards(game, playerWL, agendaNumericId, true);
                playerWL.setFleetCC(playerWL.getFleetCC() + 1);
                playerWL.setTacticalCC(playerWL.getTacticalCC() + 1);
                playerWL.setStrategicCC(playerWL.getStrategicCC() + 1);
                MessageHelper.sendMessageToChannel(
                        playerWL.getCorrectChannel(),
                        playerWL.getRepresentation()
                                + " drew some action cards and has had a command token placed in each command pool.");
            }
        }
    }
}
