package ti4.ai.strategy;

import java.util.List;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.ObjectivePolicy;
import ti4.ai.secrets.SpyNetwork;
import ti4.ai.strategy.CardPlan.Benefit;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.service.info.ListPlayerInfoService;

@UtilityClass
class PoliticsUses {

    private static final int CARDS_DRAWN = 2;
    private static final int PUBLICS_FOR_IMPERIAL = 2;

    static List<Benefit> benefits(PlanInputs in, int initiative) {
        return List.of(
                new Benefit(CardUse.POLITICS_SPEAKER, speakerValue(in)),
                new Benefit(CardUse.POLITICS_ACTION_CARDS, actionCardValue(in)),
                new Benefit(CardUse.POLITICS_AGENDA, in.agendaPhaseActive() ? CardValueScale.AGENDA_PEEK : 0));
    }

    static double tokenValue(Game game, Player seat) {
        return isSpeaker(game, seat) ? 0 : speakerValue(game, seat);
    }

    private static double speakerValue(PlanInputs in) {
        return speakerValue(in.game(), in.seat());
    }

    private static double speakerValue(Game game, Player seat) {
        if (isSpeaker(game, seat)) return CardValueScale.SPEAKER_HANDOFF_SHARE * CardValueScale.SPEAKER;
        double value = CardValueScale.SPEAKER;
        if (wantsImperialNextRound(game, seat)) value += CardValueScale.SPEAKER_FOR_IMPERIAL;
        if (game.isCustodiansScored()) value += CardValueScale.AGENDA_PEEK;
        return value;
    }

    private static boolean isSpeaker(Game game, Player seat) {
        return seat.getUserID().equals(game.getSpeakerUserID());
    }

    private static boolean wantsImperialNextRound(Game game, Player seat) {
        if (seat.controlsMecatol(true)) return true;
        long nearlyMet = game.getRevealedPublicObjectives().keySet().stream()
                .filter(id -> Mapper.getPublicObjective(id) != null)
                .filter(id -> !ObjectivePolicy.hasScored(game, seat, id))
                .filter(id -> isWithinOneStep(game, seat, id))
                .count();
        return nearlyMet >= PUBLICS_FOR_IMPERIAL;
    }

    private static boolean isWithinOneStep(Game game, Player seat, String objective) {
        int threshold = ListPlayerInfoService.getObjectiveThreshold(objective, game);
        return threshold > 0
                && ListPlayerInfoService.getPlayerProgressOnObjective(objective, game, seat) >= threshold - 1;
    }

    private static double actionCardValue(PlanInputs in) {
        Game game = in.game();
        Player seat = in.seat();
        if (StrategyCardRules.holdsNoActionCards(seat)) return 0;
        int drawn = Math.clamp(ButtonHelper.getACLimit(game, seat) - seat.getAcCount(), 0, CARDS_DRAWN);
        double value = drawn * CardValueScale.ACTION_CARD;
        if (SpyNetwork.withinReach(game, seat, CARDS_DRAWN)) {
            value += CardValueScale.SPEND_UNLOCK_SHARE * CardValueScale.VICTORY_POINT;
        }
        return value;
    }
}
