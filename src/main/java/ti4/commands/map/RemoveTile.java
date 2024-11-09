package ti4.commands.map;

import java.util.Set;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.PositionMapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class RemoveTile extends AddRemoveTile {
    public RemoveTile() {
        super(Constants.REMOVE_TILE, "Remove tile from map");
        addOptions(new OptionData(OptionType.STRING, Constants.POSITION, "Tile position on map. Accepts comma separated list", true).setAutoComplete(true));
    }

    @Override
    protected void tileAction(Tile tile, String position, Game userActiveGame) {
        userActiveGame.removeTile(position);
    }

    @Override
    protected Game tileParsing(SlashCommandInteractionEvent event, String userID) {
        String positionOption = event.getOptions().getFirst().getAsString();
        Set<String> positions = Helper.getSetFromCSV(positionOption);

        Game userActiveGame = GameManager.getUserActiveGame(userID);
        for (String position : positions) {
            if (!PositionMapper.isTilePositionValid(position)) {
                MessageHelper.replyToMessage(event, "Tile position `" + position + "` is not valid");
                return null;
            }
            tileAction(null, position, userActiveGame);
        }
        return userActiveGame;
    }
}
