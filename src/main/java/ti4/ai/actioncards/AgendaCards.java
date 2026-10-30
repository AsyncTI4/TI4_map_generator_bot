package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.actioncards.AiCard.KeepFloor;
import ti4.ai.actioncards.AiCard.Window;
import ti4.ai.brain.AiTurnContext;

@UtilityClass
class AgendaCards {

    static final String VETO = "veto";
    static final String VETO_TITLE = "Veto";
    private static final String REVEALED_AT = "lastAgendaReactTime";
    private static final long FUTURE_SKEW_MILLIS = 60_000L;
    private static final long REVEAL_GUARD_MILLIS = 7_000L;
    private static final KeepFloor PRIOR = (game, seat, alias) -> ActionCardPrior.keepPrior(alias);

    static AiCard veto() {
        return new AiCard(
                VETO,
                alias -> VETO_TITLE.equals(SabotageWindow.title(alias)),
                Window.AGENDA_WHEN,
                (game, seat) -> Optional.empty(),
                PRIOR,
                "resolveVeto",
                List.of(),
                null);
    }

    static boolean revealSettled(AiTurnContext context) {
        String revealed = context.game().getStoredValue(REVEALED_AT);
        if (revealed.isEmpty() || !StringUtils.isNumeric(revealed)) return true;
        long at = Long.parseLong(revealed);
        return at > context.now() + FUTURE_SKEW_MILLIS || context.now() >= at + REVEAL_GUARD_MILLIS;
    }

    static AiCard rider(String title) {
        return new AiCard(
                title,
                alias -> title.equals(SabotageWindow.title(alias)),
                Window.AGENDA_AFTER,
                (game, seat) -> Optional.empty(),
                PRIOR,
                null,
                List.of(),
                null);
    }
}
