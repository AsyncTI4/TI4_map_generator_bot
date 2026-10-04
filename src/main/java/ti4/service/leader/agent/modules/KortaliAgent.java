package ti4.service.leader.agent.modules;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.ExploreModel;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class KortaliAgent extends TargetedAgent {

    public static final String ID = "kortaliagent";

    public static String buttonId(Player opponent) {
        return AgentButtonIds.format(ID, opponent.getColor());
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Queen Lucreia, the Kortali";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player target = use.payload();
        Player kortali = use.user();
        List<String> fragments = target.getFragments();
        if (fragments.isEmpty()) {
            return AgentOutcome.of(
                    Message.to(kortali, target.getFactionEmojiOrColor() + " has no relic fragments to take."));
        }
        String fragment = fragments.get(ThreadLocalRandom.current().nextInt(fragments.size()));
        target.removeFragment(fragment);
        kortali.addFragment(fragment);

        ExploreModel card = Mapper.getExplore(fragment);
        String fragmentName = card == null ? fragment : card.getName();
        String message = target.getFactionEmojiOrColor() + " lost a " + fragmentName + " to "
                + kortali.getFactionEmojiOrColor() + " due to " + use.agentName() + ".";
        return AgentOutcome.notifyInFog(use.game(), target, message, kortali, message);
    }
}
