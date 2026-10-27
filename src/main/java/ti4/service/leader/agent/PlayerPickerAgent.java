package ti4.service.leader.agent;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaRiderHelper;
import ti4.helpers.ButtonHelper;
import ti4.service.leader.agent.AgentOutcome.Message;

public abstract class PlayerPickerAgent extends AgentModule<PlayerPickerAgent.Choice> {

    static final String PICK_STEP = "agentPick_";
    // TODO: drop these once games started before the agent-module migration (2026-10-27) have finished.
    private static final Map<String, String> LEGACY_PICK_STEPS = Map.of(
            "nekroAgentRes_", "nekroagent",
            "kolleccAgentRes_", "kolleccagent",
            "hacanAgentRefresh_", "hacanagent",
            "pharadnAgentSelect_", "pharadnagent",
            "vaylerianAgent_", "vaylerianagent");

    public sealed interface Choice permits PickPlayer, ResolveFor {}

    private record PickPlayer() implements Choice {}

    private record ResolveFor(Player target) implements Choice {}

    record Pick(PlayerPickerAgent picker, Player target, boolean viaYssaril) {}

    protected PlayerPickerAgent(String agentId, String displayName) {
        super(agentId, displayName);
    }

    protected abstract AgentOutcome resolveFor(AgentUse<Player> use);

    @Override
    public final Optional<Choice> decode(Game game, Player user, String payload) {
        if (StringUtils.isEmpty(payload)) {
            return Optional.of(new PickPlayer());
        }
        return AgentTargets.player(game, payload).map(ResolveFor::new);
    }

    @Override
    public final AgentOutcome resolve(AgentUse<Choice> use) {
        return switch (use.payload()) {
            case ResolveFor resolveFor -> resolveFor(withTarget(use, resolveFor.target()));
            case PickPlayer ignored -> offerPlayers(use);
        };
    }

    protected static Message pickNote(AgentUse<Player> use) {
        Player user = use.user();
        return Message.plain(
                user,
                user.getFactionEmojiOrColor() + " selected " + use.payload().getFactionEmojiOrColor() + " as user of "
                        + use.agentName() + ".");
    }

    @ButtonHandler(PICK_STEP)
    @ButtonHandler("nekroAgentRes_")
    @ButtonHandler("kolleccAgentRes_")
    @ButtonHandler("hacanAgentRefresh_")
    @ButtonHandler("pharadnAgentSelect_")
    @ButtonHandler("vaylerianAgent_")
    static void resolvePick(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        Optional<Pick> pick = parsePick(game, buttonID);
        if (pick.isEmpty()) {
            AgentLifecycle.deliver(
                    AgentOutcome.of(Message.plain(player, "Could not find that player, please resolve manually.")),
                    event,
                    player);
            return;
        }
        PlayerPickerAgent picker = pick.get().picker();
        boolean viaYssaril = pick.get().viaYssaril();
        String agentName = AgentLifecycle.agentName(game, picker.agentId(), viaYssaril);
        AgentUse<Player> use = new AgentUse<>(game, player, pick.get().target(), agentName, viaYssaril, event);
        AgentLifecycle.deliver(picker.resolveFor(use), event, player);
        ButtonHelper.deleteMessage(event);
    }

    static Optional<Pick> parsePick(Game game, String buttonID) {
        AgentStepIds.Parsed step = AgentStepIds.parse(buttonID);
        String id = step.id();
        String agentId;
        String targetPayload;
        if (id.startsWith(PICK_STEP)) {
            String agentAndTarget = id.substring(PICK_STEP.length());
            agentId = StringUtils.substringBefore(agentAndTarget, "_");
            targetPayload = StringUtils.substringAfter(agentAndTarget, "_");
        } else {
            Optional<Map.Entry<String, String>> legacy = LEGACY_PICK_STEPS.entrySet().stream()
                    .filter(entry -> id.startsWith(entry.getKey()))
                    .findFirst();
            if (legacy.isEmpty()) {
                return Optional.empty();
            }
            agentId = legacy.get().getValue();
            targetPayload = id.substring(legacy.get().getKey().length());
        }
        Optional<PlayerPickerAgent> picker = AgentModules.find(agentId)
                .filter(PlayerPickerAgent.class::isInstance)
                .map(PlayerPickerAgent.class::cast);
        Optional<Player> target = AgentTargets.player(game, targetPayload);
        if (picker.isEmpty() || target.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Pick(picker.get(), target.get(), step.viaYssaril()));
    }

    private static AgentUse<Player> withTarget(AgentUse<Choice> use, Player target) {
        return new AgentUse<>(use.game(), use.user(), target, use.agentName(), use.viaYssaril(), use.event());
    }

    private AgentOutcome offerPlayers(AgentUse<Choice> use) {
        Player user = use.user();
        List<Button> players =
                AgendaRiderHelper.getPlayerOutcomeButtons(use.game(), null, PICK_STEP + agentId(), null).stream()
                        .map(button -> button.withCustomId(ownedStepId(user, button.getCustomId(), use.viaYssaril())))
                        .toList();
        return AgentOutcome.of(Message.addressedTo(
                user, ", please choose the faction on which you wish to use " + use.agentName() + ".", players));
    }
}
