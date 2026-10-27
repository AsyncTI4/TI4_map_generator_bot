package ti4.service.leader.agent.modules.homebrew;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.ids.AgentStepIds;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.AgendaRiderHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentTargets;
import ti4.service.leader.agent.AgentUse;

public final class VaylerianAgent extends AgentModule<VaylerianAgent.Choice> {

    static final String ID = "vaylerianagent";
    private static final String DISPLAY_NAME = "Yvin Korduul, the Vaylerian";
    private static final String PICK_TARGET_PREFIX = "vaylerianAgent";

    public VaylerianAgent() {
        super(ID, DISPLAY_NAME);
    }

    sealed interface Choice {}

    record DrawFor(Player target) implements Choice {}

    record PickTarget() implements Choice {}

    public static Button pickTargetButton(Player vaylerian) {
        return Buttons.gray(
                ownedButtonId(vaylerian, ID), useLabel(vaylerian, ID, "Vaylerian Agent"), FactionEmojis.vaylerian);
    }

    public static Button button(Player vaylerian) {
        return Buttons.green(
                ownedButtonId(vaylerian, ID, vaylerian.getFaction()),
                useLabel(vaylerian, ID, "Vaylerian Agent"),
                FactionEmojis.vaylerian);
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
            case DrawFor drawFor -> drawFor(use.game(), use.user(), drawFor.target(), use.agentName());
            case PickTarget ignored -> pickTarget(use);
        };
    }

    @ButtonHandler(PICK_TARGET_PREFIX + "_")
    public static void resolvePickedTarget(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentStepIds.Parsed step = AgentStepIds.parse(buttonID);
        Player target = AgentTargets.player(game, StringUtils.substringAfter(step.id(), "_"))
                .orElse(null);
        if (target == null) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Could not find that player, please resolve manually.");
            return;
        }
        String agentName = AgentLifecycle.agentName(DISPLAY_NAME, step.viaYssaril());
        for (Message message : drawFor(game, player, target, agentName).messages()) {
            MessageHelper.sendMessageToChannel(message.recipient().getCorrectChannel(), message.text());
        }
        ButtonHelper.deleteMessage(event);
    }

    private static AgentOutcome drawFor(Game game, Player user, Player target, String agentName) {
        ActionCardHelper.drawActionCards(target, 1);
        if (!game.isFowMode()) {
            return AgentOutcome.none();
        }
        return AgentOutcome.of(Message.to(
                user, target.getFactionEmojiOrColor() + " gained 1 action card from using " + agentName + "."));
    }

    private static AgentOutcome pickTarget(AgentUse<Choice> use) {
        Player user = use.user();
        List<Button> targets =
                AgendaRiderHelper.getPlayerOutcomeButtons(use.game(), null, PICK_TARGET_PREFIX, null).stream()
                        .map(button ->
                                button.withCustomId(AgentStepIds.flagCopy(button.getCustomId(), use.viaYssaril())))
                        .toList();
        return AgentOutcome.of(Message.withButtons(
                user,
                user.getRepresentationUnfogged() + ", please choose the faction on which you wish to use "
                        + use.agentName() + ".",
                targets));
    }
}
