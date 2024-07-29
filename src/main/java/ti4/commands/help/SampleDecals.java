package ti4.commands.help;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ThreadLocalRandom;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.BasicStroke;
import java.awt.Color;
import net.dv8tion.jda.api.utils.FileUpload;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import ti4.ResourceHelper;
import ti4.generator.Mapper;
import ti4.generator.MapGenerator;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.helpers.ImageHelper;
import ti4.helpers.Storage;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ColorModel;
import ti4.commands.player.ChangeUnitDecal;

public class SampleDecals extends HelpSubcommandData {
    public SampleDecals() {
        super(Constants.SAMPLE_DECALS, "Show a sample image of dreadnoughts with various decals.");
        addOptions(new OptionData(OptionType.STRING, Constants.DECAL_HUE, "Category of decals to show (default: all)").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.COLOR, "Color of the dreadnoughts under the decals (default: your color, if in a game channel, else blue)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<String> decals = Mapper.getDecals().stream()
            .filter(decalID -> ChangeUnitDecal.userMayUseDecal(event.getUser().getId(), decalID))
            .collect(Collectors.toList());;
        OptionMapping input = event.getOption(Constants.DECAL_HUE);
        if (input == null || input.getAsString().equals("ALL") || input.getAsString().equals(""))
        {
        }
        else if (input.getAsString().equals("Other"))
        {
            List<String> others = Arrays.asList(new String[]{"cb_10", "cb_10", "cb_52", "cb_81"});
            decals = decals.stream()
                .filter(decalID -> others.contains(decalID))
                .collect(Collectors.toList());
        }
        else
        {
            decals = decals.stream()
                .filter(decalID -> Mapper.getDecalName(decalID).contains(input.getAsString()))
                .collect(Collectors.toList());;
        }
        Collections.sort(decals);
        if (decals.size() == 0)
        {
            MessageHelper.sendMessageToEventChannel(event, "No decals found. Something has probably gone wrong.");
            return;
        }

        int SPACING = 24;
        int DREADWIDTH = 77 + 2*SPACING;
        int DREADSUBHIGHT = 76;
        int LINEHEIGHT = 18;
        int DREADTEXHIGHT = DREADSUBHIGHT + 3*LINEHEIGHT + 2*SPACING;
        Font bigFont = Storage.getFont16();
        Font smallFont = Storage.getFont12();
        int PAGEWIDTH = 1280;
        int PAGEHIGHT = 1655;
        int PERROW = Math.min(PAGEWIDTH/DREADWIDTH, (int) Math.ceil(Math.sqrt(decals.size())));
        
        int left = ThreadLocalRandom.current().nextInt(PAGEWIDTH - PERROW * DREADWIDTH);
        int top = ThreadLocalRandom.current().nextInt(PAGEHIGHT - ((decals.size()+PERROW-1)/PERROW) * DREADTEXHIGHT);
        int right = left + PERROW * DREADWIDTH;
        int bottom = top + ((decals.size()+PERROW-1)/PERROW) * DREADTEXHIGHT;
        int x = left;
        int y = top;
        int n = 0;
        
        BufferedImage coloursImage = new BufferedImage(PAGEWIDTH, PAGEHIGHT, BufferedImage.TYPE_INT_ARGB);
        BufferedImage backgroundImage = ImageHelper.read(ResourceHelper.getInstance().getExtraFile("starfield.png"));
        Graphics graphic = coloursImage.getGraphics();
        graphic.drawImage(backgroundImage, 0, 0, null);
        BasicStroke stroke = new BasicStroke(3.0f);
        
        String color = "blu";
        OptionMapping colInput = event.getOption(Constants.COLOR);
        if (colInput == null || colInput.getAsString().equals(""))
        {
            Game game = getActiveGame();
            if (game != null)
            {
                Player player = game.getPlayer(getUser().getId());
                player = Helper.getGamePlayer(game, player, event, null);
                color = Mapper.getColorID(player.getColor());
            }
        }
        else
        {
            color = Mapper.getColorID(colInput.getAsString());
        }
        color = (color == null ? "blu" : color);
        String suffix =  MapGenerator.getBlackWhiteFileSuffix(color);
        
        for (String d: decals)
        {
            BufferedImage dread = ImageHelper.read(ResourceHelper.getInstance().getUnitFile(color + "_dn.png"));
            graphic.drawImage(dread, x + SPACING, y + SPACING, null);
            BufferedImage decal = ImageHelper.read(ResourceHelper.getInstance().getDecalFile(d + "_dn" + suffix));
            graphic.drawImage(decal, x + SPACING, y + SPACING, null);
            
            String label = Mapper.getDecalName(d);
            int mid = -1;
            int i = label.indexOf(" ");
            while (i >= 0) {
                if (Math.abs(label.length()/2.0-0.5 - mid) + (n%2) > Math.abs(label.length()/2.0-0.5 - i)) // the (n%2) means that tie breaks will alternate each decal, hopefully reducing collisions
                {
                    mid = i;
                }
                i = label.indexOf(" ", i + 1);
            }
            
            graphic.setFont(bigFont);
            MapGenerator.superDrawString(graphic, (mid == -1 ? label : label.substring(0,mid)), x + DREADWIDTH/2, y + DREADSUBHIGHT + SPACING,
                Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Top,
                stroke, Color.BLACK);
            MapGenerator.superDrawString(graphic, (mid == -1 ? "" : label.substring(mid+1)), x + DREADWIDTH/2, y + DREADSUBHIGHT + LINEHEIGHT + SPACING,
                Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Top,
                stroke, Color.BLACK);
            graphic.setFont(smallFont);
            MapGenerator.superDrawString(graphic, d, x + DREADWIDTH/2, y + DREADSUBHIGHT + 2*LINEHEIGHT + SPACING,
                Color.WHITE, MapGenerator.HorizontalAlign.Center, MapGenerator.VerticalAlign.Top,
                stroke, Color.BLACK);

            n += 1;
            if (n >= PERROW)
            {
                n = 0;
                x = left;
                y += DREADTEXHIGHT;
            }
            else
            {
                x += DREADWIDTH;
            }
        }
        coloursImage = coloursImage.getSubimage(left, top, right-left, bottom-top);
        FileUpload fileUpload = MapGenerator.uploadToDiscord(coloursImage, 1.0f, "decal_sample_" + top + "_" + left)
            .setDescription("Decal samples for units.");
        MessageHelper.sendFileUploadToChannel(event.getChannel(), fileUpload);
    }
}
