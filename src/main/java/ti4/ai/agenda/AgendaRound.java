package ti4.ai.agenda;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;

@UtilityClass
public class AgendaRound {

    private static final String AGENDA_COUNT = "agendaCount";
    private static final String MUTINY = "mutiny";
    private static final int FINAL_AGENDA = 2;

    public static boolean isFinalAgenda(Game game) {
        String count = game.getStoredValue(AGENDA_COUNT);
        return StringUtils.isNumeric(count) && Integer.parseInt(count) >= FINAL_AGENDA;
    }

    public static boolean keepsAVoteBack(Game game) {
        return !isFinalAgenda(game) && !game.getDiscardAgendas().containsKey(MUTINY);
    }
}
