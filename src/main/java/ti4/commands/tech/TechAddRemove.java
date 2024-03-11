package ti4.commands.tech;

import java.util.List;
import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.TechnologyModel;

public abstract class TechAddRemove extends TechSubcommandData {
    public TechAddRemove(String id, String description) {
        super(id, description);
        addOptions(new OptionData(OptionType.STRING, Constants.TECH, "Tech").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH2, "2nd Tech").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH3, "3rd Tech").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TECH4, "4th Tech").setAutoComplete(true));
        // addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set up faction").setRequired(false));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        player = Helper.getPlayer(activeGame, player, event);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        player = Helper.getPlayer(activeGame, player, event);
        if (player == null){
            sendMessage("Player/Faction/Color could not be found in map:" + activeGame.getName());
            return;
        }

        parseParameter(event, player, event.getOption(Constants.TECH));
        parseParameter(event, player, event.getOption(Constants.TECH2));
        parseParameter(event, player, event.getOption(Constants.TECH3));
        parseParameter(event, player, event.getOption(Constants.TECH4));
        
        if(player.getLeaderIDs().contains("nekrocommander") && !player.hasLeaderUnlocked("nekrocommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "nekro", event);
        }
        if(player.getLeaderIDs().contains("jolnarcommander") && !player.hasLeaderUnlocked("jolnarcommander")){
            ButtonHelper.commanderUnlockCheck(player, activeGame, "jolnar", event);
            }
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
                if (possibleTechs.isEmpty()){
                    sendMessage("No matching Tech found");
                    return;
                } else if (possibleTechs.size() > 1){
                    sendMessage("More that one matching Tech found");
                    return;
                }
                doAction(player, possibleTechs.get(0), event);
                
            }
        }
    }

    public abstract void doAction(Player player, String techID, SlashCommandInteractionEvent event);
}
