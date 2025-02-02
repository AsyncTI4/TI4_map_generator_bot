package ti4.commands.units;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;

//TODO: DELETE THIS AFTER DONE
class FixRemoveCaptureUnits extends GameStateSubcommand {

    public FixRemoveCaptureUnits() {
        super("remove_units_fix", "Release captured units", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_NAMES, "Comma separated list of '{count} unit' Eg. 2 infantry, carrier, 2 fighter, mech").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color for unit").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color capturing (default you)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.NO_MAPGEN, "'True' to not generate a map update with this command"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();

        String color = UnitCommandHelper.getTargetColor(event, game);
        if (color == null) return;

        Tile tile = getPlayer().getNomboxTile();

        tile.removeAllUnits(getPlayer().getColor());

        UnitCommandHelper.handleGenerateMapOption(event, game);
    }
}
