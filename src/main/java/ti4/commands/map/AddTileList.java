package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import net.dv8tion.jda.api.interactions.modals.Modal;
import ti4.helpers.Constants;
import ti4.service.map.AddTileListService;

class AddTileList extends GameStateSubcommand {

    public AddTileList() {
        super(Constants.ADD_TILE_LIST, "Show dialog for adding tile list (map string) to generate map", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Modal modal = AddTileListService.buildMapStringModal(getGame(), "addMapString");
        event.replyModal(modal).queue();
    }
}
