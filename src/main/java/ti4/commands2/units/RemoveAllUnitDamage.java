package ti4.commands2.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;

public class RemoveAllUnitDamage extends RemoveAllUnits {

    @Override
    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        tile.removeAllUnitDamage(color);
    }

    @Override
    public String getName() {
        return Constants.REMOVE_ALL_UNIT_DAMAGE;
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name").setRequired(true).setAutoComplete(true),
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true));
    }
}
