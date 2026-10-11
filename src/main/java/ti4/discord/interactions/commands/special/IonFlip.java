package ti4.discord.interactions.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.image.TileHelper;

class IonFlip extends GameStateSubcommand {

    IonFlip() {
        super(Constants.ION_TOKEN_FLIP, "Flip ION Storm Token", true, false);
        markSuspicious();
        addOptions(CommonOptions.tileName());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        OptionMapping tileOption =
                CommandHelper.requireOrReport(event, event.getOption(Constants.TILE_NAME), "Specify a tile");
        if (tileOption == null) return;
        String tileID = tileOption.getAsString().toLowerCase();
        Tile tile = CommandHelper.requireOrReport(
                event,
                TileHelper.getTile(event, tileID, game),
                "Could not resolve tileID:  `" + tileID + "`. Tile not found");
        if (tile == null) return;
        UnitHolder spaceUnitHolder =
                CommandHelper.requireOrReport(event, tile.getSpaceUnitHolder(), "No valid Space found");
        if (spaceUnitHolder == null) return;
        if (spaceUnitHolder.getTokenList().contains(Constants.TOKEN_ION_ALPHA_PNG)) {
            tile.removeToken(Constants.TOKEN_ION_ALPHA_PNG, spaceUnitHolder.getName());
            tile.addToken(Constants.TOKEN_ION_BETA_PNG, spaceUnitHolder.getName());
        } else if (spaceUnitHolder.getTokenList().contains(Constants.TOKEN_ION_BETA_PNG)) {
            tile.removeToken(Constants.TOKEN_ION_BETA_PNG, spaceUnitHolder.getName());
            tile.addToken(Constants.TOKEN_ION_ALPHA_PNG, spaceUnitHolder.getName());
        }
    }
}
