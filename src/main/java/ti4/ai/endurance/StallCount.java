package ti4.ai.endurance;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import ti4.ai.actioncards.AiActionCards;
import ti4.ai.explore.RelicActionRules;
import ti4.ai.scoring.ScoringReserve;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitType;
import ti4.model.ActionCardModel;
import ti4.model.DeckModel;
import ti4.model.Mapper;
import ti4.model.RelicModel;
import ti4.model.TechnologyModel;

@UtilityClass
public class StallCount {

    private static final String ACTION_TEXT = "ACTION";
    private static final String LEADER_ACTION = "ACTION:";
    private static final String COMPONENT_WINDOW = "Action";
    private static final double CARD_RELIABILITY = 0.8;
    private static final double FALLBACK_COMPONENT_SHARE = 0.25;
    private static final Set<String> OWN_ACTION_TECHS = Set.of(Endurance.SLING_RELAY, "pm");
    private static final Set<String> CONDITIONAL_TECHS =
            Set.of("tf-fabrication", "tf-orbitaldrop", "tf-mantlecracking", "tf-stalltactics", "lgf", "vtx");

    public static double of(Game game, Player player, Player viewer) {
        boolean own = player == viewer;
        return tokens(game, player, own)
                + unplayedStrategyCards(game, player)
                + (own ? ownComponents(game, player) : publicComponents(player))
                + (own ? ownHand(player) : hiddenHand(game, player));
    }

    static int tokens(Game game, Player player, boolean own) {
        int tactical = Math.max(0, player.getTacticalCC());
        if (!own) return tactical;
        int reserved = ScoringReserve.of(game, player).tokens();
        return Math.max(0, Math.min(tactical, tactical + player.getStrategicCC() - 1 - reserved));
    }

    static long unplayedStrategyCards(Game game, Player player) {
        return player.getSCs().stream()
                .filter(card -> !game.getPlayedSCs().contains(card))
                .count();
    }

    private static int ownComponents(Game game, Player seat) {
        int actionTechs =
                (int) OWN_ACTION_TECHS.stream().filter(seat::hasTechReady).count();
        if (seat.hasTechReady(Endurance.SLING_RELAY) && !hasSpaceDock(game, seat)) actionTechs--;
        return actionTechs + bioStimsRefill(seat) + RelicActionRules.stallsAvailable(seat);
    }

    private static boolean hasSpaceDock(Game game, Player seat) {
        return !ButtonHelper.getTilesOfPlayersSpecificUnits(game, seat, UnitType.Spacedock)
                .isEmpty();
    }

    private static int bioStimsRefill(Player player) {
        boolean usesATech = OWN_ACTION_TECHS.stream().anyMatch(player::hasTech);
        return usesATech && player.hasTechReady(Endurance.BIO_STIMS) ? 1 : 0;
    }

    private static double publicComponents(Player player) {
        long actionTechs = readyTechs(player).filter(StallCount::hasAction).count();
        boolean refills = player.hasTechReady(Endurance.BIO_STIMS) && (actionTechs > 0 || exhaustedActionTech(player));
        return actionTechs + (refills ? 1 : 0) + readyRelicActions(player) + readyLeaderActions(player);
    }

    private static Stream<String> readyTechs(Player player) {
        return player.getTechs().stream()
                .filter(tech -> !player.getExhaustedTechs().contains(tech));
    }

    private static boolean exhaustedActionTech(Player player) {
        return player.getExhaustedTechs().stream().anyMatch(StallCount::hasAction);
    }

    private static boolean hasAction(String tech) {
        TechnologyModel model = Mapper.getTech(tech);
        return model != null
                && !CONDITIONAL_TECHS.contains(tech)
                && model.getText().contains(ACTION_TEXT);
    }

    private static long readyRelicActions(Player player) {
        return player.getRelics().stream()
                .filter(relic -> !player.getExhaustedRelics().contains(relic))
                .map(Mapper::getRelic)
                .filter(model -> model != null && relicHasAction(model))
                .count();
    }

    private static boolean relicHasAction(RelicModel relic) {
        return relic.getText() != null && relic.getText().contains(ACTION_TEXT);
    }

    private static long readyLeaderActions(Player player) {
        return player.getLeaders().stream()
                .filter(leader -> !leader.isExhausted() && !leader.isLocked() && !leader.isActive())
                .map(Leader::getId)
                .map(Mapper::getLeader)
                .filter(model -> model != null && LEADER_ACTION.equalsIgnoreCase(model.getAbilityWindow()))
                .count();
    }

    private static double ownHand(Player seat) {
        return CARD_RELIABILITY
                * seat.getActionCards().keySet().stream()
                        .filter(AiActionCards::isComponentCard)
                        .count();
    }

    private static double hiddenHand(Game game, Player player) {
        return CARD_RELIABILITY * componentShare(game) * player.getAcCount();
    }

    static double componentShare(Game game) {
        DeckModel deck = Mapper.getDeck(game.getAcDeckID());
        if (deck == null || deck.getCardIDs().isEmpty()) return FALLBACK_COMPONENT_SHARE;
        List<String> cards = deck.getCardIDs();
        long components = cards.stream()
                .map(Mapper::getActionCard)
                .filter(model -> model != null && isComponentAction(model))
                .count();
        return (double) components / cards.size();
    }

    private static boolean isComponentAction(ActionCardModel model) {
        return COMPONENT_WINDOW.equalsIgnoreCase(model.getWindow());
    }
}
