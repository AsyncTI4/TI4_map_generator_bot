package ti4.commands.cardsso;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.message.MessageHelper;

import java.util.Map;

public class DrawSpecificSO extends SOCardsSubcommandData {

    public DrawSpecificSO() {
        super(Constants.DRAW_SPECIFIC_SO, "Draw a specific secret objective.");
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "ID of the secret objective to draw").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player that will draw the secret objective (default: you)").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.PURGE_SO, "Enter YES to purge the secret objective instead of drawing it").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        OptionMapping option = event.getOption(Constants.SO_ID);
        OptionMapping optionPurge = event.getOption(Constants.PURGE_SO);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "Secret objective ID needs to be specified.");
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
                MessageHelper.sendMessageToEventChannel(event, "Purged the specified secret objective.");
            } else {
                MessageHelper.sendMessageToEventChannel(event, "Failed to purge the specified secret objective.");
            }
            return;
        }

        Map<String, Integer> secrets = game.drawSpecificSecretObjective(option.getAsString(), user.getId());
        if (secrets == null) {
            MessageHelper.sendMessageToEventChannel(event, "Secret objective could not be retrieved.");
            return;
        }
        GameSaveLoadManager.saveMap(game, event);
        MessageHelper.sendMessageToEventChannel(event, "Secret objective sent to player's hand - please check `/so info`.");
        SOInfo.sendSecretObjectiveInfo(game, game.getPlayer(user.getId()));
    }
}
