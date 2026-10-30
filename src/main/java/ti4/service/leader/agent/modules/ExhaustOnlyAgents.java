package ti4.service.leader.agent.modules;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.ExhaustOnlyAgent;

@UtilityClass
public class ExhaustOnlyAgents {

    public static final ExhaustOnlyAgent SAAR =
            new ExhaustOnlyAgent("saaragent", "Captain Mendosa, the Saar", "Saar Agent", FactionEmojis.Saar);
    public static final ExhaustOnlyAgent FIRMAMENT = new ExhaustOnlyAgent(
            "firmamentagent", "Myru Vos, the Firmament", "Firmament Agent", FactionEmojis.Firmament);
    public static final ExhaustOnlyAgent TITANS =
            new ExhaustOnlyAgent("titansagent", "Tellurian, the Titans", "Titans Agent", FactionEmojis.Titans);
    public static final ExhaustOnlyAgent THUNDARIAN = new ExhaustOnlyAgent(
            "nomadagentthundarian", "The Thundarian, a Nomad", "The Thundarian", FactionEmojis.Nomad);

    public static final List<ExhaustOnlyAgent> ALL = List.of(SAAR, FIRMAMENT, TITANS, THUNDARIAN);
}
