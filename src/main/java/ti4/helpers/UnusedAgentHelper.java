package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;

@UtilityClass
public class UnusedAgentHelper {

    public static String getUnusedAgent(Game game, Set<ComponentSource> extraSources) {
        return getUnusedAgent(game, extraSources, Set.of());
    }

    public static String getUnusedAgent(Game game, Set<ComponentSource> extraSources, Set<String> excludedAgents) {
        List<String> agents = new ArrayList<>();
        List<FactionModel> allFactions = Mapper.getFactionsValues().stream()
                .filter(f -> game.isDiscordantStarsMode()
                        ? f.getSource().isDs() || f.getSource().isOfficial() || extraSources.contains(f.getSource())
                        : f.getSource().isOfficial() || extraSources.contains(f.getSource()))
                .toList();

        for (FactionModel faction : allFactions) {
            String agentName = faction.getAlias() + "agent";
            if (agentName.contains("keleres")) {
                agentName = "keleresagent";
            }
            if (game.getFactions().contains(faction.getAlias())
                    || (Helper.getPlayerFromLeader(game, agentName) != null)
                    || agents.contains(agentName)
                    || Mapper.getLeader(agentName) == null
                    || "unknown".equalsIgnoreCase(Mapper.getLeader(agentName).getAbilityText())
                    || game.getStoredValue("fakeAgents").contains(agentName)
                    || excludedAgents.contains(agentName)) {
                continue;
            }
            agents.add(agentName);
        }
        if (!agents.isEmpty()) {
            Collections.shuffle(agents);
            return agents.getFirst();
        }
        return null;
    }
}
