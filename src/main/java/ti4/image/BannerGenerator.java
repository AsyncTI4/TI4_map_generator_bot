package ti4.image;

import java.awt.*;
import java.awt.image.BufferedImage;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.ResourceHelper;
import ti4.helpers.Storage;
import ti4.helpers.StringHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.image.FileUploadService;

@UtilityClass
public class BannerGenerator {

    private static final BasicStroke stroke2 = new BasicStroke(2.0f);
    private static final BasicStroke stroke6 = new BasicStroke(6.0f);
    private static final BasicStroke stroke8 = new BasicStroke(8.0f);

    public static void drawFactionBanner(Player player) {
        BufferedImage bannerImage = new BufferedImage(325, 50, BufferedImage.TYPE_INT_ARGB);
        BufferedImage backgroundImage = ImageHelper.readScaled(ResourceHelper.getInstance().getExtraFile("factionbanner_background.png"), 325, 50);
        String pnColorFile = "pa_pn_color_" + Mapper.getColorID(player.getColor()) + ".png";
        BufferedImage colorImage = ImageHelper.readScaled(ResourceHelper.getInstance().getPAResource(pnColorFile), 1.5f);
        BufferedImage gradientImage = ImageHelper.read(ResourceHelper.getInstance().getExtraFile("factionbanner_gradient.png"));
        BufferedImage smallFactionImage = DrawingUtil.getPlayerFactionIconImageScaled(player, 0.26f);
        BufferedImage largeFactionImage = DrawingUtil.getPlayerFactionIconImageScaled(player, 1.4f);
        Graphics bannerG = bannerImage.getGraphics();

        bannerG.drawImage(backgroundImage, 0, 0, null);
        Graphics2D bannerG2d = (Graphics2D) bannerG;
        bannerG2d.rotate(Math.toRadians(-90));
        bannerG2d.drawImage(colorImage, -60, 0, null);
        bannerG2d.rotate(Math.toRadians(90));
        bannerG2d.drawImage(gradientImage, 0, 0, null);
        bannerG2d.drawImage(smallFactionImage, 2, 24, null);
        bannerG.drawImage(largeFactionImage, 180, -42, null);
        bannerG.setFont(Storage.getFont16());
        bannerG.setColor(Color.WHITE);

        String name = player.bannerName();
        DrawingUtil.superDrawString(bannerG, name, 29, 44, Color.WHITE, MapGenerator.HorizontalAlign.Left, MapGenerator.VerticalAlign.Bottom, stroke2, Color.BLACK);
        int mod = 0;
        if (player.getInitiative() > 9) {
            mod = 13;
        }
        DrawingUtil.superDrawString(bannerG, "#" + player.getInitiative(), 300 - mod, 44, Color.WHITE, MapGenerator.HorizontalAlign.Left, MapGenerator.VerticalAlign.Bottom, stroke2, Color.BLACK);

        String turnOrdinal = StringHelper.ordinal(player.getInRoundTurnCount());
        String descr = player.getFlexibleDisplayName() + "'s " + turnOrdinal + " turn";
        FileUpload fileUpload = FileUploadService.createFileUpload(bannerImage, player.getFaction() + player.getColor() + "banner").setDescription(descr);
        MessageHelper.sendFileUploadToChannel(player.getCorrectChannel(), fileUpload);
    }

    public static void drawAgendaBanner(int num, Game game) {
        Graphics bannerG;
        BufferedImage bannerImage = new BufferedImage(225, 50, BufferedImage.TYPE_INT_ARGB);
        BufferedImage backgroundImage = ImageHelper.readScaled(ResourceHelper.getInstance().getExtraFile("factionbanner_background.png"), 325, 50);
        BufferedImage agendaImage = ImageHelper.readScaled(ResourceHelper.getInstance().getExtraFile("agenda.png"), 50, 50);
        String pnColorFile = "pa_pn_color_" + Mapper.getColorID("blue") + ".png";
        BufferedImage colorImage = ImageHelper.readScaled(ResourceHelper.getInstance().getPAResource(pnColorFile), 1.5f);
        BufferedImage gradientImage = ImageHelper.read(ResourceHelper.getInstance().getExtraFile("factionbanner_gradient.png"));
        bannerG = bannerImage.getGraphics();

        bannerG.drawImage(backgroundImage, 0, 0, null);

        Graphics2D bannerG2d = (Graphics2D) bannerG;
        bannerG2d.rotate(Math.toRadians(-90));
        bannerG2d.drawImage(colorImage, -60, 0, null);
        bannerG2d.rotate(Math.toRadians(90));
        bannerG2d.drawImage(gradientImage, 0, 0, null);
        bannerG.drawImage(agendaImage, 0, 0, null);
        bannerG.setFont(Storage.getFont28());
        bannerG.setColor(Color.WHITE);

        DrawingUtil.superDrawString(bannerG, "Agenda #" + num, 55, 35, Color.WHITE, MapGenerator.HorizontalAlign.Left, MapGenerator.VerticalAlign.Bottom, stroke2, Color.BLACK);

        FileUpload fileUpload = FileUploadService.createFileUpload(bannerImage, "agenda" + num + "banner");
        MessageHelper.sendFileUploadToChannel(game.getActionsChannel(), fileUpload);
    }

    public static void drawPhaseBanner(String phase, int round, TextChannel channel) {
        drawPhaseBanner(phase, round, channel, null);
    }

    public static void drawPhaseBanner(String phase, int round, TextChannel channel, String altPhaseName) {
        if (altPhaseName == null) {
            altPhaseName = phase;
        }
        BufferedImage bannerImage = new BufferedImage(511, 331, BufferedImage.TYPE_INT_ARGB);
        BufferedImage backgroundImage = ImageHelper.readScaled(ResourceHelper.getInstance().getExtraFile(phase + "banner.png"), 511, 331);

        Graphics bannerG = bannerImage.getGraphics();
        bannerG.drawImage(backgroundImage, 0, 0, null);
        bannerG.setFont(Storage.getFont48());
        bannerG.setColor(Color.WHITE);
        DrawingUtil.superDrawString(bannerG, altPhaseName.toUpperCase() + " PHASE", 255, 110, Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Center, stroke8, Color.BLACK);
        bannerG.setFont(Storage.getFont32());

        String roundText = "ROUND " + StringHelper.numberToWords(round).toUpperCase();
        DrawingUtil.superDrawString(bannerG, roundText, 255, 221, Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Center, stroke6, Color.BLACK);

        String descr = "Start of " + altPhaseName + " phase, round " + round + ".";
        FileUpload fileUpload = FileUploadService.createFileUpload(bannerImage, phase + round + "banner").setDescription(descr);
        MessageHelper.sendFileUploadToChannel(channel, fileUpload);
    }
}
