package ti4.ai.agenda;

import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;

@UtilityClass
public class AgendaRound {

    private static final String AGENDA_COUNT = "agendaCount";
    private static final String MUTINY = "mutiny";
    private static final int FINAL_AGENDA = 2;
    private static final String REPRESENTATIVE_GOVERNMENT = "rep_govt";
    private static final String EXECUTIVE_ORDER = "executiveOrder";

    public static boolean isFinalAgenda(Game game) {
        String count = game.getStoredValue(AGENDA_COUNT);
        return StringUtils.isNumeric(count) && Integer.parseInt(count) >= FINAL_AGENDA;
    }

    public static boolean keepsAVoteBack(Game game) {
        return !isFinalAgenda(game) && !game.getDiscardAgendas().containsKey(MUTINY);
    }

    public static boolean underRepresentativeGovernment(Game game) {
        return game.getLaws() != null
                && game.getLaws().containsKey(REPRESENTATIVE_GOVERNMENT)
                && game.getStoredValue(EXECUTIVE_ORDER).isEmpty();
    }
}
