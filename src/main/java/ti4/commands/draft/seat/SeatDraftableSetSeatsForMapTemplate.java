package ti4.commands.draft.seat;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.draft.draftables.SeatDraftable;

class SeatDraftableSetSeatsForMapTemplate extends GameStateSubcommand {

    protected SeatDraftableSetSeatsForMapTemplate() {
        super(Constants.DRAFT_SEAT_SET_FOR_MAP, "Set the number of seats based on a map template", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        SeatDraftable draftable = SeatDraftableGroup.getDraftable(getGame());
        if (draftable == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Seats aren't draftable; you may need `/draft manage add_draftable Seat`.");
            return;
        }
        String mapTemplateId = getGame().getMapTemplateID();
        if (mapTemplateId == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Map template ID is not set for the game.");
            return;
        }
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(mapTemplateId);
        if (mapTemplate == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown map template: " + mapTemplateId);
            return;
        }
        int numSeats = mapTemplate.getPlayerCount();
        draftable.setNumSeats(numSeats);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                "Set the number of seats in the draft to " + draftable.getNumSeats() + " based on the map template "
                        + mapTemplate.getAlias() + ".");
        draftable.validateState(getGame().getDraftManager());
    }
}
