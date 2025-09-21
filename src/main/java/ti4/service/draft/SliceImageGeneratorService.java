package ti4.service.draft;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.ResourceHelper;
import ti4.helpers.Storage;
import ti4.image.DrawingUtil;
import ti4.image.ImageHelper;
import ti4.image.MapGenerator.HorizontalAlign;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.UnitHolder;
import ti4.model.FactionModel;
import ti4.model.MapTemplateModel;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.emoji.TechEmojis;
import ti4.service.image.FileUploadService;
import ti4.service.milty.MiltyDraftSlice;
import ti4.service.milty.MiltyDraftTile;

@UtilityClass
public class SliceImageGeneratorService {

    private static final BasicStroke innerStroke = new BasicStroke(4.0f);
    private static final BasicStroke outlineStroke = new BasicStroke(9.0f);

    public static FileUpload tryGenerateImage(
            DraftManager draftManager, String uniqueKey, List<String> restrictChoiceKeys) {
        SliceDraftable sliceDraftable = (SliceDraftable) draftManager.getDraftableByType(SliceDraftable.TYPE);
        if (sliceDraftable == null) return null;

        Function<String, String> getPlayerFromSlice = (sliceName) -> {
            return draftManager.getPlayersWithChoiceKey(SliceDraftable.TYPE, sliceName).stream()
                    .findFirst()
                    .orElse(null);
        };
        Function<String, FactionModel> getFactionFromPlayer = (playerUserID) -> {
            if (playerUserID == null) return null;
            List<DraftChoice> factionChoices = draftManager.getPlayerPicks(playerUserID, FactionDraftable.TYPE);
            if (!factionChoices.isEmpty()) {
                return FactionDraftable.getFactionByChoice(factionChoices.get(0));
            }
            return null;
        };

        List<MiltyDraftSlice> slices = sliceDraftable.getDraftSlices();
        if (restrictChoiceKeys != null) {
            slices = slices.stream()
                    .filter(slice -> restrictChoiceKeys.contains(slice.getName()))
                    .collect(Collectors.toList());
        }
        if (slices.isEmpty()) return null;

        return generateImage(draftManager.getGame(), slices, getPlayerFromSlice, getFactionFromPlayer, uniqueKey);
    }

    public static FileUpload generateImage(
            Game game,
            List<MiltyDraftSlice> slices,
            Function<String, String> getPlayerFromSlice,
            Function<String, FactionModel> getFactionFromPlayer,
            String uniqueKey) {
        MapTemplateModel mapTemplate = Mapper.getMapTemplate(game.getMapTemplateID());

        int sliceCount = slices.size();
        int spanW = (int) Math.ceil(Math.sqrt(sliceCount));
        int spanH = (sliceCount + spanW - 1) / spanW;

        float scale = 1.0f;
        int scaled = (int) (mapTemplate.squareSliceImageSize() * scale);
        int width = scaled * spanW;
        int height = scaled * spanH;
        BufferedImage mainImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics graphicsMain = mainImage.getGraphics();

        int index = 0;
        int deltaX = 0;
        int deltaY = 0;

        StringBuilder desc = new StringBuilder();
        for (MiltyDraftSlice slice : slices) {
            String playerUserID = getPlayerFromSlice.apply(slice.getName());
            Player playerPicked = game.getPlayer(playerUserID);

            BufferedImage sliceImage;
            if (game.isFowMode()) {
                sliceImage = partialSliceImage(slice, mapTemplate, playerPicked != null);
            } else {
                FactionModel factionModel = getFactionFromPlayer.apply(playerUserID);
                sliceImage = sliceImageWithPlayerInfo(mapTemplate, slice, playerPicked, factionModel);
            }

            BufferedImage resizedSlice = ImageHelper.scale(sliceImage, scale);
            graphicsMain.drawImage(resizedSlice, deltaX, deltaY, null);
            index++;

            int heightSlice = resizedSlice.getHeight();
            int widthSlice = resizedSlice.getWidth();

            deltaX += widthSlice;
            if (index % spanW == 0) {
                deltaY += heightSlice;
                deltaX = 0;
            }

            if (!desc.isEmpty()) desc.append(";\n");
            desc.append(slice.ttsString());
        }

        FileUpload fileUpload = FileUploadService.createFileUpload(mainImage, uniqueKey);
        fileUpload.setDescription(desc.toString());
        return fileUpload;
    }

