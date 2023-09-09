package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.helpers.Constants;
import ti4.map.Map;

public class InitTspmap extends MapSubcommandData {
    public InitTspmap() {
        super(Constants.INIT_TSPMAP, "Initialize the map to have the hyperlanes and edge adjacencies of Tispoon's endless map layout");
        addOption(OptionType.BOOLEAN, "override", "IDK, this might do something", false, true); //TODO
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();

        boolean override = false;
        Boolean overrideOption = event.getOption("override", null, OptionMapping::getAsBoolean);
        override = overrideOption == null ? false : overrideOption;

        initializeMap(activeMap, override);
        addTspmapHyperlanes(activeMap, override);
        addTspmapEdgeAdjacencies(activeMap);
    }

    public void initializeMap(Map activeMap, boolean override) {
        activeMap.clearAdjacentTileOverrides();
    }

    public void addTspmapHyperlanes(Map activeMap, boolean override) {

    }

    public void addTspmapEdgeAdjacencies(Map activeMap) {
        activeMap.clearAdjacentTileOverrides();
    }
}


    