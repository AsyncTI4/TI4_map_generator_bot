package ti4.service.combat;

import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.tuple.Pair;
import ti4.game.UnitHolder;
import ti4.model.UnitModel;

record UnitMergeResult(Map<Pair<UnitModel, UnitHolder>, Integer> units, Set<String> divergingModels) {}
