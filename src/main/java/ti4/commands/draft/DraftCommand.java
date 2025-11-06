package ti4.commands.draft;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.SubcommandGroup;
import ti4.commands.draft.andcat.AndcatDraftableGroup;
import ti4.commands.draft.faction.FactionDraftableGroup;
import ti4.commands.draft.manage.DraftManagerGroup;
import ti4.commands.draft.mantistile.MantisTileDraftableGroup;
import ti4.commands.draft.publicsnake.PublicSnakeDraftGroup;
import ti4.commands.draft.seat.SeatDraftableGroup;
import ti4.commands.draft.slice.SliceDraftableGroup;
import ti4.commands.draft.speakerorder.SpeakerOrderDraftableGroup;
import ti4.helpers.Constants;

public class DraftCommand implements ParentCommand {

    private final Map<String, SubcommandGroup> subcommandGroups = Stream.of(
                    new DraftManagerGroup(),
                    new FactionDraftableGroup(),
                    new SeatDraftableGroup(),
                    new SliceDraftableGroup(),
                    new SpeakerOrderDraftableGroup(),
                    new PublicSnakeDraftGroup(),
                    new MantisTileDraftableGroup(),
                    new AndcatDraftableGroup())
            .collect(Collectors.toMap(SubcommandGroup::getName, subcommandGroup -> subcommandGroup));

    @Override
    public String getName() {
        return Constants.DRAFT;
    }

    @Override
    public String getDescription() {
        return "Draft service commands";
    }

    @Override
    public Map<String, SubcommandGroup> getSubcommandGroups() {
        return subcommandGroups;
    }
}
