package ti4.commands.draft;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.SeatDraftable;

public class SeatDraftableSubcommands extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new SeatDraftableSetSeatCount(), new SeatDraftableSetSeatsForMapTemplate())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    protected SeatDraftableSubcommands() {
        super(Constants.DRAFT_SEAT, "Commands for managing seat drafting");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }

    public static SeatDraftable getDraftable(Game game) {
        DraftManager draftManager = game.getDraftManager();
        if (draftManager == null) {
            return null;
        }
        return (SeatDraftable) draftManager.getDraftableByType(SeatDraftable.TYPE);
    }

    public static class SeatDraftableSetSeatCount extends GameStateSubcommand {

        protected SeatDraftableSetSeatCount() {
            super(Constants.DRAFT_SEAT_SET_SEAT_COUNT, "Set the number of seats in the draft", true, false);
            addOption(OptionType.INTEGER, Constants.SEAT_COUNT_OPTION, "The number of seats to set", true, true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            SeatDraftable draftable = getDraftable(getGame());
            if (draftable == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Seats aren't draftable; you may need `/draft manage add_draftable Seat`.");
                return;
            }
            int numSeats = event.getOption(Constants.SEAT_COUNT_OPTION).getAsInt();
            draftable.setNumSeats(numSeats);
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Set the number of seats in the draft to " + draftable.getNumSeats() + ".");
            draftable.validateState(getGame().getDraftManager());
        }
    }

    public static class SeatDraftableSetSeatsForMapTemplate extends GameStateSubcommand {

        protected SeatDraftableSetSeatsForMapTemplate() {
            super(Constants.DRAFT_SEAT_SET_FOR_MAP, "Set the number of seats based on a map template", true, false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            SeatDraftable draftable = getDraftable(getGame());
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
}
