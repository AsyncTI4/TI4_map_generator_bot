package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;

public class FixTheFrankens extends AdminSubcommandData {

    public FixTheFrankens() {
        super(Constants.FIX_THE_FRANKENS, "Change franken_X to frankenX in a game");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "GameName").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping option = event.getOption(Constants.GAME_NAME);
        if (option != null) {
            String mapName = option.getAsString();
            if (!GameManager.getInstance().getGameNameToGame().containsKey(mapName)) {
                sendMessage("Game with such name does not exists, use /list_games");
                return;
            }
            Game activeGame = GameManager.getInstance().getGame(mapName);
            for(Player player : activeGame.getPlayers().values()){
                if(player.getFaction().contains("franken_")){
                    player.setFaction(player.getFaction().replace("_",""));
                }
            }
            GameSaveLoadManager.saveMap(activeGame, event);
            sendMessage("Save map: " + activeGame.getName());

        } else {
            sendMessage("No Game specified.");
        }
    }
}
