package ti4.service.leader.agent;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaRiderHelper;
import ti4.helpers.ButtonHelper;
import ti4.service.leader.agent.AgentOutcome.Message;

public abstract class PlayerPickerAgent extends AgentModule<PlayerPickerAgent.Choice> {

    public sealed interface Choice {}

    public record PickPlayer() implements Choice {}

    public record ResolveFor(Player target) implements Choice {}

    private final String pickStepPrefix;

    protected PlayerPickerAgent(String agentId, String displayName, String pickStepPrefix) {
        super(agentId, displayName);
        this.pickStepPrefix = pickStepPrefix;
    }

    protected abstract AgentOutcome resolveFor(AgentUse<Choice> use, Player target);

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
            case ResolveFor resolveFor -> resolveFor(use, resolveFor.target());
            case PickPlayer ignored -> offerPlayers(use);
        };
    }

    public static void resolvePickedPlayer(
            String agentId, String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        PlayerPickerAgent picker = AgentModules.find(agentId)
                .filter(PlayerPickerAgent.class::isInstance)
                .map(PlayerPickerAgent.class::cast)
                .orElseThrow(() -> new IllegalArgumentException("No player picker agent for " + agentId));
        AgentStepIds.Parsed step = AgentStepIds.parse(buttonID);
        Optional<Player> target = AgentTargets.player(game, StringUtils.substringAfter(step.id(), "_"));
        if (target.isEmpty()) {
            AgentLifecycle.deliver(
                    AgentOutcome.of(Message.plain(player, "Could not find that player, please resolve manually.")),
                    event,
                    player);
            return;
        }
        String agentName = AgentLifecycle.agentName(AgentLifecycle.cardName(game, picker), step.viaYssaril());
        AgentUse<Choice> use =
                new AgentUse<>(game, player, new ResolveFor(target.get()), agentName, step.viaYssaril(), event);
        AgentLifecycle.deliver(picker.resolveFor(use, target.get()), event, player);
        ButtonHelper.deleteMessage(event);
    }

    private AgentOutcome offerPlayers(AgentUse<Choice> use) {
        Player user = use.user();
        List<Button> players =
                AgendaRiderHelper.getPlayerOutcomeButtons(use.game(), null, pickStepPrefix, null).stream()
                        .map(button -> button.withCustomId(user.factionButtonChecker()
                                + AgentStepIds.flagCopy(button.getCustomId(), use.viaYssaril())))
                        .toList();
        return AgentOutcome.of(Message.addressedTo(
                user, ", please choose the faction on which you wish to use " + use.agentName() + ".", players));
    }
}
