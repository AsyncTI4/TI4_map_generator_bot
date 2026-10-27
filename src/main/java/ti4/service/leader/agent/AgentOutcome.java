package ti4.service.leader.agent;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;

public record AgentOutcome(List<Message> messages, String pressedMessageEdit, boolean deletesPressedMessage) {

    public AgentOutcome {
        messages = List.copyOf(messages);
    }

    public static AgentOutcome none() {
        return new AgentOutcome(List.of(), null, false);
    }

    public static AgentOutcome of(Message... messages) {
        return new AgentOutcome(List.of(messages), null, false);
    }

    public static AgentOutcome notifyInFog(Game game, Player primary, String message, Player affected) {
        return notifyInFog(game, primary, message, affected, message);
    }

    public static AgentOutcome notifyInFog(
            Game game, Player primary, String primaryMessage, Player affected, String affectedMessage) {
        AgentOutcome outcome = of(Message.plain(primary, primaryMessage));
        if (game.isFowMode() && affected != primary) {
            return outcome.and(Message.plain(affected, affectedMessage));
        }
        return outcome;
    }

    public static AgentOutcome offer(Message prompt, Message whenNothingToOffer) {
        return of(prompt.buttons().isEmpty() ? whenNothingToOffer : prompt);
    }

    public AgentOutcome and(Message message) {
        List<Message> combined = new ArrayList<>(messages);
        combined.add(message);
        return new AgentOutcome(combined, pressedMessageEdit, deletesPressedMessage);
    }

    public AgentOutcome withPressedMessageEdit(String text) {
        return new AgentOutcome(messages, text, deletesPressedMessage);
    }

    public AgentOutcome deletingPressedMessage() {
        return new AgentOutcome(messages, pressedMessageEdit, true);
    }

    public record Message(
            Player recipient,
            String text,
            List<Button> buttons,
            Route route,
            boolean namesRecipient,
            boolean pagedButtons) {

        public enum Route {
            REPLY,
            EVERYONE,
            CARDS_INFO_THREAD
        }

        public Message {
            buttons = List.copyOf(buttons);
        }

        public static Message plain(Player recipient, String text) {
            return plain(recipient, text, List.of());
        }

        public static Message plain(Player recipient, String text, List<Button> buttons) {
            return new Message(recipient, text, buttons, Route.REPLY, false, false);
        }

        public static Message addressedTo(Player recipient, String textAfterName) {
            return addressedTo(recipient, textAfterName, List.of());
        }

        public static Message addressedTo(Player recipient, String textAfterName, List<Button> buttons) {
            return new Message(recipient, textAfterName, buttons, Route.REPLY, true, false);
        }

        public Message inCardsInfoThread() {
            return new Message(recipient, text, buttons, Route.CARDS_INFO_THREAD, namesRecipient, pagedButtons);
        }

        public Message forEveryone() {
            return new Message(recipient, text, buttons, Route.EVERYONE, namesRecipient, pagedButtons);
        }

        public Message withPagedButtons() {
            return new Message(recipient, text, buttons, route, namesRecipient, true);
        }
    }
}
