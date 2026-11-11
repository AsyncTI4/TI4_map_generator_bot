package ti4.image.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.service.megagame.MegagameGroupServiceTestFixtures;
import ti4.testUtils.BaseTi4Test;

class MegagameGroupsImageTest extends BaseTi4Test {

    @Test
    void rendersOneColumnPerGroupAndOneRowPerMember() {
        Game game = MegagameGroupServiceTestFixtures.threeGroupsHoldingCards();

        BufferedImage image = MegagameGroupsImage.render(game);

        // 3 groups x 260 wide, 70 title + 130 header + 2 rows x 190 high
        assertThat(image.getWidth()).isEqualTo(780);
        assertThat(image.getHeight()).isEqualTo(580);
    }

    @Test
    void titleNamesRoundAndPhaseInWordsBecauseTheMapFontHasNoGreekLetters() {
        Game game = MegagameGroupServiceTestFixtures.threeGroupsHoldingCards();
        game.setRound(3);
        game.setPhaseOfGame("action");

        assertThat(MegagameGroupsView.of(game).title()).isEqualTo("Round 3  ·  Action phase");
    }
}
