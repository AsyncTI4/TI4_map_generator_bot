package ti4.discord.interactions.commands.help;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.image.draw.ImageGrid;
import ti4.image.draw.StarfieldPage;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.io.ImageUploads;
import ti4.logging.BotLogger;
import ti4.logging.LogOrigin;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.model.Mapper;

class SampleColors extends Subcommand {

    private static final String MULTI = "MULTI";
    private static final List<String> ALL_HUES =
            List.of("RED", "ORANGE", "BROWN", "YELLOW", "GREEN", "BLUE", "PURPLE", "PINK", "WHITE", "BLACK", MULTI);
    private static final int DREAD_IMAGE_WIDTH = 77;
    private static final int DREAD_IMAGE_HEIGHT = 76;

    private record Metrics(
            int spacing, int labelRoom, int lineHeight, Font titleFont, Font detailFont, BasicStroke stroke) {

        static Metrics overview() {
            return new Metrics(2, 23, 12, Fonts.ti(12), Fonts.ti(8), new BasicStroke(2.0f));
        }

        static Metrics closeUp() {
            return new Metrics(12, 32, 20, Fonts.ti(16), Fonts.ti(12), new BasicStroke(3.0f));
        }

        int cellWidth() {
            return DREAD_IMAGE_WIDTH + 2 * spacing;
        }

        int cellHeight() {
            return DREAD_IMAGE_HEIGHT + labelRoom + 2 * spacing;
        }

        ImageGrid.LabelStyle labels() {
            return new ImageGrid.LabelStyle(
                    titleFont, detailFont, stroke, cellWidth(), DREAD_IMAGE_HEIGHT + spacing, lineHeight);
        }
    }

    SampleColors() {
        super(Constants.SAMPLE_COLORS, "Show a sample image of dreadnoughts in various player colors.");
        addOptions(new OptionData(OptionType.STRING, Constants.HUE, "General hue of colors to show (default: all)")
                .setAutoComplete(true));
    }

    private static boolean isInCategory(ColorModel color, String category) {
        if (MULTI.equals(category)) return color.isMulti();
        return !color.isMulti() && color.getColorCategories().contains(category);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping input = event.getOption(Constants.HUE);
        boolean allHues = input == null
                || Constants.ALL.equals(input.getAsString())
                || input.getAsString().isEmpty();
        List<String> hues = allHues ? ALL_HUES : List.of(input.getAsString());
        Metrics metrics = allHues ? Metrics.overview() : Metrics.closeUp();
        int fewer = allHues || MULTI.equals(input.getAsString()) ? 1 : 0;

        int maxColorCount = 0;
        for (String hue : hues) {
            maxColorCount = Math.max(maxColorCount, (int) Mapper.getColors().stream()
                    .filter(color -> isInCategory(color, hue))
                    .count());
        }
        int maxHeight = (2 * hues.size() - fewer) * metrics.cellHeight();
        StarfieldPage page = new StarfieldPage(maxColorCount * metrics.cellWidth(), maxHeight);
        int left = page.left();
        int top = page.top();
        int right = drawHues(page.graphics(), hues, metrics, left, top);
        if (left == right) {
            MessageHelper.sendMessageToEventChannel(event, "No colours found. Something has probably gone wrong.");
            return;
        }

        BufferedImage coloursImage = page.crop(right, top + maxHeight);
        String fileNamePrefix =
                "colour_sample_" + top + "_" + left + "_" + (hues.size() == 1 ? hues.getFirst() : Constants.ALL);
        try (var fileUpload = ImageUploads.createFileUpload(coloursImage, fileNamePrefix)) {
            fileUpload.setDescription(
                    "Colour samples for " + (hues.size() == 1 ? "all the " + hues.getFirst() : "ALL the") + " units.");
            MessageHelper.sendFileUploadToChannel(event.getChannel(), fileUpload);
        } catch (IOException e) {
            BotLogger.error(new LogOrigin(event), "Exception when closing FileUpload.", e);
        }
    }

    private static int drawHues(Graphics2D graphic, List<String> hues, Metrics metrics, int left, int top) {
        int right = left;
        int y = top;
        for (String hue : hues) {
            boolean multi = MULTI.equals(hue);
            int x = left;
            for (ColorModel color : Mapper.getColors()) {
                if (!isInCategory(color, hue)) continue;
                x = drawColor(graphic, metrics, color, x, y, multi);
            }
            right = Math.max(right, x);
            y += (multi ? 1 : 2) * metrics.cellHeight();
        }
        return right;
    }

    private static int drawColor(Graphics2D graphic, Metrics metrics, ColorModel color, int x, int y, boolean multi) {
        String alias = color.getAlias();
        drawDreadnought(graphic, metrics, ImageResources.unit(alias + "_dn.png"), x, y);
        ImageGrid.drawLabels(graphic, metrics.labels(), x, y, List.of(color.getName()), List.of(alias));

        String splitName = "lgy".equals(alias) ? "orca" : "split" + alias;
        String splitFile = ImageResources.unit(splitName + "_dn.png");
        if (splitFile != null) {
            int splitX = x + (multi ? metrics.cellWidth() : 0);
            int splitY = y + (multi ? 0 : metrics.cellHeight());
            drawDreadnought(graphic, metrics, splitFile, splitX, splitY);
            String splitTitle = "lgy".equals(alias) ? "orca" : "split" + color.getName();
            ImageGrid.drawLabels(graphic, metrics.labels(), splitX, splitY, List.of(splitTitle), List.of(splitName));
            x += multi ? metrics.cellWidth() : 0;
        }
        return x + metrics.cellWidth();
    }

    private static void drawDreadnought(Graphics2D graphic, Metrics metrics, String file, int x, int y) {
        graphic.drawImage(ImageHelper.read(file), x + metrics.spacing(), y + metrics.spacing(), null);
    }
}
