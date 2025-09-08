package ti4.commands.map;

import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.image.PositionMapper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class RemoveTile extends GameStateSubcommand {

    public RemoveTile() {
        super(Constants.REMOVE_TILE, "Remove tile from map", true, false);
        addOptions(new OptionData(
                        OptionType.STRING,
                        Constants.POSITION,
                        "Tile position on map. Accepts comma separated list",
                        true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String positionOption = event.getOptions().getFirst().getAsString();
        Set<String> positions = Helper.getSetFromCSV(positionOption);

        Game game = getGame();
        for (String position : positions) {
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Tile position `" + position + "` is not valid");
            }
            game.removeTile(position);
        }
    }
}
