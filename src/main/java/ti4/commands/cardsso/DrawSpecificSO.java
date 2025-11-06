package ti4.commands.cardsso;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

class DrawSpecificSO extends GameStateSubcommand {

    public DrawSpecificSO() {
        super(Constants.DRAW_SPECIFIC_SO, "Draw specific secret objective", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "Secret objective ID")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(
                OptionType.USER, Constants.PLAYER, "Player who draws the secret objective (default is you)"));
        addOptions(new OptionData(
                OptionType.STRING,
                Constants.PURGE_SO,
                "Enter YES to purge the secret objective instead of drawing it"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String soId = event.getOption(Constants.SO_ID).getAsString();
        OptionMapping optionPurge = event.getOption(Constants.PURGE_SO);
        if (optionPurge != null && "YES".equals(optionPurge.getAsString())) {
            if (game.removeSOFromGame(soId)) {
                MessageHelper.sendMessageToEventChannel(event, "Purged specified secret objective.");
            } else {
                MessageHelper.sendMessageToEventChannel(event, "Failed to purge specified secret objective.");
            }
            return;
        }

        Player player = getPlayer();
        Map<String, Integer> secrets = game.drawSpecificSecretObjective(soId, player.getUserID());
        if (secrets == null) {
            MessageHelper.sendMessageToEventChannel(event, "Secret objective not retrieved");
            return;
        }
        MessageHelper.sendMessageToEventChannel(event, "Secret objective sent to player's hand");
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
    }
}
