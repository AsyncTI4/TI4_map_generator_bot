package ti4.image.card;

import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.StringHelper;
import ti4.image.io.ImageUploads;
import ti4.message.MessageHelper;

public final class BannerGenerator {

    private BannerGenerator() {}

    public static void drawFactionBanner(Player player) {
        String turn = StringHelper.ordinal(player.getInRoundTurnCount());
        FileUpload upload = ImageUploads.createFileUpload(
                        BannerImages.faction(player), player.getFaction() + player.getColor() + "banner")
                .setDescription(player.getFlexibleDisplayName() + "'s " + turn + " turn");
        MessageHelper.sendFileUploadToChannel(player.getCorrectChannel(), upload);
    }

    public static void drawAgendaBanner(int number, Game game) {
        FileUpload upload = ImageUploads.createFileUpload(BannerImages.agenda(number), "agenda" + number + "banner");
        MessageHelper.sendFileUploadToChannel(game.getActionsChannel(), upload);
    }

    public static void drawPhaseBanner(String phase, int round, TextChannel channel) {
        drawPhaseBanner(phase, round, channel, null);
    }

    public static void drawPhaseBanner(String phase, int round, TextChannel channel, String altPhaseName) {
        String label = altPhaseName == null ? phase : altPhaseName;
        FileUpload upload = ImageUploads.createFileUpload(
                        BannerImages.phase(phase, round, label), phase + round + "banner")
                .setDescription("Start of " + label + " phase, round " + round + ".");
        MessageHelper.sendFileUploadToChannel(channel, upload);
    }
}
