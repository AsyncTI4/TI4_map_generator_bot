package ti4.discord.interactions.commands.installation;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.image.MapRenderPipeline;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.message.MessageHelper;

class AddSweepToken extends GameStateSubcommand {

    public AddSweepToken() {
        super(Constants.ADD_SWEEP_TOKEN, "Add a sweep token to the selected system", true, true);
        addOptions(CommonOptions.tileName("System to add a sweep token to"));
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        String tileOption = StringUtils.substringBefore(
                event.getOption(Constants.TILE_NAME).getAsString().toLowerCase(), " ");
        Tile tile = TileHelper.getTile(event, tileOption, game);
        if (tile == null) return;

        String sweepToken = Mapper.getSweepID(player.getColor());
        tile.addCC(sweepToken);
        MapRenderPipeline.renderToWebsiteOnly(game, event);
        MessageHelper.replyToMessage(event, "Executed command. Use `/show_game` to check map.");
    }
}
