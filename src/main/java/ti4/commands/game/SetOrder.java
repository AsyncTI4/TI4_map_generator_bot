package ti4.commands.game;

import java.util.Map;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;

public class SetOrder extends GameSubcommandData {

    public SetOrder() {
        super(Constants.SET_ORDER, "Set player order in game");
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1 @playerName").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7 @playerName"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8 @playerName"));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        OptionMapping gameOption = event.getOption(Constants.GAME_NAME);
        User callerUser = event.getUser();
        String mapName;
        if (gameOption != null) {
            mapName = event.getOptions().get(0).getAsString();
            if (!GameManager.getInstance().getGameNameToGame().containsKey(mapName)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Game with such name does not exists, use /list_games");
                return;
            }
        } else {
            Game userActiveGame = GameManager.getInstance().getUserActiveGame(callerUser.getId());
            if (userActiveGame == null){
                MessageHelper.sendMessageToChannel(event.getChannel(), "Specify game or set active Game");
                return;
            }
            mapName = userActiveGame.getName();
        }

        GameManager gameManager = GameManager.getInstance();
        Game activeGame = gameManager.getGame(mapName);

        LinkedHashMap<String, Player> newPlayerOrder = new LinkedHashMap<>();
        LinkedHashMap<String, Player> players = new LinkedHashMap<>(activeGame.getPlayers());
        LinkedHashMap<String, Player> playersBackup = new LinkedHashMap<>(activeGame.getPlayers());
        try {
            setPlayerOrder(newPlayerOrder, players, event.getOption(Constants.PLAYER1));
            setPlayerOrder(newPlayerOrder, players, event.getOption(Constants.PLAYER2));
            setPlayerOrder(newPlayerOrder, players, event.getOption(Constants.PLAYER3));
            setPlayerOrder(newPlayerOrder, players, event.getOption(Constants.PLAYER4));
            setPlayerOrder(newPlayerOrder, players, event.getOption(Constants.PLAYER5));
            setPlayerOrder(newPlayerOrder, players, event.getOption(Constants.PLAYER6));
            setPlayerOrder(newPlayerOrder, players, event.getOption(Constants.PLAYER7));
            setPlayerOrder(newPlayerOrder, players, event.getOption(Constants.PLAYER8));
            if (!players.isEmpty()) {
                newPlayerOrder.putAll(players);
            }
            activeGame.setPlayers(newPlayerOrder);
        } catch (Exception e){
            activeGame.setPlayers(playersBackup);
        }
        GameSaveLoadManager.saveMap(activeGame, event);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player order set.");
    }

    public void setPlayerOrder(Map<String, Player> newPlayerOrder, LinkedHashMap<String, Player> players, OptionMapping option1) {
        if (option1 != null) {
            String id = option1.getAsUser().getId();
            Player player = players.get(id);
            if (player != null){
                newPlayerOrder.put(id, player);
                players.remove(id);
            }
        }
    }
    public void setPlayerOrder(Map<String, Player> newPlayerOrder, LinkedHashMap<String, Player> players, Player player) {
        if (player != null){
            newPlayerOrder.put(player.getUserID(), player);
            players.remove(player.getUserID());
        }
        
    }
}