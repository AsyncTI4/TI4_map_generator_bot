package ti4.commands.franken;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;

public abstract class UnitAddRemove extends FrankenSubcommandData {
    public UnitAddRemove(String name, String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID, "Unit Name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_1, "Unit Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_2, "Unit Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_3, "Unit Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_4, "Unit Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_5, "Unit Name").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> unitIDs = new ArrayList<>();

        //GET ALL UNIT OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream().filter(o -> o != null && o.getName().contains(Constants.UNIT_ID)).toList()) {
            unitIDs.add(option.getAsString());
        }

        unitIDs.removeIf(StringUtils::isEmpty);
        unitIDs.removeIf(unitID -> !Mapper.getUnits().keySet().contains(unitID));

        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        doAction(player, unitIDs);
    }

    public abstract void doAction(Player player, List<String> unitIDs);

}
