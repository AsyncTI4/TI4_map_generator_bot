package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.CombatTempModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;

public class ExhaustLeader extends LeaderAction {
	public ExhaustLeader() {
		super(Constants.EXHAUST_LEADER, "Exhaust leader");
		addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "TG count to add to leader").setRequired(false));
	}

	@Override
	void action(SlashCommandInteractionEvent event, String leaderID, Game game, Player player) {
		Leader playerLeader = player.unsafeGetLeader(leaderID);
		if (playerLeader == null) {
			MessageHelper.sendMessageToEventChannel(event, "Leader '" + leaderID + "'' not found");
			return;
		}

		if (playerLeader.isLocked()) {
			MessageHelper.sendMessageToEventChannel(event, "Leader '" + playerLeader.getId() + "' is locked");
			return;
		}

		if (playerLeader.isExhausted()) {
			MessageHelper.sendMessageToEventChannel(event, "Leader '" + playerLeader.getId() + "' is exhausted already");
			return;
		}

		Integer tgCount = event.getOption(Constants.TG, null, OptionMapping::getAsInt);
		exhaustLeader(event, game, player, playerLeader, tgCount);
	}

	public static void exhaustLeader(GenericInteractionCreateEvent event, Game game, Player player, Leader leader, Integer tgCount) {
		leader.setExhausted(true);
		LeaderModel leaderModel = leader.getLeaderModel().orElse(null);
		String message = player.getRepresentation() + " exhausted: ";
		if (leaderModel != null) {
			MessageHelper.sendMessageToChannelWithEmbed(event.getMessageChannel(), message, leaderModel.getRepresentationEmbed());
		} else {
			MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + leader.getId());
		}

		if (tgCount != null) {
			StringBuilder sb = new StringBuilder();
			leader.setTgCount(tgCount);
			sb.append("\n").append(tgCount).append(Emojis.getTGorNomadCoinEmoji(game))
				.append(" was placed on top of the leader");
			if (leader.getTgCount() != tgCount) {
				sb.append(" *(").append(tgCount).append(Emojis.getTGorNomadCoinEmoji(game)).append(" total)*\n");
			}
			MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
		}

		var posssibleCombatMod = CombatTempModHelper.GetPossibleTempModifier(Constants.LEADER, leader.getId(), player.getNumberTurns());
		if (posssibleCombatMod != null) {
			player.addNewTempCombatMod(posssibleCombatMod);
			MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Combat modifier will be applied next time you push the combat roll button.");
		}
	}
}
