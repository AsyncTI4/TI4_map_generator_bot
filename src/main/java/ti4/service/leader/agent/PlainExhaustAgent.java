package ti4.service.leader.agent;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.Mapper;

public final class PlainExhaustAgent extends AgentModule<String> {

    private PlainExhaustAgent(String agentId, String displayName) {
        super(agentId, displayName);
    }

    public static PlainExhaustAgent forLeader(String leaderId) {
        String displayName = Optional.ofNullable(Mapper.getLeader(leaderId))
                .map(PlainExhaustAgent::nameWithFaction)
                .orElse(leaderId);
        return new PlainExhaustAgent(leaderId, displayName);
    }

    private static String nameWithFaction(LeaderModel leader) {
        FactionModel faction = leader.getFaction() == null ? null : Mapper.getFaction(leader.getFaction());
        if (faction == null || faction.getShortName() == null) {
            return leader.getName();
        }
        return leader.getName() + ", the " + faction.getShortName();
    }

    @Override
    public Optional<String> decode(Game game, Player user, String payload) {
        return Optional.of(payload);
    }

    @Override
    public AgentOutcome resolve(AgentUse<String> use) {
        return AgentOutcome.none();
    }
}
