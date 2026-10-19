package ti4.discord.interactions.commands.tigl;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.Constants;
import ti4.helpers.TIGLHelper;
import ti4.message.MessageHelper;

class InitRanks extends GameStateSubcommand {

    InitRanks() {
        super(Constants.TIGL_INIT_RANKS, "Recalculate every player's TIGL rank as it was at game start", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        if (!game.isCompetitiveTIGLGame()) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "This is not a TIGL game. Use `/tigl enable` to mark it as one first.");
            return;
        }

        TIGLHelper.initializeRanksAsync(game, event.getChannel());
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Looking up every player's TIGL rank as it stood when this game was created."
                        + " The game's rank updates once the league answers.");
    }
}
