package ti4.commands.relic;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.explore.ExploreSubcommandData;
import ti4.helpers.Constants;

public class RelicCommand implements Command {
	
	private final Collection<RelicSubcommandData> subcommandData = getSubcommands();

	@Override
	public String getActionID() {
		return Constants.RELIC;
	}

	@Override
	public boolean accept(SlashCommandInteractionEvent event) {
		return event.getName().equals(getActionID());
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String subcommandName = event.getInteraction().getSubcommandName();
		for (RelicSubcommandData subcommand : subcommandData) {
			if (Objects.equals(subcommand.getName(), subcommandName)) {
				subcommand.preExecute(event);
				subcommand.execute(event);
			}
		}
	}
	
	private Collection<RelicSubcommandData> getSubcommands() {
		Collection<RelicSubcommandData> subcommands = new HashSet<>();
		return subcommands;
	}

	@Override
	public void registerCommands(CommandListUpdateAction commands) {
		// TODO Auto-generated method stub
		
	}

}
