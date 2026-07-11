package ti4.discord.interactions.buttons.handlers.faction.base.naalu;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.leader.NaaluCommanderService;
import ti4.service.leader.PlayHeroService;

@UtilityClass
class NaaluButtonHandler {

    @ButtonHandler("naaluHeroInitiation")
    public static void resolveNaaluHeroInitiation(Player player, Game game, ButtonInteractionEvent event) {
        Leader playerLeader = player.unsafeGetLeader("naaluhero");
        StringBuilder message2 = new StringBuilder(player.toString())
                .append(" played ")
                .append(Helper.getLeaderFullRepresentation(playerLeader));
        boolean purged = PlayHeroService.removeLeader(game, player, playerLeader);
        if (purged) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    message2
                            + " - The Oracle, the Naalu hero, has been purged. \n\n Sent buttons to resolve to everyone's channels");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "The Oracle, the Naalu hero, was not purged - something went wrong");
        }
        for (Player p1 : game.getRealPlayers()) {
            if (p1 == player) {
                continue;
            }
            List<Button> stuffToTransButtons = ButtonHelper.getForcedPNSendButtons(game, player, p1);
            String message = p1.getRepresentationUnfogged()
                    + ", The Oracle, the Naalu hero, has been played and you must send a promissory note. Please choose the promissory note you wish to send.";
            MessageHelper.sendMessageToChannelWithButtons(p1.getCardsInfoThread(), message, stuffToTransButtons);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("naaluCommander")
    public static void secondHalfOfNaaluCommander(GenericInteractionCreateEvent event, Game game, Player player) {
        NaaluCommanderService.secondHalfOfNaaluCommander(event, game, player);
    }

    @ButtonHandler("naaluCPN")
    public static void naaluCommanderPN(GenericInteractionCreateEvent event, Game game, Player player) {
        NaaluCommanderService.naaluCommanderPN(event, game, player);
    }
}
