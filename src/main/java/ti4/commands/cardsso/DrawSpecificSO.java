package ti4.commands.cardsso;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

class DrawSpecificSO extends GameStateSubcommand {

    public DrawSpecificSO() {
        super(Constants.DRAW_SPECIFIC_SO, "Draw specific SO", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "SO ID").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do draw SO. Default yourself"));
        addOptions(new OptionData(OptionType.STRING, Constants.PURGE_SO, "Enter YES to purge SO instead of drawing it"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String soId = event.getOption(Constants.SO_ID).getAsString();
        OptionMapping optionPurge = event.getOption(Constants.PURGE_SO);
        if (optionPurge != null && "YES".equals(optionPurge.getAsString())) {
            if (game.removeSOFromGame(soId)) {
                MessageHelper.sendMessageToEventChannel(event, "Purged specified SO");
            } else {
                MessageHelper.sendMessageToEventChannel(event, "Failed to purge specified SO");
            }
            return;
        }

        Player player = getPlayer();
        Map<String, Integer> secrets = game.drawSpecificSecretObjective(soId, player.getUserID());
        if (secrets == null) {
            MessageHelper.sendMessageToEventChannel(event, "SO not retrieved");
            return;
        }
        GameSaveLoadManager.saveGame(game, event);
        MessageHelper.sendMessageToEventChannel(event, "SO sent to user's hand - please check `/ac info`");
        SOInfo.sendSecretObjectiveInfo(game, player);
    }
}
