package ti4.discord.interactions.commands.units;

import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.CommonOptions;
import ti4.discord.interactions.commands.GameStateCommand;
import ti4.game.Game;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.service.planet.AddPlanetToPlayAreaService;

public class RemoveAllUnits extends GameStateCommand {

    private final boolean damageOnly;

    private RemoveAllUnits(String name, String description, boolean damageOnly) {
        super(name, description, true, true);
        this.damageOnly = damageOnly;
    }

    public static RemoveAllUnits units() {
        return new RemoveAllUnits(Constants.REMOVE_ALL_UNITS, "Removal all units from the map", false);
    }

    public static RemoveAllUnits damage() {
        return new RemoveAllUnits(Constants.REMOVE_ALL_UNIT_DAMAGE, "Removal all unit damages from the map", true);
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                CommonOptions.tileName(),
                CommonOptions.factionColor("Faction or Color for unit"),
                CommonOptions.noMapGen());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        Tile tile = CommandHelper.getTile(event, game);
        if (tile == null) return;

        String color = getPlayer().getColor();
        if (damageOnly) {
            tile.removeAllUnitDamage(color);
        } else {
            tile.removeAllUnits(color);
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, unitHolder.getName(), game);
            }
        }

        UnitCommandHelper.handleGenerateMapOption(event, game);
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
