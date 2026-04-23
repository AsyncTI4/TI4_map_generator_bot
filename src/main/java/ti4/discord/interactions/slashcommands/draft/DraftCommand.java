package ti4.discord.interactions.slashcommands.draft;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ti4.discord.interactions.slashcommands.ParentCommand;
import ti4.discord.interactions.slashcommands.SubcommandGroup;
import ti4.discord.interactions.slashcommands.draft.andcat.AndcatDraftableGroup;
import ti4.discord.interactions.slashcommands.draft.faction.FactionDraftableGroup;
import ti4.discord.interactions.slashcommands.draft.manage.DraftManagerGroup;
import ti4.discord.interactions.slashcommands.draft.mantistile.MantisTileDraftableGroup;
import ti4.discord.interactions.slashcommands.draft.publicsnake.PublicSnakeDraftGroup;
import ti4.discord.interactions.slashcommands.draft.seat.SeatDraftableGroup;
import ti4.discord.interactions.slashcommands.draft.slice.SliceDraftableGroup;
import ti4.discord.interactions.slashcommands.draft.speakerorder.SpeakerOrderDraftableGroup;
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
