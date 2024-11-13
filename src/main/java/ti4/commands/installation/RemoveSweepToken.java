package ti4.commands.installation;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.units.AddRemoveUnits;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class RemoveSweepToken extends InstallationSubcommandData {

    public RemoveSweepToken() {
        super(Constants.REMOVE_SWEEP_TOKEN, "Remove a sweep token from the selected system");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System to remove a sweep token from").setAutoComplete(true).setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }

        OptionMapping option = event.getOption(Constants.TILE_NAME);
        String tileOption = option != null ? StringUtils.substringBefore(event.getOption(Constants.TILE_NAME, null, OptionMapping::getAsString).toLowerCase(), " ") : "nombox";
        String tileID = AliasHandler.resolveTile(tileOption);
        Tile tile = AddRemoveUnits.getTile(event, tileID, game);
        if (tile == null) return;

        String sweepToken = Mapper.getSweepID(player.getColor());
        tile.removeCC(sweepToken);
    }
}
