package ti4.discord.interactions.commands.tf;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.helpers.ButtonHelperTwilightsFall;
import ti4.helpers.Constants;

class ReverseSplice extends GameStateSubcommand {

    public ReverseSplice() {
        super(Constants.REVERSE_SPLICE, "Reverse a splice", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        ButtonHelperTwilightsFall.reverseSpliceOrder(game);
    }
}
