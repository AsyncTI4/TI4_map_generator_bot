package ti4.service.leader.agent.modules;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.service.leader.agent.ExhaustOnlyAgent;

@UtilityClass
public class ExhaustOnlyAgents {

    public static final List<ExhaustOnlyAgent> ALL = List.of(
            new ExhaustOnlyAgent("saaragent", "Captain Mendosa, the Saar"),
            new ExhaustOnlyAgent("firmamentagent", "Myru Vos, the Firmament"),
            new ExhaustOnlyAgent("titansagent", "Tellurian, the Titans"),
            new ExhaustOnlyAgent("nomadagentthundarian", "The Thundarian, a Nomad"));
}
