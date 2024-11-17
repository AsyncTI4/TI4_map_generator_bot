package ti4.commands2.special;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

class NovaSeed extends GameStateSubcommand {

    public NovaSeed() {
        super(Constants.NOVA_SEED, "Nova seed a system", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player using nova seed"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color using nova seed").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String tileID = AliasHandler.resolveTile(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase());
        Tile tile = TileHelper.getTile(event, tileID, game);
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        secondHalfOfNovaSeed(getPlayer(), event, tile, game);
    }

    public static void secondHalfOfNovaSeed(Player player, GenericInteractionCreateEvent event, Tile tile, Game game) {
        String message1 = "Moments before disaster in game " + game.getName();
        DisasterWatchHelper.postTileInDisasterWatch(game, event, tile, 1, message1);

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

        String message2 = tile.getRepresentation() +
                " has been nova seeded by " +
                player.getRepresentation();
        DisasterWatchHelper.postTileInDisasterWatch(game, event, novaTile, 1, message2);

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

    @ButtonHandler("novaSeed_")
    public static void novaSeed(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        secondHalfOfNovaSeed(player, event, game.getTileByPosition(buttonID.split("_")[1]), game);
        ButtonHelper.deleteTheOneButton(event);
    }

}
