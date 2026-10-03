package ti4.service.leader.agent;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.buttons.Button;
import ti4.game.Player;

public record AgentOutcome(List<Message> messages) {

    public AgentOutcome {
        messages = List.copyOf(messages);
    }

    public static AgentOutcome none() {
        return new AgentOutcome(List.of());
    }

    public static AgentOutcome of(Message... messages) {
        return new AgentOutcome(List.of(messages));
    }

    public AgentOutcome and(Message message) {
        List<Message> combined = new ArrayList<>(messages);
        combined.add(message);
        return new AgentOutcome(combined);
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
    }
}
