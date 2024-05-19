package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;

public class DrawRandomBlueTile extends DiscordantStarsSubcommandData {

    public DrawRandomBlueTile() {
        super(Constants.DRAW_RANDOM_BLUE_TILE, "Draw a random blue tile that is not present in the game yet. ");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Helper.getRandomBlueTile(game, event);
    }

}
