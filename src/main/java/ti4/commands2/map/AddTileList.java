package ti4.commands2.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.map.AddTileListService;

class AddTileList extends GameStateSubcommand {

    public AddTileList() {
        super(Constants.ADD_TILE_LIST, "Add tile list (map string) to generate map", true, false);
        addOption(OptionType.STRING, Constants.TILE_LIST, "Tile list (map string) in TTPG/TTS format", true);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tileList = event.getOption(Constants.TILE_LIST).getAsString();
        AddTileListService.addTileListToMap(getGame(), tileList, event);
    }
}
