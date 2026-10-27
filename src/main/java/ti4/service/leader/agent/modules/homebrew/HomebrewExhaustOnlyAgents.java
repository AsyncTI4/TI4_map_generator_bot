package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.service.leader.agent.ExhaustOnlyAgent;

@UtilityClass
public class HomebrewExhaustOnlyAgents {

    public static final List<ExhaustOnlyAgent> ALL = List.of(
            new ExhaustOnlyAgent("gheminaagent", "Skarvald & Torvar, the Ghemina"),
            new ExhaustOnlyAgent("xanagent", "Noro Weba, the Xan"),
            new ExhaustOnlyAgent("nivynagent", "Suldhan Wraeg, the Nivyn"),
            new ExhaustOnlyAgent("edynagent", "Allant, the Edyn"),
            new ExhaustOnlyAgent("lanefiragent", "Vassa Hagi, the Lanefir"));
}
