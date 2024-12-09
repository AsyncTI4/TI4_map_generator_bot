package ti4.commands2.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateCommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.service.planet.AddPlanetToPlayAreaService;

public class RemoveAllUnits extends GameStateCommand {

    public RemoveAllUnits() {
        super(true, true);
    }

    @Override
    public String getName() {
        return Constants.REMOVE_ALL_UNITS;
    }

    @Override
    public String getDescription() {
        return "Removal all units from the map";
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
            new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name")
                .setRequired(true)
                .setAutoComplete(true),
            new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit")
                .setAutoComplete(true),
            new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command")
        );
    }


    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Tile tile = CommandHelper.getTile(event, game);
        if (tile == null) return;

        String color = getPlayer().getColor();
        tile.removeAllUnits(color);
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, unitHolder.getName(), game);
        }

        UnitCommandHelper.handleGenerateMapOption(event, game);
    }
}
