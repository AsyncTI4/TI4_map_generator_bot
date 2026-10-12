package ti4.ai.actioncards;

import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.AiCard.KeepFloor;
import ti4.ai.actioncards.AiCard.Window;

@UtilityClass
class AgendaCards {

    private static final KeepFloor PRIOR = (game, seat, alias) -> ActionCardPrior.keepPrior(alias);

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
