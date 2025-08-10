package ti4.commands.franken;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.TechInfoService;

abstract class FactionTechAddRemove extends GameStateSubcommand {

    public FactionTechAddRemove(String name, String description) {
        super(name, description, true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TECH, "Technology Name")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH2, "Technology Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH3, "Technology Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH4, "Technology Name").setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
                        .setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> techIDs = new ArrayList<>();

        // GET ALL TECH OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream()
                .filter(o -> o != null && o.getName().contains(Constants.TECH))
                .toList()) {
            techIDs.add(option.getAsString());
        }

        techIDs.removeIf(StringUtils::isEmpty);
        techIDs.removeIf(id -> !Mapper.isValidTech(id));

        if (techIDs.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(
                    event, "No valid technologies were provided. Please see `/search techs` for available choices.");
            return;
        }

        Player player = getPlayer();

        doAction(player, techIDs, event);

        TechInfoService.sendTechInfo(getGame(), player, event);
    }

    public abstract void doAction(Player player, List<String> leaderIDs, SlashCommandInteractionEvent event);
}
