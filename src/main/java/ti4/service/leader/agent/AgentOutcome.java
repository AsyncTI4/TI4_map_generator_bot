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

    public static AgentOutcome notifyInFog(Game game, Player primary, String message, Player affected) {
        return notifyInFog(game, primary, message, affected, message);
    }

    public static AgentOutcome notifyInFog(
            Game game, Player primary, String primaryMessage, Player affected, String affectedMessage) {
        AgentOutcome outcome = of(Message.to(primary, primaryMessage));
        if (game.isFowMode() && affected != primary) {
            return outcome.and(Message.to(affected, affectedMessage));
        }
        return outcome;
    }

    public static AgentOutcome offer(
            Player recipient, String promptAfterName, List<Button> buttons, String nothingToOfferAfterName) {
        if (buttons.isEmpty()) {
            return of(Message.addressedTo(recipient, nothingToOfferAfterName));
        }
        return of(Message.addressedTo(recipient, promptAfterName, buttons));
    }

    public AgentOutcome and(Message message) {
        List<Message> combined = new ArrayList<>(messages);
        combined.add(message);
        return new AgentOutcome(combined, pressedMessageEdit);
    }

    public AgentOutcome withPressedMessageEdit(String text) {
        return new AgentOutcome(messages, text);
    }

    public record Message(Player recipient, String text, List<Button> buttons, boolean secret, boolean addressed) {

        public Message {
            buttons = List.copyOf(buttons);
        }

        public static Message to(Player recipient, String text) {
            return new Message(recipient, text, List.of(), false, false);
        }

        public static Message withButtons(Player recipient, String text, List<Button> buttons) {
            return new Message(recipient, text, buttons, false, false);
        }

        public static Message addressedTo(Player recipient, String textAfterName) {
            return new Message(recipient, textAfterName, List.of(), false, true);
        }

        public static Message addressedTo(Player recipient, String textAfterName, List<Button> buttons) {
            return new Message(recipient, textAfterName, buttons, false, true);
        }

        public static Message privatelyTo(Player recipient, String text, List<Button> buttons) {
            return new Message(recipient, text, buttons, true, false);
        }
    }
}
