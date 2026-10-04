package ti4.service.leader.agent.modules;

import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;
import ti4.service.leader.agent.TargetedAgent;

public final class HyperAgent extends TargetedAgent {

    public static final String ID = "hyperagent";
    private static final String GENOME = "_Hyper Genome_";

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return GENOME;
    }

    @Override
    public String cardName(boolean viaYssaril) {
        return viaYssaril ? "Clever Clever " + GENOME : GENOME;
    }

    @Override
    public String exhaustAnnouncement(AgentUse<Player> use) {
        return use.user().getRepresentation() + " has exhausted the " + use.agentName() + ".";
    }

    @Override
    public AgentOutcome resolve(AgentUse<Player> use) {
        Player user = use.user();
        Player target = use.payload();
        boolean fog = use.game().isFowMode();

        String userMessage = user.getRepresentation() + " drew " + cardsDrawn(user);
        String targetMessage =
                target.getRepresentation() + " drew " + cardsDrawn(target) + (fog ? " via " + GENOME : "");
        if (target.getTg() > 0) {
            target.setTg(target.getTg() - 1);
            user.gainTG(1, true);
            userMessage += " and took 1 TG from " + target.getFactionEmojiOrColor() + ".";
            targetMessage += fog ? " and gave 1 TG." : " and gave 1 TG to " + user.getFactionEmojiOrColor() + ".";
        } else {
            userMessage += ".";
            targetMessage += ".";
        }

        ActionCardHelper.drawActionCardsSilent(user, 1);
        ActionCardHelper.drawActionCardsSilent(target, 1);
        return AgentOutcome.of(Message.to(user, userMessage), Message.to(target, targetMessage));
    }

    private static String cardsDrawn(Player player) {
        return player.hasAbility("scheming") ? "2 action cards (Scheming)" : "1 action card";
    }
}
