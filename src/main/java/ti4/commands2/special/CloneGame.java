package ti4.commands2.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.game.CloneGameService;

class CloneGame extends GameStateSubcommand {

    public CloneGame() {
        super(Constants.CLONE_GAME, "Clone game in its current state", false, false);
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type YES").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!"YES".equals(event.getOption(Constants.CONFIRM).getAsString())) {
            MessageHelper.sendMessageToEventChannel(event, "Please type YES.");
            return;
        }

        CloneGameService.cloneGame(getGame());
    }
}
