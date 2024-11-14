package ti4.commands.franken;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.tech.TechInfo;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public abstract class FactionTechAddRemove extends FrankenSubcommandData {
    public FactionTechAddRemove(String name, String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.TECH, "Tech Name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH2, "Tech Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH3, "Tech Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH4, "Tech Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> techIDs = new ArrayList<>();

        //GET ALL TECH OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream().filter(o -> o != null && o.getName().contains(Constants.TECH)).toList()) {
            techIDs.add(option.getAsString());
        }

        techIDs.removeIf(StringUtils::isEmpty);
        techIDs.removeIf(id -> !Mapper.isValidTech(id));

        if (techIDs.isEmpty()) {
            MessageHelper.sendMessageToEventChannel(event, "No valid techs were provided. Please see `/search techs` for available choices.");
            return;
        }

        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        doAction(player, techIDs);

        TechInfo.sendTechInfo(game, player, event);
    }

    public abstract void doAction(Player player, List<String> leaderIDs);

}
