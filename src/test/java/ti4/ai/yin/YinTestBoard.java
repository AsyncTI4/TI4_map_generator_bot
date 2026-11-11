package ti4.ai.yin;

import java.util.Set;
import ti4.ai.AiTestGame;
import ti4.ai.brain.AiTurnContext;
import ti4.ai.perception.AiPrompt;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;

// A Yin AI seat (green) beside the test game's Nekro home, fighting the human Sol seat (blue) in the system next to it
// (Lodor, tile 26). The Yin home (Darien, 4/4, tile 03) lies on the far side of Lodor; Darien pays for its abilities.
final class YinTestBoard {

    static final String PLANET = "lodor";
    static final String SPACE = "space";

    final AiTestGame test = new AiTestGame();
    final Player yin;
    final Player sol;
    final Tile tile;
    final String position;
    final Tile home;

    YinTestBoard() {
        test.game.setVp(10);
        yin = test.addSeat(AiTestGame.SECOND_AI_ID, "yin", "green");
        sol = test.sol;
        position = AiTestGame.neighbourOf(AiTestGame.HOME);
        tile = test.place("26", position);
        home = test.place("03", homePosition(position));
        yin.addPlanet("darien");
        test.isActive(yin, "action");
        test.game.setActiveSystem(position);
    }

    private static String homePosition(String position) {
        return MapPositionMapper.getAdjacentTilePositions(position).stream()
                .filter(candidate -> !"x".equals(candidate) && !AiTestGame.HOME.equals(candidate))
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    void units(String holder, Player player, UnitType type, int count) {
        test.units(tile, holder, player, type, count);
    }

    void rounds(Player player, String holder, int rounds) {
        test.game.setStoredValue(
                "combatRoundTracker" + player.getFaction() + position + holder, String.valueOf(rounds));
    }

    void unlockCommander() {
        yin.unsafeGetLeader(BrotherOmar.COMMANDER).setLocked(false);
    }

    AiTurnContext context(long now, AiPrompt... prompts) {
        return test.contextFor(yin, Set.of(), now, prompts);
    }
}
