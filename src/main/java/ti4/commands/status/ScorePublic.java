package ti4.commands.status;

import java.util.Map;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class ScorePublic extends StatusSubcommandData {
	public ScorePublic() {
		super(Constants.SCORE_OBJECTIVE, "Score Public Objective");
		addOptions(new OptionData(OptionType.INTEGER, Constants.PO_ID, "Public Objective ID that is between ()")
				.setRequired(true));
		addOptions(
				new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats")
						.setAutoComplete(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Game activeGame = getActiveGame();
		OptionMapping option = event.getOption(Constants.PO_ID);
		if (option == null) {
			MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Public Objective to score");
			return;
		}

		Player player = activeGame.getPlayer(getUser().getId());
		player = Helper.getGamePlayer(activeGame, player, event, null);
		player = Helper.getPlayer(activeGame, player, event);
		if (player == null) {
			MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
			return;
		}

		int poID = option.getAsInt();
		scorePO(event, event.getChannel(), activeGame, player, poID);
	}

	public static void scorePO(GenericInteractionCreateEvent event, MessageChannel channel, Game activeGame, Player player, int poID) {
		String both = getNameNEMoji(activeGame, poID);
        String poName = both.split("_")[0];
        if(poName.toLowerCase().contains("push boundaries")){
			int aboveN = 0;
			for(Player p2 : player.getNeighbouringPlayers()){
				if(player.getPlanets().size() > p2.getPlanets().size()){
					aboveN = aboveN + 1;
				}
			}
			if(aboveN < 2){
				MessageHelper.sendMessageToChannel(channel, "You do not have more planets than 2 neighbors");
				return;
			}
		}
		boolean scored = activeGame.scorePublicObjective(player.getUserID(), poID);
		if (!scored) {
			MessageHelper.sendMessageToChannel(channel, "No such Public Objective ID found or already scored, please retry");
		} else {
			informAboutScoring(event, channel, activeGame, player, poID);
		}
		Helper.checkEndGame(activeGame, player);
	}

	public static String getNameNEMoji(Game activeGame, int poID){
		String id = "";
		LinkedHashMap<String, Integer> revealedPublicObjectives = activeGame.getRevealedPublicObjectives();
		for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
            if (po.getValue().equals(poID)) {
                id = po.getKey();
                break;
            }
        }
        HashMap<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
        HashMap<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();
        String poName1 = publicObjectivesState1.get(id);
        String poName2 = publicObjectivesState2.get(id);
        String poName = id;
        String emojiName = "Custom";
        if (poName1 != null) {
            poName = poName1;
            emojiName = Emojis.Public1alt;
        } else if (poName2 != null){
            poName = poName2;
            emojiName = Emojis.Public2alt;
        }
		return poName+"_"+emojiName;
	}

    public static void informAboutScoring(GenericInteractionCreateEvent event, MessageChannel channel, Game activeGame, Player player, int poID) {
        String both = getNameNEMoji(activeGame, poID);
        String poName = both.split("_")[0];
        String emojiName =  both.split("_")[1];
        
        String message = Helper.getPlayerRepresentation(player, activeGame) + " scored " + emojiName + " __**" + poName + "**__";
        MessageHelper.sendMessageToChannel(channel, message);
		if (activeGame.isFoWMode()) {
			FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, message);
		}
        Helper.checkIfHeroUnlocked(event, activeGame, player);
		if(poName.toLowerCase().contains("sway the council") || poName.toLowerCase().contains("erect a monument") || poName.toLowerCase().contains("found a golden age") || poName.toLowerCase().contains("amass wealth") || poName.toLowerCase().contains("manipulate galactic law") || poName.toLowerCase().contains("hold vast reserves")){
			String message2 = Helper.getPlayerRepresentation(player, activeGame, activeGame.getGuild(), true) + " Click the names of the planets you wish to exhaust to score the objective.";
			List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, event);
			Button DoneExhausting =  Button.danger("deleteButtons", "Done Exhausting Planets");
			buttons.add(DoneExhausting);
			MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message2, buttons);
		}
		if(poName.contains("Negotiate Trade Routes")){
			int oldtg = player.getTg();
			if(oldtg > 4){
				player.setTg(oldtg-5);
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), Helper.getPlayerRepresentation(player, activeGame) + " Automatically deducted 5tg ("+oldtg+"->"+player.getTg()+")");
			}else{
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Did not deduct 5tg because you didnt have that");
			}
		}
		if(poName.contains("Centralize Galactic Trade")){
			int oldtg = player.getTg();
			if(oldtg > 9){
				player.setTg(oldtg-10);
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), Helper.getPlayerRepresentation(player, activeGame) + " Automatically deducted 10tg ("+oldtg+"->"+player.getTg()+")");
			}else{
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Did not deduct 10tg because you didnt have that");
			}
		}
		if(poName.contains("Lead From the Front")){
			int currentStrat = player.getStrategicCC();
			int currentTact = player.getTacticalCC();
			if(currentStrat+ currentTact > 2){
				if(currentStrat > 2){
					for(int x = 0; x < 3; x++){
						ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(player, activeGame, event);
					}
					player.setStrategicCC(currentStrat-3);
					MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), Helper.getPlayerRepresentation(player, activeGame) + " Automatically deducted 3 strat cc ("+currentStrat+"->"+player.getStrategicCC()+")");
				}else{
					String currentCC = Helper.getPlayerCCs(player);
					int subtract = 3 - currentStrat;
					for(int x = 0; x < currentStrat; x++){
						ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(player, activeGame, event);
					}
					player.setStrategicCC(0);
					player.setTacticalCC(currentTact-subtract);
					MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), Helper.getPlayerRepresentation(player, activeGame) + " Automatically deducted 3 strat/tactic cc ("+currentCC+"->"+Helper.getPlayerCCs(player)+")");
				}
			}else{
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Did not deduct 3cc because you didnt have that");
			}
		}
		if(poName.contains("Galvanize the People")){
			int currentStrat = player.getStrategicCC();
			int currentTact = player.getTacticalCC();
			if(currentStrat+ currentTact > 5){
				if(currentStrat > 5){
					for(int x = 0; x < 6; x++){
						ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(player, activeGame, event);
					}
					player.setStrategicCC(currentStrat-6);
					MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), Helper.getPlayerRepresentation(player, activeGame) + " Automatically deducted 6 strat cc ("+currentStrat+"->"+player.getStrategicCC()+")");
				}else{
					String currentCC = Helper.getPlayerCCs(player);
					int subtract = 6 - currentStrat;
					for(int x = 0; x < currentStrat; x++){
						ButtonHelperFactionSpecific.resolveMuaatCommanderCheck(player, activeGame, event);
					}
					player.setStrategicCC(0);
					player.setTacticalCC(currentTact-subtract);
					MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), Helper.getPlayerRepresentation(player, activeGame) + " Automatically deducted 6 strat/tactic cc ("+currentCC+"->"+Helper.getPlayerCCs(player)+")");
				}
			}else{
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Did not deduct 6cc because you didnt have that");
			}
		}


    }

	@Override
	public void reply(SlashCommandInteractionEvent event) {
		String userID = event.getUser().getId();
		Game activeGame = GameManager.getInstance().getUserActiveGame(userID);
		GameSaveLoadManager.saveMap(activeGame, event);
	}
}
