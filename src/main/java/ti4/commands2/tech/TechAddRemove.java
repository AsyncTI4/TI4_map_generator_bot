package ti4.commands2.tech;

import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.TechnologyModel;
import ti4.service.leader.CommanderUnlockCheckService;

abstract class TechAddRemove extends GameStateSubcommand {

    public TechAddRemove(String id, String description) {
        super(id, description, true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.TECH, "Tech").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH2, "2nd Tech").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH3, "3rd Tech").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH4, "4th Tech").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();

        parseParameter(event, player, event.getOption(Constants.TECH));
        parseParameter(event, player, event.getOption(Constants.TECH2));
        parseParameter(event, player, event.getOption(Constants.TECH3));
        parseParameter(event, player, event.getOption(Constants.TECH4));

        CommanderUnlockCheckService.checkPlayer(player, "nekro", "jolnar");
    }

    private void parseParameter(SlashCommandInteractionEvent event, Player player, OptionMapping techOption) {
        if (techOption != null) {
            String techID = AliasHandler.resolveTech(techOption.getAsString());
            if (Mapper.isValidTech(techID)) {
                doAction(player, techID, event);
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
                doAction(player, possibleTechs.getFirst(), event);

            }
        }
    }

    public abstract void doAction(Player player, String techID, SlashCommandInteractionEvent event);
}
