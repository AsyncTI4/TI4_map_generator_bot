package ti4.image.playerarea;

import java.awt.Point;
import java.util.List;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class SleeperTokens {

    private static final List<Point> POSITIONS =
            List.of(new Point(0, 15), new Point(50, 0), new Point(50, 50), new Point(100, 25), new Point(10, 40));

    private SleeperTokens() {}

    static int draw(PlayerAreaContext ctx, AreaSlot slot, Player player, int xDeltaFromRight, int y) {
        if (!player.hasAbility("awaken") || player.hasTech("tf-awaken")) {
            return xDeltaFromRight;
        }
        String sleeperFile = ImageResources.token(Constants.TOKEN_SLEEPER_PNG);
        int remaining = POSITIONS.size() - ctx.game().getSleeperTokensPlacedCount();
        return TokenSupply.drawRemaining(
                ctx, slot, POSITIONS, ImageHelper.read(sleeperFile), remaining, xDeltaFromRight, y);
    }
}
