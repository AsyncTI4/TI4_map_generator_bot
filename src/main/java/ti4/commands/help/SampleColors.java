package ti4.commands.help;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.ResourceHelper;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.image.DrawingUtil;
import ti4.image.ImageHelper;
import ti4.image.MapGenerator;
import ti4.image.Mapper;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.service.image.FileUploadService;

class SampleColors extends Subcommand {

    public SampleColors() {
        super(Constants.SAMPLE_COLORS, "Show a sample image of dreadnoughts in various player colors.");
        addOptions(new OptionData(OptionType.STRING, Constants.HUE, "General hue of colors to show (default: all)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int SPACING = 2;
        int DREADWIDTH = 77 + 2 * SPACING;
        int DREADSUBHIGHT = 76;
        int DREADTEXHIGHT = DREADSUBHIGHT + 23 + 2 * SPACING;
        int LINEHEIGHT = 12;
        Font bigFont = Storage.getFont12();
        Font smallFont = Storage.getFont8();
        int PAGEWIDTH = 1600;
        int PAGEHIGHT = 2400;
        BasicStroke stroke = new BasicStroke(2.0f);

        OptionMapping input = event.getOption(Constants.HUE);
        List<String> hues = new ArrayList<>();
        if (input == null || input.getAsString().equals(Constants.ALL) || input.getAsString().isEmpty()) {
            hues = Arrays.asList("RED", "GRAY", "ORANGE", "YELLOW", "GREEN", "BLUE", "PURPLE", "PINK", "MULTI1", "MULTI2");
        } else {
            SPACING = 12;
            DREADWIDTH = 77 + 2 * SPACING;
            DREADSUBHIGHT = 76;
            DREADTEXHIGHT = DREADSUBHIGHT + 32 + 2 * SPACING;
            LINEHEIGHT = 20;
            bigFont = Storage.getFont16();
            smallFont = Storage.getFont12();
            if (input.getAsString().equals("MULTI"))
            {
                hues = Arrays.asList("MULTI1", "MULTI2");
            }
            else
            {
                hues.add(input.getAsString());
            }
            stroke = new BasicStroke(3.0f);
        }

        int left = ThreadLocalRandom.current().nextInt(PAGEWIDTH - 6 * DREADWIDTH);
        int top = ThreadLocalRandom.current().nextInt(PAGEHIGHT - 2 * hues.size() * DREADTEXHIGHT);
        int right = left;
        int bottom = top + 2 * hues.size() * DREADTEXHIGHT;
        int x;
        int y = top;

        BufferedImage coloursImage = new BufferedImage(PAGEWIDTH, PAGEHIGHT, BufferedImage.TYPE_INT_ARGB);
        BufferedImage backgroundImage = ImageHelper.read(ResourceHelper.getInstance().getExtraFile("starfield.png"));
        Graphics graphic = coloursImage.getGraphics();
        graphic.drawImage(backgroundImage, 0, 0, null);

        for (String h : hues) {
            x = left;
            for (ColorModel c : Mapper.getColors()) {
                if (!c.getHue().equals(h)) {
                    continue;
                }
                String alias = c.getAlias();

                BufferedImage dread = ImageHelper.read(ResourceHelper.getInstance().getUnitFile(alias + "_dn.png"));
                graphic.drawImage(dread, x + SPACING, y + SPACING, null);
                graphic.setFont(bigFont);
                DrawingUtil.superDrawString(graphic, c.getName(), x + DREADWIDTH / 2, y + DREADSUBHIGHT + SPACING,
                    Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Top,
                    stroke, Color.BLACK);
                graphic.setFont(smallFont);
                DrawingUtil.superDrawString(graphic, alias, x + DREADWIDTH / 2, y + DREADSUBHIGHT + LINEHEIGHT + SPACING,
                    Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Top,
                    stroke, Color.BLACK);

                String file = ResourceHelper.getInstance().getUnitFile((alias.equals("lgy") ? "orca" : "split" + alias) + "_dn.png");
                if (file != null) {
                    dread = ImageHelper.read(file);
                    graphic.drawImage(dread, x + SPACING, y + SPACING + DREADTEXHIGHT, null);
                    graphic.setFont(bigFont);
                    DrawingUtil.superDrawString(graphic, (alias.equals("lgy") ? "orca" : "split" + c.getName()), x + DREADWIDTH / 2, y + DREADTEXHIGHT + DREADSUBHIGHT + SPACING,
                        Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Top,
                        stroke, Color.BLACK);
                    graphic.setFont(smallFont);
                    DrawingUtil.superDrawString(graphic, (alias.equals("lgy") ? "orca" : "split" + alias), x + DREADWIDTH / 2, y + DREADTEXHIGHT + DREADSUBHIGHT + LINEHEIGHT + SPACING,
                        Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Top,
                        stroke, Color.BLACK);
                }

                x += DREADWIDTH;
            }
            right = Math.max(right, x);
            y += 2 * DREADTEXHIGHT;
        }
        if (left == right) {
            MessageHelper.sendMessageToEventChannel(event, "No colours found. Something has probably gone wrong.");
            return;
        }
        coloursImage = coloursImage.getSubimage(left, top, right - left, bottom - top);
        String fileNamePrefix = "colour_sample_" + top + "_" + left + "_" + (hues.size() == 1 ? hues.getFirst() : Constants.ALL);
        try (var fileUpload = FileUploadService.createFileUpload(coloursImage, fileNamePrefix)) {
            fileUpload.setDescription("Colour samples for " + (hues.size() == 1 ? "all the " + hues.getFirst() : "ALL the") + " units.");
            MessageHelper.sendFileUploadToChannel(event.getChannel(), fileUpload);
        } catch (IOException e) {
            BotLogger.error(new BotLogger.LogMessageOrigin(event), "Exception when closing FileUpload.", e);
        }
    }
}
