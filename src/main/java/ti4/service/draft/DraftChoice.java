package ti4.service.draft;

import lombok.Data;
import net.dv8tion.jda.api.components.buttons.Button;

@Data
public class DraftChoice {
    private final DraftableType type;
    /**
     * A unique key for this choice, all lowercase alpha-numeric characters.
     * Used in commands.
     */
    private final String choiceKey;
    private final Button button;
    private final String displayName;
    /**
     * An optional emoji which should uniquely identify this choice to players.
     * Used to produce very shorthand summaries of player choices.
     * Ex. "Titans of Ul" can use the faction symbol.
     * Ex. franken's "Terragenisis" doesn't have a unique emoji, so this would be null.
     */
    private final String identifyingEmoji;
}
