package ti4.commands.admin;

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

public class DrawSpecificSOForPlayer extends AdminSubcommandData {

    public DrawSpecificSOForPlayer() {
        super(Constants.DRAW_SPECIFIC_SO_FOR_PLAYER, "Draw specific SO for player");
        addOptions(new OptionData(OptionType.STRING, Constants.SO_ID, "SO ID").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you do draw SO").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        OptionMapping playerOption = event.getOption(Constants.PLAYER);
        OptionMapping option = event.getOption(Constants.SO_ID);
        if (option == null) {
            MessageHelper.sendMessageToEventChannel(event, "SO ID needs to be specified");
            return;
        }
        if (playerOption == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player option was null");
            return;
        }

        User user = playerOption.getAsUser();
        Map<String, Integer> secrets = game.drawSpecificSecretObjective(option.getAsString(), user.getId());
        if (secrets == null) {
            MessageHelper.sendMessageToEventChannel(event, "SO not retrieved");
            return;
        }
        GameSaveLoadManager.saveGame(game, event);
        MessageHelper.sendMessageToEventChannel(event, "SO sent to user's hand - please check `/ac info`");
    }
}
