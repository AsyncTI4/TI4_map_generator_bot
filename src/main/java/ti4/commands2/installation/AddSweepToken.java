package ti4.commands2.installation;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.GameStateSubcommand;
import ti4.image.MapRenderPipeline;
import ti4.image.Mapper;
import ti4.image.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

class AddSweepToken extends GameStateSubcommand {

    public AddSweepToken() {
        super(Constants.ADD_SWEEP_TOKEN, "Add a sweep token to the selected system", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System to add a sweep token to").setAutoComplete(true).setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        String tileOption = StringUtils.substringBefore(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase(), " ");
        String tileID = AliasHandler.resolveTile(tileOption);
        Tile tile = TileHelper.getTile(event, tileID, game);
        if (tile == null) return;

        String sweepToken = Mapper.getSweepID(player.getColor());
        tile.addCC(sweepToken);
        MapRenderPipeline.renderToWebsiteOnly(game, event);
        MessageHelper.replyToMessage(event, "Executed command. Use /show_game to check map");
    }
}
