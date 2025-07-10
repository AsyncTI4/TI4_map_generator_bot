package ti4.commands.ds;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.Helper;
import ti4.image.TileHelper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

class ZelianHero extends GameStateSubcommand {

    public ZelianHero() {
        super(Constants.ZELIAN_HERO, "Celestial Impact a system (replace with Zelian Asteroid field)", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color using Zelian R, the Zelian heRo").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tileID = event.getOption(Constants.TILE_NAME).getAsString().toLowerCase();
        Tile tile = TileHelper.getTile(event, tileID, getGame());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        secondHalfOfCelestialImpact(getPlayer(), event, tile, getGame());
    }

    public static void secondHalfOfCelestialImpact(Player player, GenericInteractionCreateEvent event, Tile tile, Game game) {
        String message1 = "Moments before disaster in game " + game.getName() + ".";
        DisasterWatchHelper.postTileInDisasterWatch(game, event, tile, 1, message1);

        //Remove all other players ground force units from the system in question
        for (Player player_ : game.getPlayers().values()) {
            if (player_ != player) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (!unitHolder.getName().equals(Constants.SPACE)) {
                        unitHolder.removeAllUnits(player_.getColor());
                    }
                }
            }
        }

        //Gain TGs equal to the sum of the resource values of the planets in the system
        int resourcesSum = 0;
        List<Planet> planetsInSystem = tile.getPlanetUnitHolders().stream().toList();
        for (Planet p : planetsInSystem) {
            resourcesSum += p.getResources();
        }
        String tgGainMsg = player.getFactionEmoji() + " gained " + resourcesSum + " trade good" + (resourcesSum == 1 ? "" : "s") + " from _Celestial Impact_ (" +
            player.getTg() + "->" + (player.getTg() + resourcesSum) + ").";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), tgGainMsg);
        player.gainTG(resourcesSum);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, resourcesSum);

        //Add the zelian asteroid field to the map and copy over the space unitholder
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        game.removeTile(tile.getPosition());
        Tile asteroidTile = new Tile(AliasHandler.resolveTile("D36"), tile.getPosition(), space);
        game.setTile(asteroidTile);

        //After shot to disaster channel
        String message2 = tile.getRepresentation() +
            " has been celestially impacted by " +
            player.getRepresentation();
        DisasterWatchHelper.postTileInDisasterWatch(game, event, asteroidTile, 1, message2);

        if (player.hasLeaderUnlocked("zelianhero")) {
            Leader playerLeader = player.getLeader("zelianhero").orElse(null);
            StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
            boolean purged = player.removeLeader(playerLeader);
            if (purged) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Zelian R, the Zelian heRo, has been purged.");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Zelian R, the Zelian heRo, was not purged - something went wrong.");
            }
        }
    }
}
