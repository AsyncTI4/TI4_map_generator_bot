package ti4.service.leader.genome;

import java.util.Optional;
import ti4.game.Game;
import ti4.game.Player;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentUse;

public class LeaningGenome<P> extends Genome<P> {

    private final AgentModule<P> delegate;

    public LeaningGenome(String leaderId, String genomeName, AgentModule<P> delegate) {
        super(leaderId, genomeName);
        this.delegate = delegate;
    }

    public static LeaningGenome<?> leaningOn(String leaderId, String genomeName, AgentModule<?> delegate) {
        return new LeaningGenome<>(leaderId, genomeName, delegate);
    }

    protected AgentModule<P> delegate() {
        return delegate;
    }

    @Override
    public Optional<P> decode(Game game, Player user, String payload) {
        return delegate.decode(game, user, payload);
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, P payload) {
        return delegate.unusableReason(game, user, payload);
    }

    @Override
    public String exhaustAnnouncementAfterName(AgentUse<P> use) {
        return delegate.exhaustAnnouncementAfterName(use);
    }

    @Override
    public AgentOutcome resolve(AgentUse<P> use) {
        return delegate.resolve(use);
    }
}
