package ti4.commands.cardsso;

import java.util.Map;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

public class DrawSpecificSO extends SOCardsSubcommandData {

    public DrawSpecificSO() {
        super(Constants.DRAW_SPECIFIC_SO, "Draw specific SO");
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "SO ID").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do draw SO. Default yourself").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.PURGE_SO, "Enter YES to purge SO instead of drawing it").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        OptionMapping option = event.getOption(Constants.SO_ID);
        OptionMapping optionPurge = event.getOption(Constants.PURGE_SO);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "SO ID needs to be specified");
            return;
        }
        User user;
        if (playerOption == null) {
            //  MessageHelper.sendMessageToEventChannel(event, "Player option was null");
            // return;
            user = event.getUser();
        } else {
            user = playerOption.getAsUser();
        }
        if (optionPurge != null && "YES".equals(optionPurge.getAsString())) {
            if (game.removeSOFromGame(option.getAsString())) {
                MessageHelper.sendMessageToEventChannel(event, "Purged specified SO");
            } else {
                MessageHelper.sendMessageToEventChannel(event, "Failed to purge specified SO");
            }
            return;
        }

        Map<String, Integer> secrets = game.drawSpecificSecretObjective(option.getAsString(), user.getId());
        if (secrets == null) {
            MessageHelper.sendMessageToEventChannel(event, "SO not retrieved");
            return;
        }
        GameSaveLoadManager.saveGame(game, event);
        MessageHelper.sendMessageToEventChannel(event, "SO sent to user's hand - please check `/ac info`");
        SOInfo.sendSecretObjectiveInfo(game, game.getPlayer(user.getId()));
    }
}
