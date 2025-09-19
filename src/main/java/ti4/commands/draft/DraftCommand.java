package ti4.commands.draft;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.commands.ParentCommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;

public class DraftCommand implements ParentCommand {

    private final Map<String, SubcommandGroup> subcommandGroups = Stream.of(
                    new DraftManagerSubcommands(),
                    new FactionDraftableSubcommands(),
                    new SeatDraftableSubcommands(),
                    new SliceDraftableSubcommands(),
                    new SpeakerOrderDraftableSubcommands(),
                    new PublicSnakeDraftOrchestratorSubcommands())
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
