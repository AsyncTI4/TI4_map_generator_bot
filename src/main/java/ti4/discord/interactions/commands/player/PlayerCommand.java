package ti4.discord.interactions.commands.player;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.helpers.Constants;

public class PlayerCommand extends AbstractParentCommand {

    public PlayerCommand() {
        super(
                Constants.PLAYER,
                "Player",
                new Stats(),
                new Setup(),
                new SCPlay(),
                new SCUnplay(),
                new Pass(),
                new Unpass(),
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
                new ChangeUnitDecal(),
                new UnitInfo(),
                new AddAllianceMember(),
                new RemoveAllianceMember(),
                new AddTeamMate(),
                new RemoveTeamMate(),
                new SetStatsAnchor(),
                new CCsButton());
    }
}
