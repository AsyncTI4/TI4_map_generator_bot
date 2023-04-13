package ti4.commands.franken;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.AbilityInfo;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.BotLogger;

public class AbilityAdd extends FrankenSubcommandData {
    public AbilityAdd() {
        super(Constants.ABILITY_ADD, "Add an ability to your faction");
        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY, "Ability Name").setRequired(true).setAutoComplete(true));
    }
    
    public void execute(SlashCommandInteractionEvent event) {
        String abilityID = event.getOption(Constants.ABILITY, null, OptionMapping::getAsString);
        if (abilityID == null || abilityID.isBlank()) {
            sendMessage("No ability was entered");
            return;
        }
        if (!Mapper.getFactionAbilities().keySet().contains(abilityID)) {
            sendMessage("Ability not found: " + abilityID);
            BotLogger.log(event, "Could not add faction ability: " + abilityID);
            return;
        }

        Map activeMap = getActiveMap();
        Player player = activeMap.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeMap, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(Helper.getPlayerRepresentation(event, player)).append(" added an ability:\n").append(AbilityInfo.getAbilityRepresentation(abilityID));
        sendMessage(sb.toString());
        player.addFactionAbility(abilityID);
    }
}
