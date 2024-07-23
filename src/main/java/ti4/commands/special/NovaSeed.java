package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

public class NovaSeed extends SpecialSubcommandData {
    public NovaSeed() {
        super(Constants.NOVA_SEED, "Nova seed a system");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player using nova seed").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color using nova seed").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        player = Helper.getPlayer(game, player, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }

        OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
        if (tileOption == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Specify a tile");
            return;
        }
        String tileID = AliasHandler.resolveTile(tileOption.getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        secondHalfOfNovaSeed(player, event, tile, game);
    }

    public void secondHalfOfNovaSeed(Player player, GenericInteractionCreateEvent event, Tile tile, Game game) {
        String message1 = "Moments before disaster in game " + game.getName();
        StellarConverter.postTileInDisasterWatch(game, tile, 1, message1);

        //Remove all other players units from the tile in question
        for (Player player_ : game.getPlayers().values()) {
            if (player_ != player) {
                tile.removeAllUnits(player_.getColor());
                tile.removeAllUnitDamage(player_.getColor());
            }
        }

        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        space.removeAllTokens();
        game.removeTile(tile.getPosition());

        //Add the muaat supernova to the map and copy over the space unitholder
        Tile novaTile = new Tile(AliasHandler.resolveTile("81"), tile.getPosition(), space);
        game.setTile(novaTile);

        StringBuilder message2 = new StringBuilder();
        message2.append(tile.getRepresentation());
        message2.append(" has been nova seeded by ");
        message2.append(player.getRepresentation());
        StellarConverter.postTileInDisasterWatch(game, novaTile, 1, message2.toString());

        if (player.hasLeaderUnlocked("muaathero")) {
            Leader playerLeader = player.getLeader("muaathero").orElse(null);
            StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
            boolean purged = player.removeLeader(playerLeader);
            if (purged) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Adjudicator Ba'al, the Muaat hero, has been purged");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Adjudicator Ba'al, the Muaat hero, was not purged - something went wrong");
            }
        }
    }

}
