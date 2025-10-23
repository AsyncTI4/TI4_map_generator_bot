package ti4.commands.draft.seat;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.service.draft.DraftManager;
import ti4.service.draft.draftables.SeatDraftable;

public class SeatDraftableGroup extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new SeatDraftableSetSeatCount(), new SeatDraftableSetSeatsForMapTemplate())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    public SeatDraftableGroup() {
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
        return (SeatDraftable) draftManager.getDraftable(SeatDraftable.TYPE);
    }
}
