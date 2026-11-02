package ti4.service.leader.genome;

import ti4.service.leader.agent.AgentModule;

public abstract class Genome<P> extends AgentModule<P> {

    protected Genome(String leaderId, String genomeName) {
        super(leaderId, genomeName);
    }

    @Override
    public String reference(String cardName, boolean viaYssaril) {
        return viaYssaril ? "the Clever Clever _" + cardName + "_" : "the _" + cardName + "_";
    }
}
