package ti4.commands.map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

abstract public class AddRemoveTile extends MapSubcommandData {
    public AddRemoveTile(@NotNull String name, @NotNull String description) {
        super(name, description);
    }

    abstract protected void tileAction(Tile tile, String position, Game userActiveGame);

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getInteraction().getMember();
        if (member == null) {
            MessageHelper.replyToMessage(event, "Caller ID not found");
            return;
        }
        String userID = member.getId();
        if (!UserGameContextManager.doesUserHaveContextGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
        } else {
            Game userActiveGame = tileParsing(event, userID);
            if (userActiveGame == null) return;
            GameSaveLoadManager.saveGame(userActiveGame, event);
        }
    }

    protected Game tileParsing(SlashCommandInteractionEvent event, String userID) {
        String planetTileName = AliasHandler.resolveTile(event.getOptions().get(0).getAsString().toLowerCase());
        String position = event.getOptions().get(1).getAsString();
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.replyToMessage(event, "Position tile not allowed");
            return null;
        }

        String tileName = Mapper.getTileID(planetTileName);
        if (tileName == null) {
            MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
            return null;
        }
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
            return null;
        }

        Tile tile = new Tile(planetTileName, position);
        if (tile.isMecatol()) {
            AddTile.addCustodianToken(tile);
        }

        Game userActiveGame = UserGameContextManager.getContextGame(userID);
        Boolean isFowPrivate = null;
        if (userActiveGame.isFowMode()) {
            isFowPrivate = event.getChannel().getName().endsWith(Constants.PRIVATE_CHANNEL);
        }
        if (isFowPrivate != null && isFowPrivate && !userActiveGame.isAgeOfExplorationMode()) {
            MessageHelper.replyToMessage(event, "Cannot run this command in a private channel.");
            return null;
        }

        tileAction(tile, position, userActiveGame);
        userActiveGame.rebuildTilePositionAutoCompleteList();
        return userActiveGame;
    }
}
