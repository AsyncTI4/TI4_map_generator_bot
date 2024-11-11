package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;

public class DrawRandomBlueTile extends GameStateSubcommand {

    public DrawRandomBlueTile() {
        super(Constants.DRAW_RANDOM_BLUE_TILE, "Draw a random blue tile that is not present in the game yet.", true, true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Helper.getRandomBlueTile(getGame(), event);
    }

}
