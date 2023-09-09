package ti4.commands.map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.PositionMapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import org.jetbrains.annotations.NotNull;

abstract public class AddRemoveTile extends MapSubcommandData {
    public AddRemoveTile(@NotNull String name, @NotNull String description) {
        super(name, description);
        addOption(OptionType.STRING, Constants.TILE_NAME, "Tile name", true);
        addOption(OptionType.STRING, Constants.POSITION, "Tile position on map", true);
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
        GameManager gameManager = GameManager.getInstance();
        if (!gameManager.isUserWithActiveGame(userID)) {
            MessageHelper.replyToMessage(event, "Set your active game using: /set_game gameName");
        } else {
            Game userActiveGame = tileParsing(event, userID, gameManager);
            if (userActiveGame == null) return;
            GameSaveLoadManager.saveMap(userActiveGame, event);
        }
    }

    protected Game tileParsing(SlashCommandInteractionEvent event, String userID, GameManager gameManager) {
        String planetTileName = AliasHandler.resolveTile(event.getOptions().get(0).getAsString().toLowerCase());
        String position = event.getOptions().get(1).getAsString();
        if (!PositionMapper.isTilePositionValid(position)) {
            MessageHelper.replyToMessage(event, "Position tile not allowed");
            return null;
        }

        String tileName = Mapper.getTileID(planetTileName);
        String tilePath = ResourceHelper.getInstance().getTileFile(tileName);
        if (tilePath == null) {
            MessageHelper.replyToMessage(event, "Could not find tile: " + planetTileName);
            return null;
        }

        Tile tile = new Tile(planetTileName, position);
        if (planetTileName.equals("18")) {
            tile.addToken("token_custodian.png", "mr");
        }

        Game userActiveGame = gameManager.getUserActiveGame(userID);
        Boolean isFowPrivate = null;
        if (userActiveGame.isFoWMode()) {
            isFowPrivate = event.getChannel().getName().endsWith(Constants.PRIVATE_CHANNEL);
        }
        if (isFowPrivate != null && isFowPrivate) {
            MessageHelper.replyToMessage(event, "Cannot run this command in a private channel.");
            return null;
        }

        tileAction(tile, position, userActiveGame);
        userActiveGame.rebuildTilePositionAutoCompleteList();
        return userActiveGame;
    }
}
