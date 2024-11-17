package ti4.commands2.installation;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Tile;

class RemoveSweepToken extends GameStateSubcommand {

    public RemoveSweepToken() {
        super(Constants.REMOVE_SWEEP_TOKEN, "Remove a sweep token from the selected system", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System to remove a sweep token from").setAutoComplete(true).setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        String tileName = event.getOption(Constants.TILE_NAME).getAsString().toLowerCase();
        tileName = StringUtils.substringBefore(tileName, " ");
        String tileID = AliasHandler.resolveTile(tileName);
        Tile tile = TileHelper.getTile(event, tileID, getGame());
        if (tile == null) return;

        String sweepToken = Mapper.getSweepID(getPlayer().getColor());
        tile.removeCC(sweepToken);
    }
}
