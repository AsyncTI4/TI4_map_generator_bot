package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.ExhaustOnlyAgent;

@UtilityClass
public class HomebrewExhaustOnlyAgents {

    public static final ExhaustOnlyAgent GHEMINA = new ExhaustOnlyAgent(
            "gheminaagent", "Skarvald & Torvar, the Ghemina", "Ghemina Agents", FactionEmojis.ghemina);
    public static final ExhaustOnlyAgent XAN =
            new ExhaustOnlyAgent("xanagent", "Noro Weba, the Xan", "Xan Agent", FactionEmojis.xan);
    public static final ExhaustOnlyAgent NIVYN =
            new ExhaustOnlyAgent("nivynagent", "Suldhan Wraeg, the Nivyn", "Nivyn Agent", FactionEmojis.nivyn);
    public static final ExhaustOnlyAgent EDYN =
            new ExhaustOnlyAgent("edynagent", "Allant, the Edyn", "Edyn Agent", FactionEmojis.edyn);
    public static final ExhaustOnlyAgent LANEFIR =
            new ExhaustOnlyAgent("lanefiragent", "Vassa Hagi, the Lanefir", "Lanefir Agent", FactionEmojis.lanefir);

    public static final List<ExhaustOnlyAgent> ALL = List.of(GHEMINA, XAN, NIVYN, EDYN, LANEFIR);
}
