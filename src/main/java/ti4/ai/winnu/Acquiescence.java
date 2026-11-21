package ti4.ai.winnu;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.game.Player;

@UtilityClass
public class Acquiescence {

    public static final String NOTE = "acq";
    public static final double HELD_VALUE = 1.0;
    public static final double GIVE_COST = 0.5;
    static final String PLAY_PREFIX = "winnuPNPlay_";

    public static Optional<Match> playButton(AiTurnContext context, int initiative) {
        if (!holdsForeign(context.seat())) return Optional.empty();
        String handler = PLAY_PREFIX + initiative;
        return Prompts.unowned(Prompts.newestFirst(context.prompts()), handler::equals)
                .filter(match -> match.prompt().isHidden())
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
    }

    static boolean holdsForeign(Player seat) {
        return seat.getPromissoryNotes().containsKey(NOTE) && !seat.ownsPromissoryNote(NOTE);
    }
}
