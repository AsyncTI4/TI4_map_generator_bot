package ti4.service.leader.agent.modules.homebrew;

import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaRiderHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class VaylerianAgent extends AgentModule<VaylerianAgent.Choice> {

    static final String ID = "vaylerianagent";
    private static final String PICK_TARGET_PREFIX = "vaylerianAgent";

    public VaylerianAgent() {
        super(ID, "Yvin Korduul, the Vaylerian");
    }

    sealed interface Choice {}

    record DrawFor(Player target) implements Choice {}

    record PickTarget() implements Choice {}

    public static Button button() {
        return Buttons.gray(AgentButtonIds.format(ID), "Use Vaylerian Agent", FactionEmojis.vaylerian);
    }

    public static Button button(Player target) {
        return Buttons.green(
                AgentButtonIds.format(ID, target.getFaction()), "Use Vaylerian Agent", FactionEmojis.vaylerian);
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
