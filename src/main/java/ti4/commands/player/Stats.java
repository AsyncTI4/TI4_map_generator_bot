package ti4.commands.player;

import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperAbilities;
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

public class Stats extends PlayerSubcommandData {
	public Stats() {
		super(Constants.STATS, "Player Stats: CC,TG,Commodities");
		addOptions(new OptionData(OptionType.STRING, Constants.CC, "CC's Example: 3/3/2 or +1/-1/+0"))
			.addOptions(new OptionData(OptionType.STRING, Constants.TACTICAL, "Tactical command counter count - can use +1/-1 etc. to add/subtract"))
			.addOptions(new OptionData(OptionType.STRING, Constants.FLEET, "Fleet command counter count - can use +1/-1 etc. to add/subtract"))
			.addOptions(new OptionData(OptionType.STRING, Constants.STRATEGY, "Strategy command counter count - can use +1/-1 etc. to add/subtract"))
			.addOptions(new OptionData(OptionType.STRING, Constants.TG, "Trade goods count - can use +1/-1 etc. to add/subtract"))
			.addOptions(new OptionData(OptionType.STRING, Constants.COMMODITIES, "Commodity count - can use +1/-1 etc. to add/subtract"))
			.addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES_TOTAL, "Commodity total count"))
			.addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card Number"))
			.addOptions(new OptionData(OptionType.INTEGER, Constants.TURN_COUNT, "# turns this round"))
			.addOptions(new OptionData(OptionType.INTEGER, Constants.SC_PLAYED, "Flip a Strategy Card's played status. Enter the SC #"))
			.addOptions(new OptionData(OptionType.STRING, Constants.PASSED, "Player has passed y/n"))
			.addOptions(new OptionData(OptionType.STRING, Constants.SPEAKER, "Player is speaker y/n"))
			//.addOptions(new OptionData(OptionType.INTEGER, Constants.AUTO_SABO_PASS_MEDIAN, "Median time in hours before player auto passes on sabo if they have none"))
			//.addOptions(new OptionData(OptionType.INTEGER, Constants.PERSONAL_PING_INTERVAL, "Overrides the games autoping inteveral system for your turn specifically"))
			.addOptions(new OptionData(OptionType.BOOLEAN, Constants.DUMMY, "Player is a placeholder"))
			//.addOptions(new OptionData(OptionType.BOOLEAN, Constants.PREFERS_DISTANCE, "Prefers distance based tile selection"))
			.addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"))
			.addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
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

		OptionMapping playerOption = event.getOption(Constants.PLAYER);
		OptionMapping factionColorOption = event.getOption(Constants.FACTION_COLOR);
		List<OptionMapping> optionMappings = event.getOptions();
		optionMappings.remove(playerOption);
		optionMappings.remove(factionColorOption);
		//NO OPTIONS SELECTED, JUST DISPLAY STATS
		if (optionMappings.isEmpty()) {
			if (activeGame.isFoWMode()) {
				MessageHelper.sendMessageToChannel(player.getPrivateChannel(), getPlayersCurrentStatsText(player, activeGame));
			} else {
				sendMessage(getPlayersCurrentStatsText(player, activeGame));
			}
			return;
		}

		//DO CCs FIRST
		OptionMapping optionCC = event.getOption(Constants.CC);
		OptionMapping optionT = event.getOption(Constants.TACTICAL);
		OptionMapping optionF = event.getOption(Constants.FLEET);
		OptionMapping optionS = event.getOption(Constants.STRATEGY);
		if (optionCC != null && (optionT != null || optionF != null || optionS != null)) {
			sendMessage("Use format 3/3/3 for command counters or individual values, not both");
		} else {
			String originalCCString = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
			if (optionCC != null) {
				String cc = AliasHandler.resolveFaction(optionCC.getAsString().toLowerCase());
				StringTokenizer tokenizer = new StringTokenizer(cc, "/");
				if (tokenizer.countTokens() != 3) {
					sendMessage("Wrong format for tokens count. Must be 3/3/3");
				} else {
					try {
						setValue(event, activeGame, player, "Tactics CC", player::setTacticalCC, player::getTacticalCC, tokenizer.nextToken(), true);
						setValue(event, activeGame, player, "Fleet CC", player::setFleetCC, player::getFleetCC, tokenizer.nextToken(), true);
						setValue(event, activeGame, player, "Strategy CC", player::setStrategicCC, player::getStrategicCC, tokenizer.nextToken(), true);
					} catch (Exception e) {
						sendMessage("Not number entered, check CC count again");
					}
				}
				Helper.isCCCountCorrect(event, activeGame, player.getColor());
			}
			if (optionT != null) {
				setValue(event, activeGame, player, optionT, player::setTacticalCC, player::getTacticalCC, true);
			}
			if (optionF != null) {
				setValue(event, activeGame, player, optionF, player::setFleetCC, player::getFleetCC, true);
			}
			if (optionS != null) {
				setValue(event, activeGame, player, optionS, player::setStrategicCC, player::getStrategicCC, true);
			}
			if (optionT != null || optionF != null || optionS != null || optionCC != null) {
				String newCCString = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
				sendMessage(player.getRepresentation() + " updated CCs: " + originalCCString + " -> " + newCCString);
			}
			if (optionT != null || optionF != null || optionS != null) {
				Helper.isCCCountCorrect(event, activeGame, player.getColor());
			}
		}
		optionMappings.remove(optionCC);
		optionMappings.remove(optionT);
		optionMappings.remove(optionF);
		optionMappings.remove(optionS);
		if (optionMappings.isEmpty()) return;

		sendMessage(player.getRepresentation(true, true) + " player stats changed:");

		OptionMapping optionTG = event.getOption(Constants.TG);
		if (optionTG != null) {
			int oldTg = player.getTg();
			setValue(event, activeGame, player, optionTG, player::setTg, player::getTg);
			if (optionTG.getAsString().contains("+")) {
				ButtonHelperAbilities.pillageCheck(player, activeGame);
			} else {
				if (player.getTg() > oldTg) {
					ButtonHelperAbilities.pillageCheck(player, activeGame);
				}
			}

		}

		OptionMapping optionC = event.getOption(Constants.COMMODITIES);
		if (optionC != null) {

			setValue(event, activeGame, player, optionC, player::setCommodities, player::getCommodities);
			if (player.hasAbility("military_industrial_complex") && ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame).size() > 1) {
				MessageHelper.sendMessageToChannelWithButtons(ButtonHelper.getCorrectChannel(player, activeGame),
					player.getRepresentation(true, true) + " you have the opportunity to buy axis orders", ButtonHelperAbilities.getBuyableAxisOrders(player, activeGame));
			}
			if (player.getLeaderIDs().contains("mykomentoricommander") && !player.hasLeaderUnlocked("mykomentoricommander")) {
				ButtonHelper.commanderUnlockCheck(player, activeGame, "mykomentori", event);
			}
		}

		OptionMapping optionMedian = event.getOption(Constants.AUTO_SABO_PASS_MEDIAN);
		if (optionMedian != null) {
			player.setAutoSaboPassMedian(optionMedian.getAsInt());
		}

		OptionMapping optionInterval = event.getOption(Constants.PERSONAL_PING_INTERVAL);
		if (optionInterval != null) {
			player.setPersonalPingInterval(optionInterval.getAsInt());
			Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
			for (Game activeGame2 : mapList.values()) {
				for (Player player2 : activeGame2.getRealPlayers()) {
					if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
						player2.setPersonalPingInterval(optionInterval.getAsInt());
						GameSaveLoadManager.saveMap(activeGame2);
					}
				}

			}
		}

		OptionMapping optionPref = event.getOption(Constants.PREFERS_DISTANCE);
		if (optionPref != null) {
			player.setPreferenceForDistanceBasedTacticalActions(optionPref.getAsBoolean());
			Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
			for (Game activeGame2 : mapList.values()) {
				for (Player player2 : activeGame2.getRealPlayers()) {
					if (player2.getUserID().equalsIgnoreCase(player.getUserID())) {
						player2.setPreferenceForDistanceBasedTacticalActions(optionPref.getAsBoolean());
						GameSaveLoadManager.saveMap(activeGame2);
					}
				}
			}
		}

		Integer commoditiesTotalCount = event.getOption(Constants.COMMODITIES_TOTAL, null, OptionMapping::getAsInt);
		if (commoditiesTotalCount != null) {
			if (commoditiesTotalCount < 1 || commoditiesTotalCount > 10) {
				sendMessage("**Warning:** Total Commodities count seems like a wrong value:");
			}
			player.setCommoditiesTotal(commoditiesTotalCount);
			String message = ">  set **Total Commodities** to " + commoditiesTotalCount + Emojis.comm;
			sendMessage(message);
		}

		Integer turnCount = event.getOption(Constants.TURN_COUNT, null, OptionMapping::getAsInt);
		if (turnCount != null) {

			player.setTurnCount(turnCount);
			String message = ">  set **Turn Count** to " + turnCount;
			sendMessage(message);
		}

		OptionMapping optionSpeaker = event.getOption(Constants.SPEAKER);
		if (optionSpeaker != null) {
			StringBuilder message = new StringBuilder(getGeneralMessage(event, player, optionSpeaker));
			String value = optionSpeaker.getAsString().toLowerCase();
			if ("y".equals(value) || "yes".equals(value)) {
				activeGame.setSpeaker(player.getUserID());
			} else {
				message.append(", which is not a valid input. Please use one of: y/yes");
			}
			sendMessage(message.toString());
		}

		OptionMapping optionPassed = event.getOption(Constants.PASSED);
		if (optionPassed != null) {
			StringBuilder message = new StringBuilder(getGeneralMessage(event, player, optionPassed));
			String value = optionPassed.getAsString().toLowerCase();
			if ("y".equals(value) || "yes".equals(value)) {
				player.setPassed(true);
				// Turn.pingNextPlayer(event, activeMap, player);
				if(activeGame.playerHasLeaderUnlockedOrAlliance(player, "olradincommander")){
					ButtonHelperCommanders.olradinCommanderStep1(player, activeGame);
				}
			} else if ("n".equals(value) || "no".equals(value)) {
				player.setPassed(false);
			} else {
				message.append(", which is not a valid input. Please use one of: y/yes/n/no");
			}
			sendMessage(message.toString());
		}

		pickSC(event, activeGame, player, event.getOption(Constants.STRATEGY_CARD));

		OptionMapping optionSCPlayed = event.getOption(Constants.SC_PLAYED);
		if (optionSCPlayed != null) {
			StringBuilder message = new StringBuilder();
			int sc = optionSCPlayed.getAsInt();
			if (sc > 0) {
				boolean scIsPlayed = activeGame.getScPlayed().get(sc);
				if (!scIsPlayed) {
					activeGame.setSCPlayed(sc, true);
					message.append("> flipped ").append(Emojis.getSCEmojiFromInteger(sc)).append(" to ").append(Emojis.getSCBackEmojiFromInteger(sc)).append(" (played)");
				} else {
					activeGame.setSCPlayed(sc, false);

					for (Player player_ : activeGame.getPlayers().values()) {
						if (!player_.isRealPlayer()) {
							continue;
						}
						String faction = player_.getFaction();
						if (faction == null || faction.isEmpty() || "null".equals(faction)) continue;
						player_.addFollowedSC(sc);
					}
					message.append("> flipped ").append(Emojis.getSCBackEmojiFromInteger(sc)).append(" to ").append(Emojis.getSCEmojiFromInteger(sc)).append(" (unplayed)");
				}
			} else {
				message.append(
					"> attempted to change " + Constants.SC_PLAYED + ", but player has not picked an SC (SC = 0)");
			}
			sendMessage(message.toString());
		}

		OptionMapping optionDummy = event.getOption(Constants.DUMMY);
		if (optionDummy != null) {
			boolean value = optionDummy.getAsBoolean();
			player.setDummy(value);
			sendMessage(getGeneralMessage(event, player, optionDummy));
		}

	}

	private String getPlayersCurrentStatsText(Player player, Game activeGame) {
		StringBuilder sb = new StringBuilder(player.getRepresentation() + " player's current stats:\n");

		sb.append("> VP: ").append(player.getTotalVictoryPoints());
		sb.append("      CC: ").append(player.getTacticalCC()).append("/").append(player.getFleetCC()).append("/").append(player.getStrategicCC());
		sb.append("      ").append(Emojis.getTGorNomadCoinEmoji(activeGame)).append(player.getTg());
		sb.append("      ").append(Emojis.comm).append(player.getCommodities()).append("/").append(player.getCommoditiesTotal());
		sb.append("      ").append(Emojis.CFrag).append(player.getCrf());
		sb.append("   ").append(Emojis.IFrag).append(player.getIrf());
		sb.append("   ").append(Emojis.HFrag).append(player.getHrf());
		sb.append("   ").append(Emojis.UFrag).append(player.getUrf());
		if (!player.getSCs().isEmpty()) {
			sb.append("      ");
			for (int sc : player.getSCs()) {
				if (getActiveGame().getScPlayed() != null && !getActiveGame().getScPlayed().isEmpty() && getActiveGame().getScPlayed().get(sc) != null) {
					sb.append(Emojis.getSCBackEmojiFromInteger(sc));
				} else {
					sb.append(Emojis.getSCEmojiFromInteger(sc));
				}
			}
		} else {
			sb.append("      No SC Picked");
		}
		sb.append("\n");
		sb.append("> Debt: `").append(player.getDebtTokens()).append("`\n");
		sb.append("> Speaker: `").append(getActiveGame().getSpeaker().equals(player.getUserID())).append("`\n");
		sb.append("> Passed: `").append(player.isPassed()).append("`\n");
		sb.append("> Dummy: `").append(player.isDummy()).append("`\n");
		sb.append("> Stats Anchor: `").append(player.getPlayerStatsAnchorPosition()).append("`\n");

		sb.append("> Abilities: `").append(player.getAbilities()).append("`\n");
		sb.append("> Planets: `").append(player.getPlanets()).append("`\n");
		sb.append("> Techs: `").append(player.getTechs()).append("`\n");
		sb.append("> Fragments: `").append(player.getFragments()).append("`\n");
		sb.append("> Relics: `").append(player.getRelics()).append("`\n");
		sb.append("> Mahact CC: `").append(player.getMahactCC()).append("`\n");
		sb.append("> Leaders: `").append(player.getLeaderIDs()).append("`\n");
		sb.append("> Owned PNs: `").append(player.getPromissoryNotesOwned()).append("`\n");
		sb.append("> Owned Units: `").append(player.getUnitsOwned()).append("`\n");
		sb.append("> Alliance Members: ").append(player.getAllianceMembers()).append("\n");
		sb.append("> Followed SCs: `").append(player.getFollowedSCs().toString()).append("`\n");
		sb.append("> Expected Number of Hits: `").append((player.getExpectedHitsTimes10() / 10.0)).append("`\n");
		sb.append("> Actual Hits: `").append(player.getActualHits()).append("`\n");
		sb.append("> Total Resource Value of Units: ").append(Emojis.resources).append("`").append(player.getTotalResourceValueOfUnits()).append("`\n");
		sb.append("> Total Hit-point Value of Units: ").append(Emojis.PinkHeart).append("`").append(player.getTotalHPValueOfUnits()).append("`\n");
		sb.append("> Total Combat Value of Units: ").append("💥").append("`").append(player.getTotalCombatValueOfUnits()).append("`\n");
		sb.append("> Total Unit Ability Value of Units: ").append(Emojis.UnitUpgradeTech).append("`").append(player.getTotalUnitAbilityValueOfUnits()).append("`\n");
		sb.append("> Decal Set: `").append(player.getDecalName()).append("`\n");
		Guild guild = activeGame.getGuild();
		if (guild != null && activeGame.isFrankenGame() && guild.getThreadChannelById(player.getBagInfoThreadID()) != null) {
			sb.append("> Bag Draft Thread: ").append(guild.getThreadChannelById(player.getBagInfoThreadID()).getAsMention()).append("\n");
		}
		sb.append("\n");

		return sb.toString();
	}

	public boolean pickSC(GenericInteractionCreateEvent event, Game activeGame, Player player, OptionMapping optionSC) {
		if (optionSC == null) {
			return false;
		}
		int scNumber = optionSC.getAsInt();
		return secondHalfOfPickSC(event, activeGame, player, scNumber);
	}

	public boolean secondHalfOfPickSC(GenericInteractionCreateEvent event, Game activeGame, Player player, int scNumber) {
		Map<Integer, Integer> scTradeGoods = activeGame.getScTradeGoods();
		if (player.getColor() == null || "null".equals(player.getColor()) || player.getFaction() == null) {
			MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Can only pick SC if both Faction and Color have been picked");
			return false;
		}
		if (!scTradeGoods.containsKey(scNumber)) {
			MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "Strategy Card must be from possible ones in Game: " + scTradeGoods.keySet());
			return false;
		}

		Map<String, Player> players = activeGame.getPlayers();
		for (Player playerStats : players.values()) {
			if (playerStats.getSCs().contains(scNumber)) {
				MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), "SC #" + scNumber + " is already picked.");
				return false;
			}
		}

		player.addSC(scNumber);
		if (activeGame.isFoWMode()) {
			String messageToSend = Emojis.getColorEmojiWithName(player.getColor()) + " picked SC #" + scNumber;
			FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, messageToSend);
		}

		if (scNumber == 5 && !activeGame.isHomeBrewSCMode() && !player.getPromissoryNotes().containsKey(player.getColor() + "_ta")) {
			MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), player.getRepresentation(true, true) + " heads up, you just picked trade but dont currently hold your Trade Agreement");
		}

		Integer tgCount = scTradeGoods.get(scNumber);
		String msg = player.getRepresentation(true, true) +
            "\n> Picked: " + Helper.getSCRepresentation(activeGame, scNumber);
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg);
		if (tgCount != null && tgCount != 0) {
			int tg = player.getTg();
			tg += tgCount;
			MessageHelper.sendMessageToChannel((MessageChannel) event.getChannel(), player.getRepresentation() + " gained " + tgCount + " tgs from picking SC #" + scNumber);
			if (activeGame.isFoWMode()) {
				String messageToSend = Emojis.getColorEmojiWithName(player.getColor()) + " gained " + tgCount + " tgs from picking SC #" + scNumber;
				FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, messageToSend);
			}
			player.setTg(tg);
			if (player.getLeaderIDs().contains("hacancommander") && !player.hasLeaderUnlocked("hacancommander")) {
				ButtonHelper.commanderUnlockCheck(player, activeGame, "hacan", event);
			}
			ButtonHelperAbilities.pillageCheck(player, activeGame);
		}
		return true;
	}

	public void setValue(SlashCommandInteractionEvent event, Game activeGame, Player player, OptionMapping option,
		Consumer<Integer> consumer, Supplier<Integer> supplier) {
		setValue(event, activeGame, player, option.getName(), consumer, supplier, option.getAsString(), false);
	}

	public void setValue(SlashCommandInteractionEvent event, Game activeGame, Player player, OptionMapping option,
		Consumer<Integer> consumer, Supplier<Integer> supplier, boolean suppressMessage) {
		setValue(event, activeGame, player, option.getName(), consumer, supplier, option.getAsString(), suppressMessage);
	}

	public void setValue(SlashCommandInteractionEvent event, Game activeGame, Player player, String optionName,
		Consumer<Integer> consumer, Supplier<Integer> supplier, String value, boolean suppressMessage) {
		try {
			boolean setValue = !value.startsWith("+") && !value.startsWith("-");
			String explanation = "";
			if (value.contains("?")) {
				explanation = value.substring(value.indexOf("?") + 1);
				value = value.substring(0, value.indexOf("?")).replace(" ", "");
			}

			int number = Integer.parseInt(value);
			int existingNumber = supplier.get();
			if (setValue) {
				consumer.accept(number);
				String messageToSend = getSetValueMessage(event, player, optionName, number, existingNumber, explanation);
				if (!suppressMessage) sendMessage(messageToSend);
				if (activeGame.isFoWMode()) {
					FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, messageToSend);
				}
			} else {
				int newNumber = existingNumber + number;
				newNumber = Math.max(newNumber, 0);
				consumer.accept(newNumber);
				String messageToSend = getChangeValueMessage(event, player, optionName, number, existingNumber, newNumber, explanation);
				if (!suppressMessage) sendMessage(messageToSend);
				if (activeGame.isFoWMode()) {
					FoWHelper.pingAllPlayersWithFullStats(activeGame, event, player, messageToSend);
				}
			}
		} catch (Exception e) {
			sendMessage("Could not parse number for: " + optionName);
		}
	}

	public static String getSetValueMessage(SlashCommandInteractionEvent event, Player player, String optionName, Integer setToNumber, Integer existingNumber, String explanation) {
		if (explanation == null || "".equalsIgnoreCase(explanation)) {
			return "> set **" + optionName + "** to **" + setToNumber + "**   _(was "
				+ existingNumber + ", a change of " + (setToNumber - existingNumber)
				+ ")_";
		} else {
			return "> set **" + optionName + "** to **" + setToNumber + "**   _(was "
				+ existingNumber + ", a change of " + (setToNumber - existingNumber)
				+ ")_ for the reason of: " + explanation;
		}

	}

	public static String getChangeValueMessage(SlashCommandInteractionEvent event, Player player, String optionName,
		Integer changeNumber, Integer existingNumber, Integer newNumber, String explanation) {
		String changeDescription = "changed";
		if (changeNumber > 0) {
			changeDescription = "increased";
		} else if (changeNumber < 0) {
			changeDescription = "decreased";
		}
		if (explanation == null || "".equalsIgnoreCase(explanation)) {
			return "> " + changeDescription + " **" + optionName + "** by " + changeNumber + "   _(was "
				+ existingNumber + ", now **" + newNumber + "**)_";
		} else {
			return "> " + changeDescription + " **" + optionName + "** by " + changeNumber + "   _(was "
				+ existingNumber + ", now **" + newNumber + "**)_ for the reason of: " + explanation;
		}

	}

	private static String getGeneralMessage(SlashCommandInteractionEvent event, Player player, OptionMapping option) {
		return ">  set **" + option.getName() + "** to " + option.getAsString();
	}
}
