package ti4.service.leader.agent;

import java.util.List;
import java.util.Optional;

public interface AgentCatalog {

    List<AgentModule<?>> modules();

    default Optional<AgentModule<?>> find(String agentId) {
        return modules().stream()
                .filter(module -> module.agentId().equalsIgnoreCase(agentId)
                        || module.aliasIds().stream().anyMatch(agentId::equalsIgnoreCase))
                .findFirst();
    }
}
