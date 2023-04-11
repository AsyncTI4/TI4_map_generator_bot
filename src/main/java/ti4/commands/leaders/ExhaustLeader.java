package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.Stats;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Leader;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class ExhaustLeader extends LeaderAction {
	public ExhaustLeader() {
		super(Constants.EXHAUST_LEADER, "Exhaust leader");
		addOptions(new OptionData(OptionType.STRING, Constants.TG, "TG count to add to leader").setRequired(false));
	}

	@Override
	void action(SlashCommandInteractionEvent event, String leader, Map activeMap, Player player) {
		Leader playerLeader = player.getLeader(leader);
		if (playerLeader != null) {
			if (playerLeader.isLocked()) {
				sendMessage("Leader '" + leader + "' is locked");
				return;
			}
			playerLeader.setExhausted(true);
			sendMessage(Helper.getFactionLeaderEmoji(player, playerLeader));
			StringBuilder messageText = new StringBuilder(Helper.getPlayerRepresentation(event, player))
					.append(" exhausted ").append(Helper.getLeaderFullRepresentation(player, playerLeader));
			OptionMapping optionTG = event.getOption(Constants.TG);
			if (optionTG != null) {
				Stats stats = new Stats();
				stats.preExecute(event);
				stats.setValue(event, activeMap, player, optionTG, playerLeader::setTgCount, playerLeader::getTgCount);
				messageText.append("\n").append(optionTG.getAsString()).append(Emojis.tg)
						.append(" was placed on top of the leader");
				if (playerLeader.getTgCount() != optionTG.getAsInt()) {
					messageText.append(" _(").append(String.valueOf(playerLeader.getTgCount())).append(Emojis.tg)
							.append(" total)_\n");
				}
			}
			sendMessage(messageText.toString());
		} else {
			sendMessage("Leader '" + leader + "'' not found");
		}
	}
}
