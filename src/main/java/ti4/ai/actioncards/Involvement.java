package ti4.ai.actioncards;

import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.ai.brain.AiTurnContext;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.FoWHelper;

@UtilityClass
class Involvement {

    static final double AIMED_AT_SEAT = 1.0;
    static final double AGENDA_PHASE = 0.6;
    static final double OTHERWISE = 0.35;
    private static final String PUBLIC_DISGRACE = "Public Disgrace";
    private static final Set<String> TARGETS_THE_ACTIVE_PLAYER = Set.of("Coup d'Etat", "Extreme Duress", "Crisis");
    private static final Set<String> COMBAT_CARDS = Set.of(
            "Parley",
            "Rout",
            "Morale Boost",
            "Shields Holding",
            "Direct Hit",
            "Skilled Retreat",
            "Experimental Battlestation",
            "Ghost Squad",
            "Reflective Shielding",
            "Waylay",
            "Intercept",
            "Blitz",
            "Bunker",
            "Disable",
            "Scramble Frequency",
            "Fire Team",
            "Fighter Prototype",
            "Emergency Repairs",
            "Reveal Prototype",
            "Courageous to the End",
            "Maneuvering Jets",
            "In The Silence Of Space",
            "Solar Flare",
            "Salvage",
            "Crash Landing");

    static double level(AiTurnContext context, String title, Player cardPlayer) {
        if (aimedAtSeat(context, title, cardPlayer)) return AIMED_AT_SEAT;
        return context.game().getPhaseOfGame().toLowerCase().contains("agenda") ? AGENDA_PHASE : OTHERWISE;
    }

    static boolean isCombatCard(String title) {
        return COMBAT_CARDS.contains(title);
    }

    static boolean aimedAtSeat(AiTurnContext context, String title, Player cardPlayer) {
        Game game = context.game();
        Player seat = context.seat();
        if (cardPlayer == null || cardPlayer == seat) return false;
        if (TARGETS_THE_ACTIVE_PLAYER.contains(title)) return context.isActivePlayer();
        if (PUBLIC_DISGRACE.equals(title)) {
            return context.isActivePlayer() && "strategy".equalsIgnoreCase(game.getPhaseOfGame());
        }
        return COMBAT_CARDS.contains(title) && sharesTheActiveSystem(game, seat, cardPlayer);
    }

    private static boolean sharesTheActiveSystem(Game game, Player seat, Player cardPlayer) {
        if (game.getActiveSystem().isBlank()) return false;
        Tile tile = game.getTileByPosition(game.getActiveSystem());
        return tile != null
                && FoWHelper.playerHasUnitsInSystem(seat, tile)
                && FoWHelper.playerHasUnitsInSystem(cardPlayer, tile);
    }
}
