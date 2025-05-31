package ti4.commands.franken;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.message.MessageHelper;

class BanFaction extends GameStateSubcommand {

    public BanFaction() {
        super(Constants.BAN_FACTION, "Ban A Faction From The Draft", true, false);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction Name").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION2, "Faction  Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION3, "Faction  Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION4, "Faction  Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION5, "Faction  Name").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION6, "Faction  Name").setAutoComplete(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        List<String> abilityIDs = new ArrayList<>();

        //GET ALL ABILITY OPTIONS AS STRING
        for (OptionMapping option : event.getOptions().stream().filter(o -> o != null && o.getName().contains(Constants.FACTION)).toList()) {
            abilityIDs.add(option.getAsString());
        }

        abilityIDs.removeIf(StringUtils::isEmpty);
        abilityIDs.removeIf(a -> !Mapper.getFactionIDs().contains(a));

        Game game = getGame();
        for (String ability : abilityIDs) {
            game.setStoredValue("bannedFactions", game.getStoredValue("bannedFactions") + "finSep" + ability);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Successfully banned " + Mapper.getFaction(ability).getFactionName());
        }
    }

}
