package ti4.commands.draft.mantistile;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.draft.MantisMapBuildService;
import ti4.service.draft.draftables.MantisTileDraftable;

class MantisTileDraftableStartBuilding extends GameStateSubcommand {
    protected MantisTileDraftableStartBuilding() {
        super(Constants.DRAFT_MANTIS_TILE_START_BUILDING, "Send the buttons to build the map", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MantisTileDraftable draftable = MantisTileDraftableGroup.getDraftable(getGame());
        if (draftable == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    "Mantis tile isn't draftable; you may need `/draft manage add_draftable MantisTile`.");
            return;
        }

        MantisMapBuildService.initializeMapBuilding(getGame().getDraftManager());
    }
}
