package ti4.commands2.units;

import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateCommand;
import ti4.generator.Mapper;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.PlayAreaHelper;
import ti4.helpers.UnitModifier;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

abstract public class AddRemoveUnits extends GameStateCommand {

    public AddRemoveUnits() {
        super(true, true);
    }

    @Override
    public List<OptionData> getOptions() {
        return List.of(
                new OptionData(OptionType.STRING, Constants.TILE_NAME, "System/Tile name"),
                new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit {planet}' Eg. 2 infantry primor, carrier, 2 fighter, mech pri").setRequired(true),
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for unit").setAutoComplete(true),
                new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String color = CommandHelper.getColor(game, event);
        if (!Mapper.isValidColor(color)) {
            MessageHelper.replyToMessage(event, "Color/Faction not valid");
            return;
        }

        OptionMapping option = event.getOption(Constants.TILE_NAME);
        String tileOption = option != null ?
                StringUtils.substringBefore(event.getOption(Constants.TILE_NAME, null, OptionMapping::getAsString).toLowerCase(), " ")
                : "nombox";
        String tileID = AliasHandler.resolveTile(tileOption);
        Tile tile = TileHelper.getTileObject(event, tileID, game);
        if (tile == null)
            return;

        UnitModifier.parseAndUpdateGame(event, color, tile, game);
        for (UnitHolder unitHolder_ : tile.getUnitHolders().values()) {
            PlayAreaHelper.addPlanetToPlayArea(game, event, tile, unitHolder_.getName());
        }
        actionAfterAll(event, tile, color, game);
    }

    protected void unitParsingForTile(SlashCommandInteractionEvent event, String color, Tile tile, Game game) {
        UnitModifier.parseAndUpdateGame(event, color, tile, game);
    }

    abstract protected void unitAction(SlashCommandInteractionEvent event, Tile tile, int count, String planetName,
        UnitKey unitID, String color, Game game);

    abstract protected void unitAction(GenericInteractionCreateEvent event, Tile tile, int count, String planetName,
        UnitKey unitID, String color, Game game);

    protected void actionAfterAll(SlashCommandInteractionEvent event, Tile tile, String color, Game game) {
        // do nothing, overriden by child classes
    }
}
