package ti4.service.leader.agent;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Game;
import ti4.game.Player;

public record AgentOutcome(List<Message> messages, String pressedMessageEdit) {

    public AgentOutcome {
        messages = List.copyOf(messages);
    }

    public static AgentOutcome none() {
        return new AgentOutcome(List.of(), null);
    }

    public static AgentOutcome of(Message... messages) {
        return new AgentOutcome(List.of(messages), null);
    }

    public static AgentOutcome notifyInFog(
            Game game, Player primary, String primaryMessage, Player affected, String affectedMessage) {
        AgentOutcome outcome = of(Message.to(primary, primaryMessage));
        if (game.isFowMode() && affected != primary) {
            return outcome.and(Message.to(affected, affectedMessage));
        }
        return outcome;
    }

    public AgentOutcome and(Message message) {
        List<Message> combined = new ArrayList<>(messages);
        combined.add(message);
        return new AgentOutcome(combined, pressedMessageEdit);
    }

    public AgentOutcome and(AgentOutcome other) {
        List<Message> combined = new ArrayList<>(messages);
        combined.addAll(other.messages());
        String edit = other.pressedMessageEdit() != null ? other.pressedMessageEdit() : pressedMessageEdit;
        return new AgentOutcome(combined, edit);
    }

    public AgentOutcome withPressedMessageEdit(String text) {
        return new AgentOutcome(messages, text);
    }

    public record Message(Player recipient, String text, List<Button> buttons) {

        public Message {
            buttons = List.copyOf(buttons);
        }

        public static Message to(Player recipient, String text) {
            return new Message(recipient, text, List.of());
        }

        public static Message withButtons(Player recipient, String text, List<Button> buttons) {
            return new Message(recipient, text, buttons);
        }

        public static Message inPressedChannel(String text) {
            return new Message(null, text, List.of());
        }

        public boolean goesToPressedChannel() {
            return recipient == null;
        }
    }
}
