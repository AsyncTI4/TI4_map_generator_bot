package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class PlayerKolumeBreakthroughTest extends BaseTi4Test {

    private static final String KOLUME_BREAKTHROUGH = "kolumebt";
    private static final String TECH = "sr";

    private Game game;
    private Player kolume;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("kolume-exhaust-test");
        kolume = new Player("kolume-user", "Kolume", game);
        kolume.setColor("red");
        kolume.setCommoditiesBase(5);
        kolume.addTech(TECH);
    }

    private void unlockKolumeBreakthrough() {
        kolume.addBreakthrough(KOLUME_BREAKTHROUGH);
        kolume.setBreakthroughUnlocked(KOLUME_BREAKTHROUGH, true);
    }

    @Test
    void exhaustingOwnedTechGainsOneCommodityWithKolumeUnlocked() {
        unlockKolumeBreakthrough();
        kolume.setCommodities(0);

        kolume.exhaustTech(TECH);

        assertThat(kolume.getExhaustedTechs()).contains(TECH);
        assertThat(kolume.getCommodities()).isEqualTo(1);
    }

    // The passive keeps working after the breakthrough's exhaust ability has been used.
    @Test
    void exhaustedKolumeBreakthroughStillGainsCommodity() {
        unlockKolumeBreakthrough();
        kolume.setBreakthroughExhausted(KOLUME_BREAKTHROUGH, true);
        kolume.setCommodities(0);

        kolume.exhaustTech(TECH);

        assertThat(kolume.getCommodities()).isEqualTo(1);
    }

    @Test
    void noCommodityWithoutUnlockedKolumeBreakthrough() {
        kolume.addBreakthrough(KOLUME_BREAKTHROUGH);
        kolume.setCommodities(0);

        kolume.exhaustTech(TECH);

        assertThat(kolume.getExhaustedTechs()).contains(TECH);
        assertThat(kolume.getCommodities()).isZero();
    }

    @Test
    void noCommodityWhenTechIsNotOwned() {
        unlockKolumeBreakthrough();
        kolume.setCommodities(0);

        kolume.exhaustTech("nf");

        assertThat(kolume.getExhaustedTechs()).doesNotContain("nf");
        assertThat(kolume.getCommodities()).isZero();
    }

    @Test
    void noCommodityWhenTechIsAlreadyExhausted() {
        unlockKolumeBreakthrough();
        kolume.getExhaustedTechs().add(TECH);
        kolume.setCommodities(0);

        kolume.exhaustTech(TECH);

        assertThat(kolume.getCommodities()).isZero();
    }
}
