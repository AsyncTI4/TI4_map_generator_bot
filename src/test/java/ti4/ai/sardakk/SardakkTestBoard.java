package ti4.ai.sardakk;

import ti4.ai.AiTestGame;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.model.MapPositionMapper;
import ti4.model.Mapper;

// The AI seat plays Sardakk's commander (the seat is the test game's "nekro" seat, which has no faction-specific
// rules in the way). Layout: the home system, a held two-planet system beside it (Starpoint and New Albion, the
// "source") and a third system beside the source that is not the home system (Lodor, the "target").
final class SardakkTestBoard {

    static final String FIRST_SOURCE_PLANET = "newalbion";
    static final String SECOND_SOURCE_PLANET = "starpoint";

    final AiTestGame test = new AiTestGame();
    final Tile home;
    final Tile source;
    final Tile target;

    SardakkTestBoard() {
        test.game.setRound(3);
        home = test.nekroHome();
        String sourcePosition = AiTestGame.neighbourOf(AiTestGame.HOME);
        source = test.place("27", sourcePosition);
        target = test.place("26", targetPosition(sourcePosition));
        test.nekro.addPlanet(FIRST_SOURCE_PLANET);
        test.nekro.addPlanet(SECOND_SOURCE_PLANET);
    }

    void unlockCommander() {
        test.nekro.addLeader("sardakkcommander");
        test.nekro.unsafeGetLeader("sardakkcommander").setLocked(false);
    }

    void infantryOnEachSourcePlanet(int count) {
        test.units(source, FIRST_SOURCE_PLANET, test.nekro, UnitType.Infantry, count);
        test.units(source, SECOND_SOURCE_PLANET, test.nekro, UnitType.Infantry, count);
    }

    void tokenOnSource() {
        source.addCC(Mapper.getCCID(test.nekro.getColor()));
    }

    String sourcePosition() {
        return source.getPosition();
    }

    String targetPosition() {
        return target.getPosition();
    }

    private static String targetPosition(String sourcePosition) {
        return MapPositionMapper.getAdjacentTilePositions(sourcePosition).stream()
                .filter(position -> !"x".equals(position) && !AiTestGame.HOME.equals(position))
                .findFirst()
                .orElseThrow();
    }
}
