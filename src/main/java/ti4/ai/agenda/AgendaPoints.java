package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.isAgainst;
import static ti4.ai.agenda.AgendaValueMath.isFor;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;

@UtilityClass
public class AgendaPoints {

    private static final String HOLY_PLANET = "holy_planet_of_ixth";
    private static final String SEED_OF_AN_EMPIRE = "seed_empire";
    private static final String MUTINY = "mutiny";

    public static Set<Player> gainers(Game game, String outcome) {
        String agenda = AgendaPolicy.currentAgenda(game);
        if (AgendaPolicy.givesElectedPlayerAPoint(agenda)) return only(AgendaValueMath.electedPlayer(game, outcome));
        return switch (agenda) {
            case HOLY_PLANET -> only(holyPlanetOwner(game, outcome));
            case SEED_OF_AN_EMPIRE -> seedRecipients(game, outcome);
            case MUTINY -> isFor(outcome) ? live(AgendaHelper.getWinningVoters(AgendaValueMath.FOR, game)) : Set.of();
            default -> Set.of();
        };
    }

    @Nullable
    private static Player holyPlanetOwner(Game game, String outcome) {
        Planet planet = game.getPlanetsInfo().get(outcome);
        if (planet == null || !AgendaPolicy.hasRequiredTrait(HOLY_PLANET, planet)) return null;
        return BoardView.controller(game, outcome);
    }

    private static Set<Player> seedRecipients(Game game, String outcome) {
        if (isFor(outcome)) return live(DirectiveValues.seedRecipients(game, true));
        if (isAgainst(outcome)) return live(DirectiveValues.seedRecipients(game, false));
        return Set.of();
    }

    private static Set<Player> only(@Nullable Player player) {
        return player == null ? Set.of() : live(List.of(player));
    }

    private static Set<Player> live(List<Player> players) {
        return players.stream().filter(player -> !player.isEliminated()).collect(Collectors.toSet());
    }
}
