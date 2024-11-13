package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class CloneGame extends SpecialSubcommandData {
    public CloneGame() {
        super(Constants.CLONE_GAME, "Clone game in its current state");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if ("YES".equals(event.getOption(Constants.CONFIRM).getAsString())) {
            ButtonHelper.cloneGame(event, game);
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Please type YES.");
        }

    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {

    }
}
