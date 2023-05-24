package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.*;
import ti4.message.MessageHelper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class Stats extends PlayerSubcommandData {
	public Stats() {
		super(Constants.STATS, "Player Stats: CC,TG,Commodities");
		addOptions(new OptionData(OptionType.STRING, Constants.CC, "CC's Example: 3/3/2 or +1/-1/+0"))
				.addOptions(new OptionData(OptionType.STRING, Constants.TACTICAL, "Tactical command counter count"))
				.addOptions(new OptionData(OptionType.STRING, Constants.FLEET, "Fleet command counter count"))
				.addOptions(new OptionData(OptionType.STRING, Constants.STRATEGY, "Strategy command counter count"))
				.addOptions(new OptionData(OptionType.STRING, Constants.TG, "Trade goods count"))
				.addOptions(new OptionData(OptionType.STRING, Constants.COMMODITIES, "Commodity count"))
				.addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES_TOTAL, "Commodity total count"))
				.addOptions(new OptionData(OptionType.INTEGER, Constants.STRATEGY_CARD, "Strategy Card Number count"))
				.addOptions(new OptionData(OptionType.INTEGER, Constants.SC_PLAYED, "Flip a Strategy Card from played to unplayed or unplayer to played"))
				.addOptions(new OptionData(OptionType.STRING, Constants.PASSED, "Player passed y/n"))
				.addOptions(new OptionData(OptionType.STRING, Constants.SPEAKER, "Player is speaker y/n"))
				.addOptions(new OptionData(OptionType.BOOLEAN, Constants.DUMMY, "Player is a placeholder"))
				.addOptions(new OptionData(OptionType.USER, Constants.PLAYER, "Player for which you set stats"))
				.addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR,"Faction or Color for which you set stats").setAutoComplete(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {

		Map activeMap = getActiveMap();
		Player player = activeMap.getPlayer(getUser().getId());
		player = Helper.getGamePlayer(activeMap, player, event, null);
		player = Helper.getPlayer(activeMap, player, event);
		if (player == null) {
			sendMessage("Player could not be found");
			return;
		}
		
		List<OptionMapping> optionMappings = event.getOptions();
		//NO OPTIONS SELECTED, JUST DISPLAY STATS
		if (optionMappings.isEmpty() && !activeMap.isFoWMode()) {
			sendMessage(getPlayersCurrentStatsText(player));
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
				@SuppressWarnings("ConstantConditions")
				String cc = AliasHandler.resolveFaction(optionCC.getAsString().toLowerCase());
				StringTokenizer tokenizer = new StringTokenizer(cc, "/");
				if (tokenizer.countTokens() != 3) {
					sendMessage("Wrong format for tokens count. Must be 3/3/3");
				} else {
					try {
						setValue(event, activeMap, player, "Tactics CC", player::setTacticalCC, player::getTacticalCC, tokenizer.nextToken(), true);
						setValue(event, activeMap, player, "Fleet CC", player::setFleetCC, player::getFleetCC, tokenizer.nextToken(), true);
						setValue(event, activeMap, player, "Strategy CC", player::setStrategicCC, player::getStrategicCC, tokenizer.nextToken(), true);
					} catch (Exception e) {
						sendMessage("Not number entered, check CC count again");
					}
				}
				Helper.isCCCountCorrect(event, activeMap, player.getColor());
			}
			if (optionT != null) {
				setValue(event, activeMap, player, optionT, player::setTacticalCC, player::getTacticalCC, true);
			}
			if (optionF != null) {
				setValue(event, activeMap, player, optionF, player::setFleetCC, player::getFleetCC, true);
			}
			if (optionS != null) {
				setValue(event, activeMap, player, optionS, player::setStrategicCC, player::getStrategicCC, true);
			}
			if (optionT != null || optionF != null || optionS != null || optionCC != null) {
				String newCCString = player.getTacticalCC() + "/" + player.getFleetCC() + "/" + player.getStrategicCC();
				sendMessage(Helper.getPlayerRepresentation(event, player) + " updated CCs: " + originalCCString + " -> " + newCCString);
			}
			if (optionT != null || optionF != null || optionS != null) {
				Helper.isCCCountCorrect(event, activeMap, player.getColor());
			}
		}
		optionMappings.remove(optionCC);
		optionMappings.remove(optionT);
		optionMappings.remove(optionF);
		optionMappings.remove(optionS);
		if (optionMappings.isEmpty()) return;

		sendMessage(Helper.getPlayerRepresentation(event, player, true) + " player stats changed:");
		
		OptionMapping optionTG = event.getOption(Constants.TG);
		if (optionTG != null) {
			setValue(event, activeMap, player, optionTG, player::setTg, player::getTg);
		}

		OptionMapping optionC = event.getOption(Constants.COMMODITIES);
		if (optionC != null) {
			setValue(event, activeMap, player, optionC, player::setCommodities, player::getCommodities);
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

		OptionMapping optionSpeaker = event.getOption(Constants.SPEAKER);
		if (optionSpeaker != null) {
			StringBuilder message = new StringBuilder(getGeneralMessage(event, player, optionSpeaker));
			String value = optionSpeaker.getAsString().toLowerCase();
			if ("y".equals(value) || "yes".equals(value)) {
				activeMap.setSpeaker(player.getUserID());
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
			} else if ("n".equals(value) || "no".equals(value)) {
				player.setPassed(false);
			} else {
				message.append(", which is not a valid input. Please use one of: y/yes/n/no");
			}
			sendMessage(message.toString());
		}
		pickSC(event, activeMap, player, event.getOption(Constants.STRATEGY_CARD));

		OptionMapping optionSCPlayed = event.getOption(Constants.SC_PLAYED);
		if (optionSCPlayed != null) {
			StringBuilder message = new StringBuilder();
			int sc = optionSCPlayed.getAsInt();
			if (sc > 0) {
				boolean scIsPlayed = activeMap.getScPlayed().get(sc);
				if (!scIsPlayed) {
					activeMap.setSCPlayed(sc, true);
					message.append("> flipped " + Helper.getSCEmojiFromInteger(sc) + " to "
							+ Helper.getSCBackEmojiFromInteger(sc) + " (played)");
				} else {
					activeMap.setSCPlayed(sc, false);

					for (Player player_ : activeMap.getPlayers().values()) {
						if (!player_.isRealPlayer()) {
							continue;
						}
						String faction = player_.getFaction();
						if (faction == null || faction.isEmpty() || faction.equals("null")) continue;
						player_.addFollowedSC(sc);
					}
					message.append("> flipped " + Helper.getSCBackEmojiFromInteger(sc) + " to "
							+ Helper.getSCEmojiFromInteger(sc) + " (unplayed)");
				}
			} else {
				message.append(
						"> attempted to change " + Constants.SC_PLAYED + ", but player has not picked an SC (SC = 0)");
			}
			sendMessage(message.toString());
		}

		OptionMapping optionDummy = event.getOption(Constants.DUMMY);
		if (optionDummy != null) {
			StringBuilder message = new StringBuilder(getGeneralMessage(event, player, optionDummy));
			boolean value = optionDummy.getAsBoolean();
			player.setDummy(value);
			sendMessage(message.toString());
		}

	}

	private String getPlayersCurrentStatsText(Player player) {
		StringBuilder sb = new StringBuilder(Helper.getPlayerRepresentation(getEvent(), player, false) + " player's current stats:\n");

		sb.append("> VP: ").append(player.getTotalVictoryPoints(getActiveMap()));
		sb.append("      CC: ").append(player.getTacticalCC()).append("/").append(player.getFleetCC()).append("/").append(player.getStrategicCC());
		sb.append("      ").append(Emojis.tg).append(player.getTg());
		sb.append("      ").append(Emojis.comm).append(player.getCommodities()).append("/").append(player.getCommoditiesTotal());
		sb.append("      ").append(Emojis.CFrag).append(player.getCrf());
		sb.append("   ").append(Emojis.IFrag).append(player.getIrf());
		sb.append("   ").append(Emojis.HFrag).append(player.getHrf());
		sb.append("   ").append(Emojis.UFrag).append(player.getVrf());
		if (!player.getSCs().isEmpty()) {
			sb.append("      ");
			for (int sc: player.getSCs()) {
				if (getActiveMap().getScPlayed().get(sc)) {
					sb.append(Helper.getSCBackEmojiFromInteger(sc));
				} else {
					sb.append(Helper.getSCEmojiFromInteger(sc));
				}
			}
		} else {
			sb.append("      No SC Picked");
		}
		sb.append("\n");
		sb.append("> Speaker: ").append(getActiveMap().getSpeaker().equals(player.getUserID())).append("\n");
		sb.append("> Passed: ").append(player.isPassed()).append("\n");
		sb.append("> Dummy: ").append(player.isDummy()).append("\n");
		
		sb.append("> Abilities: ").append(player.getFactionAbilities()).append("\n");
		sb.append("> Planets: ").append(player.getPlanets()).append("\n");
		sb.append("> Techs: ").append(player.getTechs()).append("\n");
		sb.append("> Relics: ").append(player.getRelics()).append("\n");
		sb.append("> Leaders: [");
		player.getLeaders().forEach(l -> sb.append(" [" + l.getId() + "] "));
		sb.append("]\n");

		return sb.toString();
	}

	@Override
	public void reply(SlashCommandInteractionEvent event) {
		String userID = event.getUser().getId();
		Map activeMap = MapManager.getInstance().getUserActiveMap(userID);
		MapSaveLoadManager.saveMap(activeMap, event);

		GenerateMap.getInstance().saveImage(activeMap, event);
	}

	public boolean pickSC(SlashCommandInteractionEvent event, Map activeMap, Player player, OptionMapping optionSC) {
			if (optionSC == null) {
				return false;
			}
			if (activeMap.isMapOpen() && !activeMap.isCommunityMode()) {
				activeMap.setMapStatus(MapStatus.open);
			}
			int scNumber = optionSC.getAsInt();
			LinkedHashMap<Integer, Integer> scTradeGoods = activeMap.getScTradeGoods();
			if (player.getColor() == null || "null".equals(player.getColor()) || player.getFaction() == null) {
				MessageHelper.sendMessageToChannel(event.getChannel(), "Can pick SC only if faction and color picked");
				return false;
			}
			if (!scTradeGoods.containsKey(scNumber)) {
				MessageHelper.sendMessageToChannel(event.getChannel(),"Strategy Card must be from possible ones in Game");
				return false;
			}

			LinkedHashMap<String, Player> players = activeMap.getPlayers();
			for (Player playerStats : players.values()) {
				if (playerStats.getSCs().contains(scNumber)) {
					MessageHelper.sendMessageToChannel(event.getChannel(), "SC #"+scNumber+" is already picked.");
					return false;
				}
			}
	
			player.addSC(scNumber);
			String messageToSend = Helper.getColourAsMention(event.getGuild(),player.getColor()) + " picked SC #"+scNumber;
			if (activeMap.isFoWMode()) {
				FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, messageToSend);
			}
			Integer tgCount = scTradeGoods.get(scNumber);
			if (tgCount != null && tgCount != 0) {
				int tg = player.getTg();
				tg += tgCount;
				messageToSend = Helper.getColourAsMention(event.getGuild(),player.getColor()) +" gained "+tgCount +" tgs from picking SC #"+scNumber;
				MessageHelper.sendMessageToChannel(event.getChannel(),"You gained "+tgCount +" tgs from picking SC #"+scNumber);
				if (activeMap.isFoWMode()) {
					FoWHelper.pingAllPlayersWithFullStats(activeMap, event, player, messageToSend);
				}
				
				player.setTg(tg);
			}
			return true;
	}

	public void setValue(SlashCommandInteractionEvent event, Map map, Player player, OptionMapping option,
			Consumer<Integer> consumer, Supplier<Integer> supplier) {
		setValue(event, map, player, option.getName(), consumer, supplier, option.getAsString(), false);
	}

	public void setValue(SlashCommandInteractionEvent event, Map map, Player player, OptionMapping option,
			Consumer<Integer> consumer, Supplier<Integer> supplier, boolean suppressMessage) {
		setValue(event, map, player, option.getName(), consumer, supplier, option.getAsString(), suppressMessage);
	}

	public void setValue(SlashCommandInteractionEvent event, Map map, Player player, String optionName,
			Consumer<Integer> consumer, Supplier<Integer> supplier, String value, boolean suppressMessage) {
		try {
			boolean setValue = !value.startsWith("+") && !value.startsWith("-");
			int number = Integer.parseInt(value);
			int existingNumber = supplier.get();
			if (setValue) {
				consumer.accept(number);
				String messageToSend = getSetValueMessage(event, player, optionName, number, existingNumber);
				if (!suppressMessage) sendMessage(messageToSend);
				if (map.isFoWMode()) {
					FoWHelper.pingAllPlayersWithFullStats(map, event, player, messageToSend);
				}
			} else {
				int newNumber = existingNumber + number;
				newNumber = Math.max(newNumber, 0);
				consumer.accept(newNumber);
				String messageToSend = getChangeValueMessage(event, player, optionName, number, existingNumber, newNumber);
				if (!suppressMessage) sendMessage(messageToSend);
				if (map.isFoWMode()) {
					FoWHelper.pingAllPlayersWithFullStats(map, event, player, messageToSend);
				}
			}
		} catch (Exception e) {
			sendMessage("Could not parse number for: " + optionName);
		}
	}

	public static String getSetValueMessage(SlashCommandInteractionEvent event, Player player, String optionName, Integer setToNumber, Integer existingNumber) {
		return "> set **" + optionName + "** to **" + String.valueOf(setToNumber) + "**   _(was "
				+ String.valueOf(existingNumber) + ", a change of " + String.valueOf(setToNumber - existingNumber)
				+ ")_";
	}

	public static String getChangeValueMessage(SlashCommandInteractionEvent event, Player player, String optionName,
			Integer changeNumber, Integer existingNumber, Integer newNumber) {
		String changeDescription = "changed";
		if (changeNumber > 0) {
			changeDescription = "increased";
		} else if (changeNumber < 0) {
			changeDescription = "decreased";
		}
		return "> " + changeDescription + " **" + optionName + "** by " + String.valueOf(changeNumber) + "   _(was "
				+ String.valueOf(existingNumber) + ", now **" + String.valueOf(newNumber) + "**)_";
	}

	private static String getGeneralMessage(SlashCommandInteractionEvent event, Player player, OptionMapping option) {
		return ">  set **" + option.getName() + "** to " + option.getAsString();
	}
}
