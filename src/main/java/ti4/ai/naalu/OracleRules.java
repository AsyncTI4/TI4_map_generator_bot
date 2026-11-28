package ti4.ai.naalu;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiDecision;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.brain.Prompts;
import ti4.ai.brain.Prompts.Match;
import ti4.game.Game;
import ti4.game.Player;

@UtilityClass
public class OracleRules {

    static final String HERO = "naaluhero";
    static final String HERO_BUTTON = "naaluHeroInitiation";

    public static Optional<AiDecision> play(AiTurnContext context) {
        Player seat = context.seat();
        if (!seat.hasLeaderUnlocked(HERO)) return Optional.empty();
        Optional<Match> button = Prompts.unowned(Prompts.newestFirst(context.prompts()), HERO_BUTTON::equals)
                .filter(match -> match.prompt().isHidden())
                .filter(match -> !context.alreadyPressed(match.prompt(), match.button()));
        if (button.isEmpty()) return Optional.empty();
        if (givers(context.game(), seat) == 0) return Optional.empty();
        return Optional.of(button.get().press("play The Oracle to take a promissory note from every other player"));
    }

    static long givers(Game game, Player seat) {
        return game.getRealPlayers().stream()
                .filter(other -> other != seat)
                .filter(OracleRules::holdsANoteInHand)
                .count();
    }

    private static boolean holdsANoteInHand(Player player) {
        return player.getPromissoryNotes().keySet().stream()
                .anyMatch(note -> !player.getPromissoryNotesInPlayArea().contains(note));
    }
}
