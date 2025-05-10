package ti4.commands.cardsso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class ShowRandomSO extends GameStateSubcommand {

    public ShowRandomSO() {
        super("show_random", "Show a Secret Objective to a player", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source faction or color (default is you)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        List<String> secrets = new ArrayList<>(player.getSecrets().keySet());
        if (secrets.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No secret objectives to reveal");
            return;
        }
        
        Player otherPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (otherPlayer == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }

        Collections.shuffle(secrets);
        String soID = secrets.getFirst();

        String sb = otherPlayer.getRepresentationUnfogged() + " you were shown the following random Secret Objective:\n" +
            "> Player: " + player.getRepresentationNoPing() + "\n" +
            "> " + SecretObjectiveInfoService.getSecretObjectiveRepresentation(soID) + "\n";

        player.setSecret(soID);

        MessageHelper.sendMessageToEventChannel(event, "Random secret objective shown to " + otherPlayer.getRepresentationNoPing());
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(otherPlayer, sb);
    }
}
