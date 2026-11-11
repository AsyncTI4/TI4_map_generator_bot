package ti4.ai.agenda;

import java.util.List;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;

@UtilityClass
class ExpertAgendaValues {

    private interface Group {
        @Nullable
        Double valueOf(AgendaView view, String agenda, String outcome, String ownVote);
    }

    private static final List<Group> GROUPS = List.of(
            ExpertElectionValues::valueOf,
            ExpertPlanetValues::valueOf,
            ExpertLawValues::valueOf,
            ExpertDirectiveValues::valueOf,
            ExpertCardValues::valueOf,
            ExpertMechAgendaValues::valueOf);

    @Nullable
    static Double valueOf(AgendaView view, String agenda, String outcome, String ownVote) {
        for (Group group : GROUPS) {
            Double value = group.valueOf(view, agenda, outcome, ownVote);
            if (value != null) return value;
        }
        return null;
    }
}
