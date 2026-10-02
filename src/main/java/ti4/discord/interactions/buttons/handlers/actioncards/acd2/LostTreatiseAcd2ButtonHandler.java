package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperStats;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class LostTreatiseAcd2ButtonHandler {

    @ButtonHandler("resolveLostTreatise")
    public static void resolveLostTreatise(Player player, Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green(player.factionButtonChecker() + "resolveLostTreatiseRedistribute", "Redistribute"));
        buttons.add(Buttons.green(player.factionButtonChecker() + "resolveLostTreatiseFleet", "Gain 1 Fleet Token"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose how to resolve _Lost Treatise_.",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveLostTreatiseRedistribute")
    public static void resolveLostTreatiseRedistribute(Player player, Game game, ButtonInteractionEvent event) {
        ButtonHelperStats.sendGainCCButtons(game, player, true);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveLostTreatiseFleet")
    public static void resolveLostTreatiseFleet(Player player, Game game, ButtonInteractionEvent event) {
        int oldFleetCC = player.getFleetCC();
        player.setFleetCC(oldFleetCC + 1);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getFactionEmoji() + " gained a command token in their fleet pool (" + oldFleetCC + "->"
                        + player.getFleetCC() + ") using _Lost Treatise_.");
        if (ButtonHelper.isLawInPlay(game, "regulations") && player.getEffectiveFleetCC() > 4) {
            String msg = player.getRepresentation() + ", reminder that _Fleet Regulations_ is a";
            msg += " law in play, which is limiting fleet pool to 4 tokens.";
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
