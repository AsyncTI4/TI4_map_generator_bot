package ti4.discord.interactions.commands.help;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.FileUpload;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.draw.ImageGrid;
import ti4.image.draw.StarfieldPage;
import ti4.image.io.Fonts;
import ti4.image.io.ImageHelper;
import ti4.image.io.ImageResources;
import ti4.image.io.ImageUploads;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.UnitDecalService;

class SampleDecals extends Subcommand {

    private static final int SPACING = 24;
    private static final int DREAD_WIDTH = 77 + 2 * SPACING;
    private static final int DREAD_IMAGE_HEIGHT = 76;
    private static final int LINE_HEIGHT = 18;
    private static final int CELL_HEIGHT = DREAD_IMAGE_HEIGHT + 3 * LINE_HEIGHT + 2 * SPACING;

    public SampleDecals() {
        super(Constants.SAMPLE_DECALS, "Show a sample image of dreadnoughts with various decals.");
        addOptions(new OptionData(OptionType.STRING, Constants.DECAL_HUE, "Category of decals to show (default: all)")
                .setAutoComplete(true));
        addOptions(new OptionData(
                        OptionType.STRING, Constants.COLOR, "Which color to use as the background (default: blue)")
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> decals = Mapper.getDecals().stream()
                .filter(decalID ->
                        UnitDecalService.userMayUseDecal(event.getUser().getId(), decalID))
                .collect(Collectors.toList());

        OptionMapping input = event.getOption(Constants.DECAL_HUE);
        if (input != null
                && !Constants.ALL.equals(input.getAsString())
                && !input.getAsString().isEmpty()) {
            if ("Other".equals(input.getAsString())) {
                List<String> others = List.of("cb_10", "cb_11", "cb_52", "cb_81");
                decals = decals.stream().filter(others::contains).collect(Collectors.toList());
            } else {
                decals = decals.stream()
                        .filter(decalID -> Mapper.getDecalName(decalID).contains(input.getAsString()))
                        .collect(Collectors.toList());
            }
        }
        if (decals.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No decals found. Something has probably gone wrong.");
            return;
        }

        String color = event.getOption(Constants.COLOR, "blue", OptionMapping::getAsString);
        if (!Mapper.isValidColor(color)) color = "blue";
        String colorID = Mapper.getColorID(color);

        Collections.sort(decals);

        int perRow = Math.min(StarfieldPage.width() / DREAD_WIDTH, (int) Math.ceil(Math.sqrt(decals.size())));
        int rows = (decals.size() + perRow - 1) / perRow;
        StarfieldPage page = new StarfieldPage(perRow * DREAD_WIDTH, rows * CELL_HEIGHT);
        ImageGrid.Layout layout = new ImageGrid.Layout(page.left(), page.top(), perRow, DREAD_WIDTH, CELL_HEIGHT);

        drawDecals(page.graphics(), layout, decals, colorID);

        BufferedImage decalsImage = page.crop(layout.left() + perRow * DREAD_WIDTH, layout.top() + rows * CELL_HEIGHT);
        FileUpload fileUpload =
                ImageUploads.createFileUpload(decalsImage, "decal_sample_" + page.top() + "_" + page.left());
        MessageHelper.sendFileUploadToChannel(event.getChannel(), fileUpload);
    }

    private static void drawDecals(Graphics2D graphic, ImageGrid.Layout layout, List<String> decals, String colorID) {
        ImageGrid.LabelStyle labels = new ImageGrid.LabelStyle(
                Fonts.ti(16),
                Fonts.ti(12),
                new BasicStroke(3.0f),
                DREAD_WIDTH,
                DREAD_IMAGE_HEIGHT + SPACING,
                LINE_HEIGHT);
        UnitKey base = Units.getUnitKey(UnitType.Dreadnought, colorID);
        BufferedImage noDecal = ImageHelper.read(ImageResources.unit(base));
        String decalSuffix = Mapper.getColor(colorID).blackWhiteFileSuffix();
        int index = 0;
        for (String decalId : decals) {
            Point cell = layout.origin(index);
            BufferedImage decal = ImageHelper.read(ImageResources.decal(decalId + "_dn" + decalSuffix));
            graphic.drawImage(noDecal, cell.x + SPACING, cell.y + SPACING, null);
            graphic.drawImage(decal, cell.x + SPACING, cell.y + SPACING, null);
            List<String> titles = splitInTwoRows(Mapper.getDecalName(decalId), (index % layout.columns()) % 2);
            ImageGrid.drawLabels(graphic, labels, cell.x, cell.y, titles, List.of(decalId));
            index++;
        }
    }

    private static List<String> splitInTwoRows(String label, int tieBreak) {
        int mid = -1;
        int space = label.indexOf(' ');
        while (space >= 0) {
            if (Math.abs(label.length() / 2.0 - 0.5 - mid) + tieBreak > Math.abs(label.length() / 2.0 - 0.5 - space)) {
                mid = space;
            }
            space = label.indexOf(' ', space + 1);
        }
        return mid == -1 ? List.of(label, "") : List.of(label.substring(0, mid), label.substring(mid + 1));
    }
}
