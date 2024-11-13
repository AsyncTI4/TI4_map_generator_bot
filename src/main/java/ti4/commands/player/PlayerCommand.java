package ti4.commands.player;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.CommandHelper;
import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class PlayerCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
            new Stats(),
            new Setup(),
            new SCPlay(),
            new SCUnplay(),
            new Pass(),
            new AbilityInfo(),
            new TurnEnd(),
            new TurnStart(),
            new SCPick(),
            new SCUnpick(),
            new Speaker(),
            new SendTG(),
            new SendCommodities(),
            new SendDebt(),
            new ClearDebt(),
            new ChangeColor(),
            new CorrectFaction(),
            new ChangeUnitDecal(),
            new UnitInfo(),
            new AddAllianceMember(),
            new RemoveAllianceMember(),
            new AddTeamMate(),
            new RemoveTeamMate(),
            new SetStatsAnchor(),
            new CCsButton()
    ).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));


    @Override
    public String getName() {
        return Constants.PLAYER;
    }

    @Override
    public String getDescription() {
        return "Player";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return ParentCommand.super.accept(event) &&
                CommandHelper.acceptIfPlayerInGame(event);
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
