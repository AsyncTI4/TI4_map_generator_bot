package ti4.commands.franken;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public abstract class UnitAddRemove extends FrankenSubcommandData {
    public UnitAddRemove(String name, String description) {
        super(name, description);
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID, "Unit Name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_1, "Unit Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_2, "Unit Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_3, "Unit Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_4, "Unit Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.UNIT_ID_5, "Unit Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> unitIDs = new ArrayList<>();

        //GET ALL UNIT OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream().filter(o -> o != null && o.getName().contains(Constants.UNIT_ID)).toList()) {
            unitIDs.add(option.getAsString());
        }

        unitIDs.removeIf(StringUtils::isEmpty);
        unitIDs.removeIf(unitID -> !Mapper.getUnits().containsKey(unitID));

        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }

        doAction(player, unitIDs);
        MessageHelper.sendMessageToChannel(event.getChannel(), player.checkUnitsOwned());
    }

    public abstract void doAction(Player player, List<String> unitIDs);

}
