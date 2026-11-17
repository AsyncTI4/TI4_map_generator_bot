package ti4.image.draw;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import ti4.helpers.Constants;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.model.Mapper;
import ti4.service.megagame.MegagameGroupService;

public final class MegagameGroupTokens {

    private static final Map<String, BufferedImage> SPEAKER_TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, BufferedImage> PLAIN_SPEAKER_TOKENS = new ConcurrentHashMap<>();

    private MegagameGroupTokens() {}

    @Nullable
    public static BufferedImage speakerToken(String group) {
        return SPEAKER_TOKENS.computeIfAbsent(group, key -> tintedSpeakerToken(key, true));
    }

    @Nullable
    public static BufferedImage plainSpeakerToken(String group) {
        return PLAIN_SPEAKER_TOKENS.computeIfAbsent(group, key -> tintedSpeakerToken(key, false));
    }

    public static void drawBadge(Graphics2D g2, String group, int x, int y) {
        g2.setFont(Fonts.ti(40));
        OutlinedText.draw(
                g2,
                group,
                x,
                y,
                MegagameGroupService.colour(group),
                HorizontalAlign.Left,
                VerticalAlign.Top,
                Strokes.STROKE_4,
                Color.BLACK);
    }

    @Nullable
    private static BufferedImage tintedSpeakerToken(String group, boolean withLetter) {
        BufferedImage base = ImageHelper.read(ImageResources.token(Mapper.getTokenID(Constants.SPEAKER)));
        if (base == null) {
            return null;
        }
        BufferedImage token = new BufferedImage(base.getWidth(), base.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = token.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(base, 0, 0, null);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.45f));
        g2.setColor(MegagameGroupService.colour(group));
        g2.fillRect(0, 0, base.getWidth(), base.getHeight());
        g2.setComposite(AlphaComposite.SrcOver);
        if (!withLetter) {
            g2.dispose();
            return token;
        }
        g2.setFont(Fonts.ti(Math.max(16, base.getHeight() / 2)));
        OutlinedText.draw(
                g2,
                group,
                base.getWidth() - 4,
                base.getHeight() / 2,
                Color.WHITE,
                HorizontalAlign.Right,
                VerticalAlign.Center,
                Strokes.STROKE_4,
                Color.BLACK);
        g2.dispose();
        return token;
    }
}
