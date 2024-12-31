package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.game.StartPhaseService;

class StartPhase extends GameStateSubcommand {

    public StartPhase() {
        super(Constants.START_PHASE, "Start a specific phase of the game", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.SPECIFIC_PHASE, "What phase do you wish to get buttons for?").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String phase = event.getOption(Constants.SPECIFIC_PHASE, null, OptionMapping::getAsString);
        StartPhaseService.startPhase(event, game, phase);
    }
}
