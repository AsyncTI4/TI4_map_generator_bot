package ti4.discord.interactions.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.service.option.TEOptionService;

class GalacticEventsSetup extends GameStateSubcommand {

    GalacticEventsSetup() {
        super(Constants.GALACTIC_EVENTS_SETUP, "Game Setup for Galactic Events", false, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        TEOptionService.offerTEOptionButtons(game, event.getMessageChannel());
    }
}