    private static BufferedImage partialSliceImage(MiltyDraftSlice slice, MapTemplateModel template, boolean taken) {
        List<Point> tilePositions = template.tileDisplayCoords();
        int imageSize = template.squareSliceImageSize();
        Point hs = tilePositions.getFirst();

        List<String> tileStrings = new ArrayList<>();
        tileStrings.add(ResourceHelper.getInstance().getTileFile("00_green.png"));
        tileStrings.addAll(
                slice.getTiles().stream().map(t -> t.getTile().getTilePath()).toList());

        String fow = slice.getTiles().getFirst().getTile().getFowTilePath(null);
        BufferedImage fogFilter = ImageHelper.read(fow);

        BufferedImage sliceImage = new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
        Graphics graphics = sliceImage.getGraphics();

        int index = 0;
        for (String tilePath : tileStrings) {
            BufferedImage img = ImageHelper.read(tilePath);
            Point p = tilePositions.get(index);
            graphics.drawImage(img, p.x, p.y, null);
            if (taken) graphics.drawImage(fogFilter, p.x, p.y, null);
            index++;
        }
        List<TI4Emoji> whs = new ArrayList<>();
        List<TI4Emoji> legendary = new ArrayList<>();
        List<TI4Emoji> blueSkips = new ArrayList<>();
        List<TI4Emoji> greenSkips = new ArrayList<>();
        List<TI4Emoji> yellowSkips = new ArrayList<>();
        List<TI4Emoji> redSkips = new ArrayList<>();
        for (MiltyDraftTile tile : slice.getTiles()) {
            if (tile.isHasAlphaWH()) whs.add(MiscEmojis.WHalpha);
            if (tile.isHasBetaWH()) whs.add(MiscEmojis.WHbeta);
            if (tile.isHasOtherWH()) whs.add(MiscEmojis.WHgamma);
            if (tile.isLegendary()) legendary.add(MiscEmojis.LegendaryPlanet);

            for (UnitHolder uh : tile.getTile().getPlanetUnitHolders()) {
                if (uh instanceof Planet p) {
                    for (String spec : p.getTechSpecialities()) {
                        switch (spec) {
                            case "propulsion" -> blueSkips.add(TechEmojis.PropulsionTech);
                            case "biotic" -> greenSkips.add(TechEmojis.BioticTech);
                            case "cybernetic" -> yellowSkips.add(TechEmojis.CyberneticTech);
                            case "warfare" -> redSkips.add(TechEmojis.WarfareTech);
                        }
                    }
                }
            }
        }
        List<TI4Emoji> featureEmojis = new ArrayList<>();
        featureEmojis.addAll(yellowSkips);
        featureEmojis.addAll(blueSkips);
        featureEmojis.addAll(greenSkips);
        featureEmojis.addAll(redSkips);
        featureEmojis.addAll(whs);
        featureEmojis.addAll(legendary);

        List<Point> featurePoints = Arrays.asList(
                new Point(83, 3), new Point(220, 3),
                new Point(60, 43), new Point(243, 43),
                new Point(37, 83), new Point(266, 83),
                new Point(14, 123), new Point(289, 123));

        int resources = slice.getTotalRes();
        int influence = slice.getTotalInf();
        String totalsString = resources + "/" + influence;

        int resourcesMilty = slice.getOptimalRes();
        int influenceMilty = slice.getOptimalInf();
        int flexMilty = slice.getOptimalFlex();
        String optimalString = "(" + resourcesMilty + "/" + influenceMilty + "+" + flexMilty + ")";

        ((Graphics2D) graphics).setStroke(innerStroke);

        index = 0;
        graphics.setColor(Color.black);
        for (TI4Emoji feature : featureEmojis) {
            Point fPoint = featurePoints.get(index);
            BufferedImage featureImage = getEmojiImage(feature);
            featureImage.getGraphics();
            graphics.setColor(Color.black);
            graphics.fillRoundRect(fPoint.x + hs.x, fPoint.y + hs.y, 40, 40, 40, 40);

            graphics.drawImage(featureImage, fPoint.x + hs.x, fPoint.y + hs.y, null);
            index++;
            if (index >= featurePoints.size()) break;
        }

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont50());
        DrawingUtil.superDrawString(
                graphics, totalsString, hs.x + 172, hs.y + 110, Color.white, hCenter, null, outlineStroke, Color.black);
        DrawingUtil.superDrawString(
                graphics,
                optimalString,
                hs.x + 172,
                hs.y + 165,
                Color.white,
                hCenter,
                null,
                outlineStroke,
                Color.black);

        return sliceImage;
    }

    private static BufferedImage sliceImageWithPlayerInfo(
            MapTemplateModel mapTemplate, MiltyDraftSlice slice, Player player, FactionModel factionModel) {
        List<Point> tilePositions = mapTemplate.tileDisplayCoords();
        Point hs = tilePositions.getFirst();

        BufferedImage sliceImage = partialSliceImage(slice, mapTemplate, player != null);
        Graphics graphics = sliceImage.getGraphics();

        HorizontalAlign hCenter = HorizontalAlign.Center;
        graphics.setColor(Color.white);
        graphics.setFont(Storage.getFont64());
        DrawingUtil.superDrawString(
                graphics,
                slice.getName(),
                hs.x + 172,
                hs.y + 50,
                Color.white,
                hCenter,
                null,
                outlineStroke,
                Color.black);

        if (player != null) {
            graphics.setColor(Color.white);
            graphics.setFont(Storage.getFont50());

            String playerName = player.getUserName();
            DrawingUtil.superDrawString(
                    graphics, playerName, hs.x + 172, hs.y + 230, Color.red, hCenter, null, outlineStroke, Color.black);

            if (factionModel != null) {
                String factionName = factionModel.getFactionName();
                if (factionModel.getAlias().startsWith("keleres")) factionName = "The Council Keleres";
                graphics.setFont(Storage.getFont35());
                DrawingUtil.superDrawString(
                        graphics,
                        factionName,
                        hs.x + 172,
                        hs.y + 270,
                        Color.orange,
                        hCenter,
                        null,
                        outlineStroke,
                        Color.black);
                BufferedImage img = getEmojiImage(factionModel.getFactionEmoji());
                int offset = graphics.getFontMetrics().stringWidth(factionName) / 2 + 10;
                graphics.drawImage(img, hs.x + 172 - offset - 40, hs.y + 240, null);
                graphics.drawImage(img, hs.x + 172 + offset, hs.y + 240, null);
            }
        }

        return sliceImage;
    }

    private static BufferedImage getEmojiImage(TI4Emoji emoji) {
        return ImageHelper.readEmojiImageScaled(emoji, 40);
    }

    private static BufferedImage getEmojiImage(String emojiString) {
        return ImageHelper.readEmojiImageScaled(emojiString, 40);
    }
}
