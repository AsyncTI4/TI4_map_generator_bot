package ti4.commands2.help;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.ResourceHelper;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.image.DrawingUtil;
import ti4.image.ImageHelper;
import ti4.image.MapGenerator;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.UnitDecalService;
import ti4.service.image.FileUploadService;

class SampleDecals extends Subcommand {

    public SampleDecals() {
        super(Constants.SAMPLE_DECALS, "Show a sample image of dreadnoughts with various decals.");
        addOptions(new OptionData(OptionType.STRING, Constants.DECAL_HUE, "Category of decals to show (default: all)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> decals = Mapper.getDecals().stream()
            .filter(decalID -> UnitDecalService.userMayUseDecal(event.getUser().getId(), decalID))
            .collect(Collectors.toList());

        OptionMapping input = event.getOption(Constants.DECAL_HUE);
        if (input != null && !input.getAsString().equals("ALL") && !input.getAsString().isEmpty()) {
            if (input.getAsString().equals("Other")) {
                List<String> others = List.of("cb_10", "cb_11", "cb_52", "cb_81");
                decals = decals.stream()
                    .filter(others::contains)
                    .collect(Collectors.toList());
            } else {
                decals = decals.stream()
                    .filter(decalID -> Mapper.getDecalName(decalID).contains(input.getAsString()))
                    .collect(Collectors.toList());
            }
        }
        Collections.sort(decals);
        if (decals.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No decals found. Something has probably gone wrong.");
            return;
        }

        int SPACING = 24;
        int DREADWIDTH = 77 + 2 * SPACING;
        int DREADSUBHIGHT = 76;
        int LINEHEIGHT = 18;
        int DREADTEXHIGHT = DREADSUBHIGHT + 3 * LINEHEIGHT + 2 * SPACING;
        Font bigFont = Storage.getFont16();
        Font smallFont = Storage.getFont12();
        int PAGEWIDTH = 1280;
        int PAGEHIGHT = 1655;
        int PERROW = Math.min(PAGEWIDTH / DREADWIDTH, (int) Math.ceil(Math.sqrt(decals.size())));

        int left = ThreadLocalRandom.current().nextInt(PAGEWIDTH - PERROW * DREADWIDTH);
        int top = ThreadLocalRandom.current().nextInt(PAGEHIGHT - ((decals.size() + PERROW - 1) / PERROW) * DREADTEXHIGHT);
        int right = left + PERROW * DREADWIDTH;
        int bottom = top + ((decals.size() + PERROW - 1) / PERROW) * DREADTEXHIGHT;
        int x = left;
        int y = top;
        int n = 0;

        BufferedImage coloursImage = new BufferedImage(PAGEWIDTH, PAGEHIGHT, BufferedImage.TYPE_INT_ARGB);
        BufferedImage backgroundImage = ImageHelper.read(ResourceHelper.getInstance().getExtraFile("starfield.png"));
        Graphics graphic = coloursImage.getGraphics();
        graphic.drawImage(backgroundImage, 0, 0, null);
        BasicStroke stroke = new BasicStroke(3.0f);

        for (String d : decals) {
            BufferedImage dread = ImageHelper.read(ResourceHelper.getInstance().getDecalFile("sample/" + d + "_dn_wht.png"));
            graphic.drawImage(dread, x + SPACING, y + SPACING, null);

            String label = Mapper.getDecalName(d);
            int mid = -1;
            int i = label.indexOf(" ");
            while (i >= 0) {
                if (Math.abs(label.length() / 2.0 - 0.5 - mid) + (n % 2) > Math.abs(label.length() / 2.0 - 0.5 - i)) { // the (n%2) means that tie breaks will alternate each decal, hopefully reducing collisions
                    mid = i;
                }
                i = label.indexOf(" ", i + 1);
            }

            graphic.setFont(bigFont);
            DrawingUtil.superDrawString(graphic, (mid == -1 ? label : label.substring(0, mid)), x + DREADWIDTH / 2, y + DREADSUBHIGHT + SPACING,
                Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Top,
                stroke, Color.BLACK);
            DrawingUtil.superDrawString(graphic, (mid == -1 ? "" : label.substring(mid + 1)), x + DREADWIDTH / 2, y + DREADSUBHIGHT + LINEHEIGHT + SPACING,
                Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Top,
                stroke, Color.BLACK);
            graphic.setFont(smallFont);
            DrawingUtil.superDrawString(graphic, d, x + DREADWIDTH / 2, y + DREADSUBHIGHT + 2 * LINEHEIGHT + SPACING,
                Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Top,
                stroke, Color.BLACK);

            n += 1;
            if (n >= PERROW) {
                n = 0;
                x = left;
                y += DREADTEXHIGHT;
            } else {
                x += DREADWIDTH;
            }
        }
        coloursImage = coloursImage.getSubimage(left, top, right - left, bottom - top);
        FileUpload fileUpload = FileUploadService.createWebpFileUpload(coloursImage, "decal_sample_" + top + "_" + left)
            .setDescription("Decal samples for units.");
        MessageHelper.sendFileUploadToChannel(event.getChannel(), fileUpload);
    }
}
