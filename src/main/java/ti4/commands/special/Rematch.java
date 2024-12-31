package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.game.RematchService;

class Rematch extends GameStateSubcommand {

    public Rematch() {
        super(Constants.REMATCH, "Create a new game with the same players and channels as the current game", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if ("YES".equals(event.getOption(Constants.CONFIRM).getAsString())) {
            RematchService.rematch(game, event);
        } else {
            MessageHelper.sendMessageToEventChannel(event, "Please type YES.");
        }
    }
}
