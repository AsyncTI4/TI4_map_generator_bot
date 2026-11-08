package ti4.image.map;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.service.megagame.MegagameGroupServiceTestFixtures;
import ti4.testUtils.BaseTi4Test;

class MegagameGroupsImageTest extends BaseTi4Test {

    @Test
    void rendersOneSpeakerAndOneFactionColumnPerGroupAndOneRowPerMember() {
        Game game = MegagameGroupServiceTestFixtures.threeGroupsHoldingCards();

        BufferedImage image = MegagameGroupsImage.render(game);

        // 3 groups x (170 speaker + 200 faction) wide, 120 header + 2 rows x 170 high
        assertThat(image.getWidth()).isEqualTo(1110);
        assertThat(image.getHeight()).isEqualTo(460);
    }
}
