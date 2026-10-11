package ti4.discord.interactions.commands.draft.mantistile;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.service.draft.MantisMapBuildContext;
import ti4.service.draft.MantisMapBuildService;
import ti4.service.draft.draftables.MantisTileDraftable;

class MantisTileDraftableStartBuilding extends GameStateSubcommand {
    protected MantisTileDraftableStartBuilding() {
        super(Constants.DRAFT_MANTIS_TILE_START_BUILDING, "Send the buttons to build the map", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MantisTileDraftable draftable = CommandHelper.requireOrReport(
                event,
                MantisTileDraftableGroup.getDraftable(getGame()),
                "Mantis tile isn't draftable; you may need `/draft manage add_draftable MantisTile`.");
        if (draftable == null) return;

        MantisMapBuildContext mapBuildContext =
                MantisMapBuildContext.from(getGame().getDraftManager(), draftable);
        MantisMapBuildService.initializeMapBuilding(mapBuildContext);
    }
}
