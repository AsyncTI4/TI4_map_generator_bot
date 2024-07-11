package ti4.commands.tech;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;

public class TechChangeType extends TechSubcommandData {
    public TechChangeType() {
        super(Constants.CHANGE_TYPE, "Change what color a tech displays as");
        addOptions(new OptionData(OptionType.STRING, Constants.TECH, "Tech").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH_TYPE, "The type you're setting the tech to").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH2, "2nd Tech").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH3, "3rd Tech").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH4, "4th Tech").setAutoComplete(true));
        // addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction").setRequired(false));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        parseParameter(event, event.getOption(Constants.TECH), event.getOption(Constants.TECH_TYPE), game);
        parseParameter(event, event.getOption(Constants.TECH2), event.getOption(Constants.TECH_TYPE), game);
        parseParameter(event, event.getOption(Constants.TECH3), event.getOption(Constants.TECH_TYPE), game);
        parseParameter(event, event.getOption(Constants.TECH4), event.getOption(Constants.TECH_TYPE), game);

    }

    private void parseParameter(SlashCommandInteractionEvent event, OptionMapping techOption, OptionMapping techType, Game game) {
        if (techOption != null && techType != null) {
            String techID = AliasHandler.resolveTech(techOption.getAsString());
            if (Mapper.isValidTech(techID)) {
                game.setStoredValue("colorChange" + techID, techType.getAsString());
            } else {
                Map<String, TechnologyModel> techs = Mapper.getTechs();
                List<String> possibleTechs = techs.entrySet().stream().filter(value -> value.getValue().getName().toLowerCase().contains(techID))
                    .map(Map.Entry::getKey).toList();
                if (possibleTechs.isEmpty()) {
                    MessageHelper.sendMessageToEventChannel(event, "No matching Tech found");
                    return;
                } else if (possibleTechs.size() > 1) {
                    MessageHelper.sendMessageToEventChannel(event, "More that one matching Tech found");
                    return;
                }
                game.setStoredValue("colorChange" + techID, techType.getAsString());

            }
        }
    }

}
