package ti4.commands.leaders;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.leader.ExhaustLeaderService;

class ExhaustLeader extends GameStateSubcommand {

	public ExhaustLeader() {
		super(Constants.EXHAUST_LEADER, "Exhaust leader", true, true);
		addOptions(new OptionData(OptionType.STRING, Constants.LEADER, "Leader for which to do action").setRequired(true).setAutoComplete(true));
		addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
		addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade good count to add to leader"));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Game game = getGame();
		Player player = getPlayer();
		String leaderID = event.getOption(Constants.LEADER, null, OptionMapping::getAsString);

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
		ExhaustLeaderService.exhaustLeader(game, player, playerLeader, tgCount);
	}
}
