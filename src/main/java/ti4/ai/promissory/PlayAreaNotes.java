package ti4.ai.promissory;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.scoring.ThronePoint;
import ti4.ai.tactical.TacticalPlan;
import ti4.ai.tactical.TacticalRules;
import ti4.ai.trade.AllianceValue;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;
import ti4.model.Mapper;
import ti4.model.PromissoryNoteModel;

@UtilityClass
public class PlayAreaNotes {

    private static final String OWNERS_KEY = "playAreaOwnersAtActionStart|";
    private static final String SEPARATOR = ",";
    private static final String ACTION_PHASE = "action";
    private static final String RETURN_TEXT = "return this card";
    private static final String ACTIVATION_TEXT = "you activate a system that contains";
    private static final String SUPPORT_FOR_THE_THRONE = "_sftt";
    private static final double OTHER_NOTE_VALUE = 1.0;
    private static final double CEASEFIRE_BLOCK_SHARE = 0.75;

    public static boolean heldAtActionStart(AiTurnContext context, Player owner) {
        String owners = context.memory().get(OWNERS_KEY + context.turnKey()).orElse("");
        return List.of(StringUtils.split(owners, SEPARATOR)).contains(owner.getFaction());
    }

    public static double adjustedScore(Game game, Player seat, Tile tile, TacticalPlan plan) {
        double score = plan.score() - returnCost(game, seat, tile);
        if (plan.moves().isEmpty()) return score;
        return score - Math.max(0, score) * CEASEFIRE_BLOCK_SHARE * CeasefireRules.blockChance(game, seat, tile);
    }

    static void observe(AiTurnContext context) {
        Game game = context.game();
        if (!context.isActivePlayer()
                || !ACTION_PHASE.equalsIgnoreCase(game.getPhaseOfGame())
                || TacticalRules.activatedThisTurn(context)) {
            return;
        }
        String owners = context.seat().getPromissoryNotesInPlayArea().stream()
                .map(game::getPNOwner)
                .filter(Objects::nonNull)
                .map(Player::getFaction)
                .distinct()
                .collect(Collectors.joining(SEPARATOR));
        context.memory().put(OWNERS_KEY + context.turnKey(), owners);
    }

    static double returnCost(Game game, Player seat, Tile tile) {
        double cost = 0;
        for (String note : seat.getPromissoryNotesInPlayArea()) {
            Player owner = game.getPNOwner(note);
            if (owner == null || owner == seat || !returnsOnActivation(note)) continue;
            if (!FoWHelper.playerHasUnitsInSystem(owner, tile)) continue;
            cost += lossOf(game, seat, owner, note);
        }
        return cost;
    }

    static double lossOf(Game game, Player seat, Player owner, String note) {
        if (note.endsWith(SUPPORT_FOR_THE_THRONE)) return ThronePoint.kept(game, seat, owner);
        if (note.endsWith(NoteGiving.ALLIANCE)) return AllianceValue.lost(game, seat, owner);
        return OTHER_NOTE_VALUE;
    }

    private static boolean returnsOnActivation(String note) {
        PromissoryNoteModel model = Mapper.getPromissoryNote(note);
        String text = model == null ? null : model.getText();
        return text != null && text.contains(RETURN_TEXT) && text.contains(ACTIVATION_TEXT);
    }
}
