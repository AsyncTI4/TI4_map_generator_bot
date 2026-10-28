package ti4.service.leader.agent.modules.homebrew.theodisi;

import java.util.List;
import java.util.Optional;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.agent.AgentLifecycle;
import ti4.service.leader.agent.AgentModule;
import ti4.service.leader.agent.AgentOutcome;
import ti4.service.leader.agent.AgentOutcome.Message;
import ti4.service.leader.agent.AgentUse;

public final class VerydithAgent extends AgentModule<VerydithAgent.NoteExchange> {

    static final String ID = "verydithagent";
    private static final String CHOOSE_SENDER = "selectFirstTargetVerydithAgent";
    private static final String CHOOSE_RECEIVER = "selectSecondTargetVerydithAgent_";
    private static final String LEGACY_RESOLVE = "resolveVerydithAgent_";
    private static final String SEPARATOR = "|";

    public record NoteExchange(Player receiver, Player sender) {}

    public VerydithAgent() {
        super(ID, "Seris Kael, the Verydith");
    }

    public static Button cardsInfoButton(Player holder) {
        return Buttons.gray(
                holder.factionButtonChecker() + CHOOSE_SENDER,
                useLabel(holder, ID, "Seris Kael"),
                FactionEmojis.verydith);
    }

    @ButtonHandler(CHOOSE_SENDER)
    static void chooseSender(ButtonInteractionEvent event, Game game, Player holder) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            return;
        }
        List<Button> buttons = game.getRealPlayers().stream()
                .map(sender -> Buttons.green(
                        holder.factionButtonChecker() + CHOOSE_RECEIVER + sender.getFaction(),
                        sender.getFactionNameOrColor(),
                        sender.getFactionEmojiOrColor()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(), "Please choose the person returning the promissory note.", buttons);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler(CHOOSE_RECEIVER)
    static void chooseReceiver(ButtonInteractionEvent event, Game game, Player holder, String buttonID) {
        if (!holder.hasUnexhaustedLeader(ID)) {
            return;
        }
        Player sender = game.getPlayerFromColorOrFaction(buttonID.substring(CHOOSE_RECEIVER.length()));
        if (sender == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find player.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Please choose the person receiving the promissory note.",
                receiverButtons(game, holder, sender));
        ButtonHelper.deleteMessage(event);
    }

    static List<Button> receiverButtons(Game game, Player holder, Player sender) {
        return game.getRealPlayers().stream()
                .filter(receiver -> receiver != sender)
                .map(receiver -> Buttons.green(
                        ownedButtonId(holder, ID, receiver.getFaction() + SEPARATOR + sender.getFaction()),
                        receiver.getFactionNameOrColor(),
                        receiver.getFactionEmojiOrColor()))
                .toList();
    }

    // TODO: drop this once games started before the agent-module migration (2026-10-27) have finished.
    @ButtonHandler(LEGACY_RESOLVE)
    static void legacyResolve(String buttonID, ButtonInteractionEvent event, Game game, Player player) {
        AgentLifecycle.pressLegacyButton(event, game, player, ID, buttonID.substring(LEGACY_RESOLVE.length()));
    }

    @Override
    public Optional<NoteExchange> decode(Game game, Player user, String payload) {
        String[] parts = StringUtils.split(payload, SEPARATOR);
        if (parts == null || parts.length != 2) {
            return Optional.empty();
        }
        Player receiver = game.getPlayerFromColorOrFaction(parts[0]);
        Player sender = game.getPlayerFromColorOrFaction(parts[1]);
        if (receiver == null || sender == null) {
            return Optional.empty();
        }
        return Optional.of(new NoteExchange(receiver, sender));
    }

    @Override
    public Optional<String> unusableReason(Game game, Player user, NoteExchange exchange) {
        if (exchange.receiver() == exchange.sender()) {
            return Optional.of("A player cannot return a promissory note to themselves");
        }
        return Optional.empty();
    }

    @Override
    public AgentOutcome resolve(AgentUse<NoteExchange> use) {
        Player receiver = use.payload().receiver();
        Player sender = use.payload().sender();
        return AgentOutcome.of(gainCommandTokenOffer(use, receiver), gainCommandTokenOffer(use, sender))
                .deletingPressedMessage();
    }

    private static Message gainCommandTokenOffer(AgentUse<NoteExchange> use, Player player) {
        return Message.addressedTo(
                player,
                ", you may gain 1 command token due to " + use.agentNameFor(player) + ".",
                ButtonHelper.getGainCCButtons(player));
    }
}
