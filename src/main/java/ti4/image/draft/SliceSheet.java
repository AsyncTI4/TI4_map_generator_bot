package ti4.image.draft;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.io.RemoteImages;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.service.milty.MiltyDraftSlice;

public final class SliceSheet {

    private static final float SCALE = 1.0f;
    private static final int EMOJI_SIZE = 40;
    private static final int PLAYER_NAME_Y = 230;
    private static final int FACTION_NAME_Y = 270;
    private static final int FACTION_EMOJI_Y = 240;

    private SliceSheet() {}

    public record Card(
            MiltyDraftSlice slice,
            boolean taken,
            boolean revealed,
            @Nullable String playerName,
            @Nullable FactionModel faction) {

        public static Card hidden(MiltyDraftSlice slice, boolean taken) {
            return new Card(slice, taken, false, null, null);
        }

        public static Card revealed(
                MiltyDraftSlice slice, boolean taken, @Nullable String playerName, @Nullable FactionModel faction) {
            return new Card(slice, taken, true, playerName, faction);
        }
    }

    public static BufferedImage render(MapTemplateModel template, List<Card> cards) {
        int cardCount = cards.size();
        int columns = (int) Math.ceil(Math.sqrt(cardCount));
        int rows = (cardCount + columns - 1) / columns;
        int cardSize = (int) (template.squareSliceImageSize() * SCALE);
        BufferedImage sheet = new BufferedImage(cardSize * columns, cardSize * rows, BufferedImage.TYPE_INT_ARGB);
        Graphics2D sheetGraphics = sheet.createGraphics();

        int index = 0;
        int deltaX = 0;
        int deltaY = 0;
        for (Card card : cards) {
            BufferedImage resizedCard = ImageHelper.scale(cardImage(template, card), SCALE);
            sheetGraphics.drawImage(resizedCard, deltaX, deltaY, null);
            index++;

            deltaX += resizedCard.getWidth();
            if (index % columns == 0) {
                deltaY += resizedCard.getHeight();
                deltaX = 0;
            }
        }
        return sheet;
    }

    private static BufferedImage cardImage(MapTemplateModel template, Card card) {
        BufferedImage image = sliceImage(card.slice(), template, card.taken());
        if (card.revealed()) {
            drawIdentity(image, template.tileDisplayCoords().getFirst(), card);
        }
        return image;
    }

    private static BufferedImage sliceImage(MiltyDraftSlice slice, MapTemplateModel template, boolean taken) {
        List<Point> tilePositions = template.tileDisplayCoords();
        Point home = tilePositions.getFirst();
        int imageSize = template.squareSliceImageSize();

        List<String> tilePaths = new ArrayList<>();
        tilePaths.add(ImageResources.tile("00_green.png"));
        tilePaths.addAll(slice.getTiles().stream()
                .map(tile -> ImageResources.tilePath(tile.getTile()))
                .toList());

        String fogPath = ImageResources.fogTilePath(slice.getTiles().getFirst().getTile(), null);
        BufferedImage fogFilter = ImageHelper.read(fogPath);

        BufferedImage image = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        int index = 0;
        for (String tilePath : tilePaths) {
            Point point = tilePositions.get(index);
            graphics.drawImage(ImageHelper.read(tilePath), point.x, point.y, null);
            if (taken) graphics.drawImage(fogFilter, point.x, point.y, null);
            index++;
        }
        SliceStats.drawFeatures(graphics, slice, home);
        SliceStats.drawTotals(graphics, slice, home);
        return image;
    }

    private static void drawIdentity(BufferedImage image, Point home, Card card) {
        Graphics2D graphics = image.createGraphics();
        SliceStats.drawName(graphics, card.slice().getName(), home);
        if (!card.taken()) return;

        graphics.setColor(Color.white);
        graphics.setFont(Fonts.ti(50));
        SliceStats.drawCenteredLabel(graphics, card.playerName(), Color.red, home, PLAYER_NAME_Y);

        FactionModel faction = card.faction();
        if (faction == null) return;
        String factionName =
                faction.getAlias().startsWith("keleres") ? "The Council Keleres" : faction.getFactionName();
        graphics.setFont(Fonts.ti(35));
        SliceStats.drawCenteredLabel(graphics, factionName, Color.orange, home, FACTION_NAME_Y);
        BufferedImage emoji = RemoteImages.emoji(faction.getFactionEmoji(), EMOJI_SIZE);
        int offset = graphics.getFontMetrics().stringWidth(factionName) / 2 + 10;
        graphics.drawImage(emoji, home.x + 172 - offset - EMOJI_SIZE, home.y + FACTION_EMOJI_Y, null);
        graphics.drawImage(emoji, home.x + 172 + offset, home.y + FACTION_EMOJI_Y, null);
    }
}
