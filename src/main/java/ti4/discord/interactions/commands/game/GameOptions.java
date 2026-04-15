package ti4.discord.interactions.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.option.GameOptionService;

class GameOptions extends GameStateSubcommand {

    public GameOptions() {
        super(Constants.OPTIONS, "Modify some Game Options", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        GameOptionService.offerGameOptionButtons(getGame(), event.getChannel());
    }
}
