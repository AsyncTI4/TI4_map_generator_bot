package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.UserGameContextManager;
import ti4.message.MessageHelper;

public class ReverseSpeakerOrder extends GameSubcommandData {

    public ReverseSpeakerOrder() {
        super(Constants.REVERSE_SPEAKER_ORDER, "Change the speaker order from clockwise to counterclockwise or vice versa");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES to confirm").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!"YES".equals(event.getOption(Constants.CONFIRM, null, OptionMapping::getAsString))) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Must confirm with YES");
            return;
        }
        User user = event.getUser();
        String gameName = UserGameContextManager.getContextGame(user.getId());
        Game game = GameManager.getGame(gameName);

        game.setReverseSpeakerOrder(!game.isReverseSpeakerOrder());
    }
}
