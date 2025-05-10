package ti4.commands.franken;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;

class BanAbility extends GameStateSubcommand {

    public BanAbility() {
        super(Constants.BAN_ABILITY, "Ban An Ability From The Draft", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY, "Ability Name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY_1, "Ability Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY_2, "Ability Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY_3, "Ability Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY_4, "Ability Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ABILITY_5, "Ability Name").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> abilityIDs = new ArrayList<>();

        //GET ALL ABILITY OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream().filter(o -> o != null && o.getName().contains(Constants.ABILITY)).toList()) {
            abilityIDs.add(option.getAsString());
        }

        abilityIDs.removeIf(StringUtils::isEmpty);
        abilityIDs.removeIf(a -> !Mapper.getAbilities().containsKey(a));

        Game game = getGame();
        for (String ability : abilityIDs) {
            game.setStoredValue("bannedAbilities", game.getStoredValue("bannedAbilities") + "finSep" + ability);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Successfully banned " + Mapper.getAbility(ability).getName());
        }
    }

}
