package ti4.commands.relic;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

public class RelicPurgeFragments extends RelicSubcommandData {

	public RelicPurgeFragments() {
		super(Constants.PURGE_FRAGMENTS, "Purge a number of relic fragments (for example, to gain a relic; may use unknown fragments).");
		addOptions(typeOption.setRequired(true));
		addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of fragments to purge (default 3, use this for NRA Fabrication or Black Market Forgery)."));
		addOptions(new OptionData(OptionType.BOOLEAN, Constants.ALSO_DRAW_RELIC, "'true' to also draw a relic"));
		addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Game game = getActiveGame();
		Player activePlayer = game.getPlayer(getUser().getId());
		activePlayer = Helper.getGamePlayer(game, activePlayer, event, null);
		activePlayer = Helper.getPlayer(game, activePlayer, event);
		if (activePlayer == null) {
			MessageHelper.sendMessageToEventChannel(event, "Player not found in game.");
			return;
		}
		String color = event.getOption(Constants.TRAIT, null, OptionMapping::getAsString);
		int count = event.getOption(Constants.COUNT, 3, OptionMapping::getAsInt);

		List<String> fragmentsToPurge = new ArrayList<>();
		List<String> unknowns = new ArrayList<>();
		List<String> playerFragments = activePlayer.getFragments();
		for (String id : playerFragments) {
			ExploreModel explore = Mapper.getExplore(id);
			if (explore.getType().equalsIgnoreCase(color)) {
				fragmentsToPurge.add(id);
			} else if (explore.getType().equalsIgnoreCase(Constants.FRONTIER)) {
				unknowns.add(id);
			}
		}

		while (fragmentsToPurge.size() > count) {
			fragmentsToPurge.remove(0);
		}

		while (fragmentsToPurge.size() < count) {
			if (unknowns.isEmpty()) {
				MessageHelper.sendMessageToEventChannel(event, "Not enough fragments. Note that default count is 3.");
				return;
			}
			fragmentsToPurge.add(unknowns.remove(0));
		}

		for (String id : fragmentsToPurge) {
			activePlayer.removeFragment(id);
			game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
		}

		CommanderUnlockCheck.checkAllPlayersInGame(game, "lanefir");

		String message = activePlayer.getRepresentation() + " purged fragments: " + fragmentsToPurge;
		MessageHelper.sendMessageToEventChannel(event, message);

		if (activePlayer.hasTech("dslaner")) {
			activePlayer.setAtsCount(activePlayer.getAtsCount() + 1);
			MessageHelper.sendMessageToEventChannel(event, activePlayer.getRepresentation() + " Put 1 commodity on ATS Armaments");
		}

		boolean drawRelic = event.getOption(Constants.ALSO_DRAW_RELIC, false, OptionMapping::getAsBoolean);
		if (drawRelic) {
			RelicDraw.drawRelicAndNotify(activePlayer, event, game);
		}
	}

}
