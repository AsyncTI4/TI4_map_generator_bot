package ti4.commands.help;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import software.amazon.awssdk.regions.servicemetadata.EmailServiceMetadata;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;

public class ListUnits extends HelpSubcommandData {

    public ListUnits() {
        super(Constants.LIST_UNITS, "List all units");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // String searchString = event.getOption(Constants.SEARCH, null, OptionMapping::getAsString);

        // Map<String, UnitModel> unitList = Mapper.getUnits();
        // String message = "**__Unit List__**\n" + unitList.entrySet().stream()
        //     .map(e -> "`" + e.getKey() + "`= " + getUnitRepresentation(e.getKey()))
        //     .filter(s -> searchString == null ? true : s.toLowerCase().contains(searchString))
        //     .sorted()
        //     .collect(Collectors.joining("\n"));

        // MessageHelper.sendMessageToThread(event.getChannel(), "Unit List", message);



        List<MessageEmbed> messageEmbeds = new ArrayList<MessageEmbed>();

        for (UnitModel unitModel : Mapper.getUnits().values()) {
            messageEmbeds.add(getUnitRepresentationEmbed(unitModel));
        }
        for (MessageEmbed messageEmbed : messageEmbeds.subList(0, 10)) {
            event.getChannel().sendMessageEmbeds(messageEmbed).queue();
        }
    }

    public static String getUnitRepresentation(String unitID) {
        UnitModel unit = Mapper.getUnit(unitID);

        if (unit == null) {
            return "Unit not found: " + unitID;
        }
        String faction = unit.getFaction();
        String factionEmoji = Helper.getEmojiFromDiscord(faction);
        String unitEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());

        String representation = unitEmoji + " " + unit.getName() + factionEmoji + ": " + unit.getAbility();
        return representation;

    }

    public static MessageEmbed getUnitRepresentationEmbed(UnitModel unit) {
        
        String faction = unit.getFaction();
        String factionEmoji = Helper.getEmojiFromDiscord(faction);
        String unitEmoji = Helper.getEmojiFromDiscord(unit.getBaseType());

        String representation = unitEmoji + " " + unit.getName() + factionEmoji + " " + faction + ")";

        EmbedBuilder eb = new EmbedBuilder();
        /*
            Set the title:
            1. Arg: title as string
            2. Arg: URL as string or could also be null
        */
        eb.setTitle(factionEmoji + " " + unit.getName(), null);

        /*
            Set the color
        */
        // eb.setColor(Color.red);
        // eb.setColor(new Color(0xF40C0C));
        // eb.setColor(new Color(255, 0, 54));

        /*
            Set the text of the Embed:
            Arg: text as string
        */
        eb.setDescription(unitEmoji + " " + unit.getBaseType());


        // String afbText = unit.getAfbHitsOn() + 



        /*
            Add fields to embed:
            1. Arg: title as string
            2. Arg: text as string
            3. Arg: inline mode true / false
        */
        // eb.addField("Title of field", "test of field", false);
        // eb.addField("Title of field", "test of field", false);
        eb.addField("Abilities:", unit.getCardText(), true);
        eb.addField("Title of inline field", "test of inline field", true);
        eb.addField("Title of inline field", "test of inline field", true);

        /*
            Add spacer like field
            Arg: inline mode true / false
        */
        // eb.addBlankField(false);

        /*
            Add embed author:
            1. Arg: name as string
            2. Arg: url as string (can be null)
            3. Arg: icon url as string (can be null)
        */
        // eb.setAuthor("name", null, "https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/zekroBot_Logo_-_round_small.png");

        /*
            Set footer:
            1. Arg: text as string
            2. icon url as string (can be null)
        */
        // eb.setFooter("Text", "https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/zekroBot_Logo_-_round_small.png");

        /*
            Set image:
            Arg: image url as string
        */
        // eb.setImage("https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/logo%20-%20title.png");

        /*
            Set thumbnail image:
            Arg: image url as string
        */
        // eb.setThumbnail("https://github.com/zekroTJA/DiscordBot/blob/master/.websrc/logo%20-%20title.png");

        return eb.build();
    }
}
