package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

public class Rematch extends SpecialSubcommandData {
    public Rematch() {
        super(Constants.REMATCH, "New game, same players");
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        if ("YES".equals(event.getOption(Constants.CONFIRM).getAsString())) {
            ButtonHelper.rematch(game, event);
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Please type YES.");
        }

    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {

    }
}
