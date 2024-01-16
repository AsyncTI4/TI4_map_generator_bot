package ti4.commands.status;

import java.util.List;
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
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.ButtonHelperCommanders;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

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

		
		if (poName.toLowerCase().contains("push boundaries")) {
			int aboveN = 0;
			for (Player p2 : player.getNeighbouringPlayers()) {
				if (player.getPlanets().size() > p2.getPlanets().size()) {
					aboveN = aboveN + 1;
				}
			}
			if (aboveN < 2) {
				MessageHelper.sendMessageToChannel(channel, "You do not have more planets than 2 neighbors");
				return;
			}
		}
		boolean scored = activeGame.scorePublicObjective(player.getUserID(), poID);
		if (!scored) {
			MessageHelper.sendMessageToChannel(channel, "No such Public Objective ID found or already scored, please retry");
		} else {
			informAboutScoring(event, channel, activeGame, player, poID);
			for(Player p2 : player.getNeighbouringPlayers()){
				if(p2.hasLeaderUnlocked("syndicatecommander")){
					p2.setTg(p2.getTg()+1);
					String msg = p2.getRepresentation(true, true) + " you gained 1tg due to your neighbor scoring a PO while you have syndicate commander. Your tgs went from "+(p2.getTg()-1)+" -> "+p2.getTg();
					MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(p2, activeGame),msg);
					ButtonHelperAbilities.pillageCheck(p2, activeGame);
					ButtonHelperAgents.resolveArtunoCheck(player, activeGame, 1);
				}
			}
		}
		Helper.checkEndGame(activeGame, player);
	}

	public static String getNameNEMoji(Game activeGame, int poID) {
		String id = "";
		Map<String, Integer> revealedPublicObjectives = activeGame.getRevealedPublicObjectives();
		for (Map.Entry<String, Integer> po : revealedPublicObjectives.entrySet()) {
			if (po.getValue().equals(poID)) {
				id = po.getKey();
				break;
			}
		}
		Map<String, String> publicObjectivesState1 = Mapper.getPublicObjectivesStage1();
		Map<String, String> publicObjectivesState2 = Mapper.getPublicObjectivesStage2();
		String poName1 = publicObjectivesState1.get(id);
		String poName2 = publicObjectivesState2.get(id);
		String poName = id;
		String emojiName = "Custom";
		if (poName1 != null) {
			poName = poName1;
			emojiName = Emojis.Public1alt;
		} else if (poName2 != null) {
			poName = poName2;
			emojiName = Emojis.Public2alt;
		}
		return poName + "_" + emojiName;
	}

	public static void informAboutScoring(GenericInteractionCreateEvent event, MessageChannel channel, Game activeGame, Player player, int poID) {
		String both = getNameNEMoji(activeGame, poID);
		String poName = both.split("_")[0];
		String emojiName = both.split("_")[1];

		String message = player.getRepresentation() + " scored " + emojiName + " __**" + poName + "**__";
		MessageHelper.sendMessageToChannel(channel, message);
		if (activeGame.isFoWMode()) {
			FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, message);
		}
		Helper.checkIfHeroUnlocked(event, activeGame, player);
		if (poName.toLowerCase().contains("sway the council") || poName.toLowerCase().contains("erect a monument") || poName.toLowerCase().contains("found a golden age")
			|| poName.toLowerCase().contains("amass wealth") || poName.toLowerCase().contains("manipulate galactic law") || poName.toLowerCase().contains("hold vast reserves")) {
			String message2 = player.getRepresentation(true, true) + " Click the names of the planets you wish to exhaust to score the objective.";
			List<Button> buttons = ButtonHelper.getExhaustButtonsWithTG(activeGame, player, "both");
			Button DoneExhausting = Button.danger("deleteButtons", "Done Exhausting Planets");
			buttons.add(DoneExhausting);
			MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame), message2, buttons);
		}
		if (poName.contains("Negotiate Trade Routes")) {
			int oldtg = player.getTg();
			if (oldtg > 4) {
				player.setTg(oldtg - 5);
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
					player.getRepresentation() + " Automatically deducted 5tg (" + oldtg + "->" + player.getTg() + ")");
			} else {
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Did not deduct 5tg because you didnt have that");
			}
		}
		if (poName.contains("Centralize Galactic Trade")) {
			int oldtg = player.getTg();
			if (oldtg > 9) {
				player.setTg(oldtg - 10);
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
					player.getRepresentation() + " Automatically deducted 10tg (" + oldtg + "->" + player.getTg() + ")");
			} else {
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Did not deduct 10tg because you didnt have that");
			}
		}
		if (poName.contains("Lead From the Front")) {
			int currentStrat = player.getStrategicCC();
			int currentTact = player.getTacticalCC();
			if (currentStrat + currentTact > 2) {
				if (currentStrat > 2) {
					for (int x = 0; x < 3; x++) {
						ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
					}
					player.setStrategicCC(currentStrat - 3);
					MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
						player.getRepresentation() + " Automatically deducted 3 strat cc (" + currentStrat + "->" + player.getStrategicCC() + ")");
				} else {
					String currentCC = player.getCCRepresentation();
					int subtract = 3 - currentStrat;
					for (int x = 0; x < currentStrat; x++) {
						ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
					}
					player.setStrategicCC(0);
					player.setTacticalCC(currentTact - subtract);
					MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
						player.getRepresentation() + " Automatically deducted 3 strat/tactic cc (" + currentCC + "->" + player.getCCRepresentation() + ")");
				}
			} else {
				MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame), "Did not deduct 3cc because you didnt have that");
			}
		}
		if (poName.contains("Galvanize the People")) {
			int currentStrat = player.getStrategicCC();
			int currentTact = player.getTacticalCC();
			if (currentStrat + currentTact > 5) {
				if (currentStrat > 5) {
					for (int x = 0; x < 6; x++) {
						ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
					}
					player.setStrategicCC(currentStrat - 6);
					MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
						player.getRepresentation() + " Automatically deducted 6 strat cc (" + currentStrat + "->" + player.getStrategicCC() + ")");
				} else {
					String currentCC = player.getCCRepresentation();
					int subtract = 6 - currentStrat;
					for (int x = 0; x < currentStrat; x++) {
						ButtonHelperCommanders.resolveMuaatCommanderCheck(player, activeGame, event);
					}
					player.setStrategicCC(0);
					player.setTacticalCC(currentTact - subtract);
					MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(player, activeGame),
						player.getRepresentation() + " Automatically deducted 6 strat/tactic cc (" + currentCC + "->" + player.getCCRepresentation() + ")");
				}
			} else {
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
