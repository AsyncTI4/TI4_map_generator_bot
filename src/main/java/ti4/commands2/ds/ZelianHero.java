package ti4.commands2.ds;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.special.StellarConverter;
import ti4.commands.units.AddRemoveUnits;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
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
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color using Zelian R, the Zelian hero").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tileID = AliasHandler.resolveTile(event.getOption(Constants.TILE_NAME).getAsString().toLowerCase());
        Tile tile = AddRemoveUnits.getTile(event, tileID, getGame());
        if (tile == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }

        secondHalfOfCelestialImpact(getPlayer(), event, tile, getGame());
    }

    public static void secondHalfOfCelestialImpact(Player player, GenericInteractionCreateEvent event, Tile tile, Game game) {
        String message1 = "Moments before disaster in game " + game.getName();
        StellarConverter.postTileInDisasterWatch(game, event, tile, 1, message1);

        //Remove all other players ground force units from the tile in question
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
        String tgGainMsg = player.getFactionEmoji() + " gained " + resourcesSum + "TG" + (resourcesSum == 1 ? "" : "s") + " from Celestial Impact (" +
                player.getTg() + "->" + (player.getTg() + resourcesSum) + ").";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), tgGainMsg);
        player.gainTG(resourcesSum);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, game, resourcesSum);

        //Add the zelian asteroid field to the map and copy over the space unitholder
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        game.removeTile(tile.getPosition());
        Tile asteroidTile = new Tile(AliasHandler.resolveTile("D36"), tile.getPosition(), space);
        game.setTile(asteroidTile);

        //After shot to disaster channel
        String message2 = tile.getRepresentation() +
                " has been celestially impacted by " +
                player.getRepresentation();
        StellarConverter.postTileInDisasterWatch(game, event, asteroidTile, 1, message2);

        if (player.hasLeaderUnlocked("zelianhero")) {
            Leader playerLeader = player.getLeader("zelianhero").orElse(null);
            StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
            boolean purged = player.removeLeader(playerLeader);
            if (purged) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Zelian R, the Zelian heRo, has been purged");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Zelian R, the Zelian heRo, was not purged - something went wrong");
            }
        }
    }

    @ButtonHandler("celestialImpact_")
    public static void celestialImpact(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        secondHalfOfCelestialImpact(player, event, game.getTileByPosition(buttonID.split("_")[1]), game);
        ButtonHelper.deleteTheOneButton(event);
    }
}
