package ti4.ai.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ti4.ai.AiTestGame;
import ti4.ai.eval.Territory.Zone;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.model.Mapper;
import ti4.testUtils.BaseTi4Test;

// Territory is pure hex geometry: whose side of the map a system lies on, with the shortest paths to Mecatol Rex as
// the seat's own lanes. The boards here place only the home systems and Mecatol Rex.
class TerritoryTest extends BaseTi4Test {

    private static final List<String> HOME_POSITIONS = List.of("301", "304", "307", "310", "313", "316");
    private static final List<String> FACTIONS = List.of("nekro", "sol", "sardakk", "hacan", "letnev", "l1z1x");
    private static final List<String> COLORS = List.of("black", "blue", "red", "green", "orange", "purple");

    private AiTestGame test;

    @BeforeEach
    void setUp() {
        test = new AiTestGame();
    }

    private void sixPlayerMap() {
        for (int seat = 0; seat < FACTIONS.size(); seat++) {
            String faction = FACTIONS.get(seat);
            if (!"nekro".equals(faction) && !"sol".equals(faction)) {
                test.addSeat("20000000000000010" + seat, faction, COLORS.get(seat));
            }
            test.place(Mapper.getFaction(faction).getHomeSystem(), HOME_POSITIONS.get(seat));
        }
        test.place("18", "000");
    }

    private Zone zoneAt(String position) {
        return Territory.of(test.game, test.nekro).zone(new Tile("26", position));
    }

    // The seat at 301 sits three steps from Mecatol Rex along 201 and 101. Its neighbours 302 and 318 are closer to it
    // than to anyone else, 202 and 212 are as close to the seat as to a rival, and 203 is closer to the rival at 304.
    @Test
    void sortsTheDefaultSixPlayerMapIntoZonesForTheSeatAt301() {
        sixPlayerMap();

        assertThat(zoneAt("301")).isEqualTo(Zone.HOME);
        assertThat(zoneAt("000")).isEqualTo(Zone.MECATOL);
        assertThat(zoneAt("304")).isEqualTo(Zone.OTHER_HOME);
        assertThat(zoneAt("201")).isEqualTo(Zone.LANE);
        assertThat(zoneAt("101")).isEqualTo(Zone.LANE);
        assertThat(zoneAt("302")).isEqualTo(Zone.SLICE);
        assertThat(zoneAt("318")).isEqualTo(Zone.SLICE);
        assertThat(zoneAt("202")).isEqualTo(Zone.BORDER);
        assertThat(zoneAt("212")).isEqualTo(Zone.BORDER);
        assertThat(zoneAt("203")).isEqualTo(Zone.FOREIGN);
    }

    @Test
    void theOtherSeatsSeeTheirOwnLanes() {
        sixPlayerMap();
        Player sardakk = test.game.getPlayers().values().stream()
                .filter(player -> "sardakk".equals(player.getFaction()))
                .findFirst()
                .orElseThrow();

        Territory sardakksView = Territory.of(test.game, sardakk);

        assertThat(sardakksView.zone(new Tile("26", "307"))).isEqualTo(Zone.HOME);
        assertThat(sardakksView.zone(new Tile("26", "205"))).isEqualTo(Zone.LANE);
        assertThat(sardakksView.zone(new Tile("26", "201"))).isNotEqualTo(Zone.LANE);
    }

    // A test board with a home and a few systems and no rivals' homes: nothing throws, and the systems beside home
    // belong to the seat (there is no Mecatol Rex tile, so none of them is a lane).
    @Test
    void worksOnASparseBoardWithoutMecatolRex() {
        test.nekroHome();
        test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        assertThat(test.game.getMecatolTile()).isNull();

        for (String position : neighboursOfHome()) {
            assertThat(zoneAt(position)).isEqualTo(Zone.SLICE);
        }
        assertThat(zoneAt(AiTestGame.HOME)).isEqualTo(Zone.HOME);
    }

    @Test
    void nothingIsALaneWithoutMecatolRex() {
        sixPlayerMap();
        test.game.removeTile("000");
        assertThat(test.game.getMecatolTile()).isNull();

        for (String position : List.of("201", "101", "302", "202", "203", "102")) {
            assertThat(zoneAt(position)).isNotEqualTo(Zone.LANE).isNotEqualTo(Zone.MECATOL);
        }
    }

    @Test
    void aSeatWithoutAHomeSeesNeutralGround() {
        Player homeless = test.addSeat("200000000000000099", "hacan", "green");
        test.place("26", "302");

        assertThat(Territory.of(test.game, homeless).zone(new Tile("26", "302")))
                .isEqualTo(Zone.NEUTRAL);
    }

    @Test
    void aPositionTheMapperDoesNotKnowIsNeutral() {
        test.nekroHome();

        assertThat(zoneAt("999")).isEqualTo(Zone.NEUTRAL);
    }

    // Territory looks at positions and public homes only.
    @Test
    void readsNoHiddenInformation() {
        sixPlayerMap();
        test.sol.setSecret("dhw");
        test.sol.setActionCard("sabo1");
        test.sol.setPromissoryNote("blue_ta", 17);
        Player spy = Mockito.spy(test.sol);
        test.game.getPlayers().put(spy.getUserID(), spy);

        for (String position : List.of("301", "201", "101", "302", "202", "203", "304")) zoneAt(position);

        verify(spy, never()).getSecretsUnscored();
        verify(spy, never()).getSecrets();
        verify(spy, never()).getActionCards();
        verify(spy, never()).getPromissoryNotes();
    }

    private List<String> neighboursOfHome() {
        return ti4.model.MapPositionMapper.getAdjacentTilePositions(AiTestGame.HOME).stream()
                .filter(position -> !"x".equals(position))
                .toList();
    }
}
