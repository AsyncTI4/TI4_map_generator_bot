package ti4.ai.brain;

import java.util.List;
import java.util.Set;
import ti4.ai.perception.AiPrompt;
import ti4.ai.perception.PromptButton;
import ti4.ai.profile.AiProfile;
import ti4.game.Game;
import ti4.game.Player;

public record AiTurnContext(
        Game game,
        Player seat,
        AiProfile profile,
        List<AiPrompt> prompts,
        Set<String> pressedKeys,
        AiMemory memory,
        long now) {

    public String turnKey() {
        return game.getActivePlayerID() + "@" + game.getLastActivePlayerChange().getTime();
    }

    public static String pressKey(AiPrompt prompt, PromptButton button) {
        return prompt.messageId() + "|" + button.customId();
    }

    public boolean alreadyPressed(AiPrompt prompt, PromptButton button) {
        return pressedKeys.contains(pressKey(prompt, button));
    }

    public boolean isActivePlayer() {
        return seat.getUserID().equals(game.getActivePlayerID());
    }

    public String faction() {
        return seat.getFaction();
    }
}
