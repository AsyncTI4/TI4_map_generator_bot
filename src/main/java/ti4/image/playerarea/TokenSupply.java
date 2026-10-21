package ti4.image.playerarea;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;

final class TokenSupply {

    private TokenSupply() {}

    static List<Point> zigzag(int count, int stepX, int dropY) {
        List<Point> points = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            points.add(new Point(i * stepX, dropY * ((i + 1) % 2)));
        }
        return points;
    }

    static int drawRemaining(
            PlayerAreaContext ctx,
            AreaSlot slot,
            List<Point> points,
            BufferedImage image,
            int tokensRemaining,
            int xDeltaFromRight,
            int y) {
        if (image == null) {
            return xDeltaFromRight;
        }
        int maxOffset = 0;
        for (int i = 0; i < tokensRemaining; i++) maxOffset = Math.max(maxOffset, points.get(i).x);

        xDeltaFromRight += maxOffset + image.getWidth() + 5;

        for (int i = 0; i < tokensRemaining; i++) {
            Point point = points.get(i);
            ctx.g2().drawImage(image, slot.rightEdge() - xDeltaFromRight + point.x, y + point.y, null);
        }
        return xDeltaFromRight;
    }

    static int drawWideZigzag(
            PlayerAreaContext ctx,
            AreaSlot slot,
            String tokenName,
            int maxTokens,
            int tokensRemaining,
            int xDeltaFromRight,
            int y) {
        return drawRemaining(
                ctx,
                slot,
                zigzag(maxTokens, 54, 50),
                ImageHelper.read(ImageResources.token(tokenName)),
                tokensRemaining,
                xDeltaFromRight,
                y);
    }
}
