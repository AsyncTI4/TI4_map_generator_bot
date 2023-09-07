package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;

public class ExhaustLeader extends LeaderAction {
	public ExhaustLeader() {
		super(Constants.EXHAUST_LEADER, "Exhaust leader");
		addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "TG count to add to leader").setRequired(false));
	}

	@Override
	void action(SlashCommandInteractionEvent event, String leaderID, Map activeMap, Player player) {
		Leader playerLeader = player.unsafeGetLeader(leaderID);
		if (playerLeader == null) {
			sendMessage("Leader '" + leaderID + "'' not found");
			return;
		}

		if (playerLeader.isLocked()) {
			sendMessage("Leader '" + playerLeader.getId() + "' is locked");
			return;
		}

		playerLeader.setExhausted(true);
		sendMessage(Helper.getFactionLeaderEmoji(playerLeader));
		StringBuilder messageText = new StringBuilder(Helper.getPlayerRepresentation(player, activeMap))
				.append(" exhausted ").append(Helper.getLeaderFullRepresentation(playerLeader));
		OptionMapping optionTG = event.getOption(Constants.TG);
		if (optionTG != null) {
			playerLeader.setTgCount(optionTG.getAsInt());
			messageText.append("\n").append(optionTG.getAsString()).append(Emojis.tg)
					.append(" was placed on top of the leader");
			if (playerLeader.getTgCount() != optionTG.getAsInt()) {
				messageText.append(" _(").append(String.valueOf(playerLeader.getTgCount())).append(Emojis.tg)
						.append(" total)_\n");
			}
		}
		sendMessage(messageText.toString());
	}
}
