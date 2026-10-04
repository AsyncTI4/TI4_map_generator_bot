package ti4.service.leader.agent.modules;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaRiderHelper;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class VaylerianAgent implements AgentModule<VaylerianAgent.Choice> {

    public static final String ID = "vaylerianagent";
    public static final String PICK_TARGET_PREFIX = "vaylerianAgent";

    public sealed interface Choice {}

    public record DrawFor(Player target) implements Choice {}

    public record PickTarget() implements Choice {}

    public static String buttonId() {
        return AgentButtonIds.format(ID);
    }

    public static String buttonId(Player target) {
        return AgentButtonIds.format(ID, target.getFaction());
    }

    @Override
    public String agentId() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Yvin Korduul, the Vaylerian";
    }

    @Override
    public Optional<Choice> decode(Game game, Player user, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.of(new PickTarget());
        }
        return AgentTargets.player(game, payload).map(DrawFor::new);
    }

    @Override
    public AgentOutcome resolve(AgentUse<Choice> use) {
        return switch (use.payload()) {
            case DrawFor drawFor -> drawFor(use, drawFor.target());
            case PickTarget ignored -> pickTarget(use);
        };
    }

    private static AgentOutcome drawFor(AgentUse<Choice> use, Player target) {
        ActionCardHelper.drawActionCards(target, 1);
        if (!use.game().isFowMode()) {
            return AgentOutcome.none();
        }
        return AgentOutcome.of(
                Message.to(use.user(), target.getFactionEmojiOrColor() + " gained 1 action card due to agent usage."));
    }

    private static AgentOutcome pickTarget(AgentUse<Choice> use) {
        Player user = use.user();
        return AgentOutcome.of(Message.withButtons(
                user,
                user.getRepresentationUnfogged() + ", please choose the faction on which you wish to use "
                        + use.agentName() + ".",
                AgendaRiderHelper.getPlayerOutcomeButtons(use.game(), null, PICK_TARGET_PREFIX, null)));
    }
}
