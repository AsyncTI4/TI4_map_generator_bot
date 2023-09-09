package ti4.commands.game;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.helpers.Constants;
import ti4.map.*;
import ti4.message.MessageHelper;

public class SetStatus extends GameSubcommandData{

    public SetStatus() {
        super(Constants.SET_STATUS, "Set Game Status information:");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_STATUS, "Game status: open, locked").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Game name to be set as active"));
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

        OptionMapping mapStatusOption = event.getOption(Constants.GAME_STATUS);
        if (mapStatusOption == null){
            MessageHelper.replyToMessage(event, "Game status not specified.");
            return;
        }
        String status = mapStatusOption.getAsString().toLowerCase();

        GameManager gameManager = GameManager.getInstance();
        Game gameToChangeStatusFor = gameManager.getGame(mapName);

        try {
            GameStatus gameStatus = GameStatus.valueOf(status);
            if (gameStatus == GameStatus.open) {
                gameToChangeStatusFor.setGameStatus(GameStatus.open);
            } else if (gameStatus == GameStatus.locked) {
                gameToChangeStatusFor.setGameStatus(GameStatus.locked);
            }
        } catch (Exception e){
            MessageHelper.replyToMessage(event, "Map: " + mapName + " status was not changed, as invalid status entered.");
            return;
        }
        GameSaveLoadManager.saveMap(gameToChangeStatusFor, event);
        MessageHelper.replyToMessage(event, "Map: "+ mapName +" status changed to: " + gameToChangeStatusFor.getGameStatus());
    }
}
