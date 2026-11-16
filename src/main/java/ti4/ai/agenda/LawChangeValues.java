package ti4.ai.agenda;

import static ti4.ai.agenda.AgendaValueMath.FOR;
import static ti4.ai.agenda.AgendaValueMath.isFor;
import static ti4.ai.agenda.AgendaValueMath.liveRivals;
import static ti4.ai.agenda.AgendaValueMath.rivalGainCost;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.eval.BoardView;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.AgendaModel;
import ti4.model.Mapper;

@UtilityClass
class LawChangeValues {

    private static final Set<String> POINT_LAWS =
            Set.of("holy_planet_of_ixth", "shard_of_the_throne", "crown_of_emphidia");
    private static final int POINT = 1;
    private static final Set<String> LAW_CHANGES = Set.of("abolishment", "miscount", "constitution");
    private static final String FOR_AGAINST = "for/against";
    private static final String ELECT_PLAYER = "elect player";
    private static final double REVOTE_FLIP_CHANCE = 0.5;
    private static final double HOME_EXHAUST_SHARE = 0.75;

    static double judicialAbolishment(Game game, Player voter, boolean expertTable, String outcome) {
        if (!isLawInPlay(game, outcome)) return 0;
        return -lawInPlayValue(game, voter, expertTable, outcome);
    }

    static double miscountDisclosed(Game game, Player voter, boolean expertTable, String outcome) {
        if (!isLawInPlay(game, outcome)) return 0;
        double current = lawInPlayValue(game, voter, expertTable, outcome);
        return REVOTE_FLIP_CHANCE * (revoteAlternative(game, voter, expertTable, outcome) - current);
    }

    static double newConstitution(Game game, Player voter, boolean expertTable, String outcome) {
        if (!isFor(outcome)) return 0;
        double lawsLost = List.copyOf(game.getLaws().keySet()).stream()
                .mapToDouble(law -> lawInPlayValue(game, voter, expertTable, law))
                .sum();
        return -lawsLost - homeExhaustionCost(game, voter);
    }

    private static boolean isLawInPlay(Game game, String law) {
        return game.getLaws().containsKey(law) && !LAW_CHANGES.contains(law);
    }

    private static double lawInPlayValue(Game game, Player voter, boolean expertTable, String law) {
        if (LAW_CHANGES.contains(law)) return 0;
        if (POINT_LAWS.contains(law)) return pointLawValue(game, voter, law);
        String resolved = resolvedLawOutcome(game, law);
        if (resolved.isEmpty()) return 0;
        return AgendaValues.of(game, voter, false, expertTable, law, resolved, "")
                .orElse(0);
    }

    private static double pointLawValue(Game game, Player voter, String law) {
        Player holder = pointHolder(game, game.getLawsInfo().getOrDefault(law, ""));
        return holder == null ? 0 : AgendaValueMath.pointsTo(game, voter, holder, POINT);
    }

    @Nullable
    private static Player pointHolder(Game game, String subject) {
        Player elected = AgendaValueMath.electedPlayer(game, subject);
        if (elected != null) return elected;
        Player controller = BoardView.controller(game, subject);
        return controller != null && game.getRealPlayers().contains(controller) ? controller : null;
    }

    private static String resolvedLawOutcome(Game game, String law) {
        String subject = game.getLawsInfo().getOrDefault(law, "");
        if (StringUtils.isNotBlank(subject)) return subject;
        return targetContains(law, FOR_AGAINST) ? FOR : "";
    }

    private static boolean targetContains(String agenda, String target) {
        AgendaModel model = Mapper.getAgenda(agenda);
        return model != null && model.getTarget().toLowerCase().contains(target);
    }

    private static double revoteAlternative(Game game, Player voter, boolean expertTable, String law) {
        if (!targetContains(law, ELECT_PLAYER)) return 0;
        Player holder = game.getPlayerFromColorOrFaction(game.getLawsInfo().getOrDefault(law, ""));
        return game.getRealPlayers().stream()
                .filter(player -> player != holder && !player.isEliminated())
                .mapToDouble(player -> AgendaValues.of(game, voter, false, expertTable, law, player.getFaction(), "")
                        .orElse(0))
                .average()
                .orElse(0);
    }

    private static double homeExhaustionCost(Game game, Player voter) {
        double rivalsHurt = liveRivals(game, voter).stream()
                .mapToDouble(rival -> rivalGainCost(game, voter, rival, homeExhaustionLoss(rival)))
                .sum();
        return homeExhaustionLoss(voter) - rivalsHurt;
    }

    private static double homeExhaustionLoss(Player player) {
        return HOME_EXHAUST_SHARE * homeSpendable(player);
    }

    private static double homeSpendable(Player player) {
        Tile home = player.getHomeSystemTile();
        if (home == null) return 0;
        return home.getPlanetUnitHolders().stream()
                .filter(planet -> player.getPlanets().contains(planet.getName()))
                .mapToDouble(AgendaValueMath::spendValue)
                .sum();
    }
}
