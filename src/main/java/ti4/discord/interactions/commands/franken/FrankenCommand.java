package ti4.discord.interactions.commands.franken;

import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.discord.interactions.commands.franken.ban.Ban;
import ti4.helpers.Constants;

public class FrankenCommand extends AbstractParentCommand {

    public FrankenCommand() {
        super(
                Constants.FRANKEN,
                "Franken",
                new AbilityAdd(),
                new AbilityRemove(),
                new LeaderAdd(),
                new LeaderRemove(),
                new FactionTechAdd(),
                new FactionTechRemove(),
                new PNAdd(),
                new PNRemove(),
                new UnitAdd(),
                new UnitRemove(),
                new BreakthroughAdd(),
                new BreakthroughRemove(),
                // WARNING: Franken commands are at 25/25 capacity.
                //          Reorganization must happen in order to add more commands
                new StartFrankenDraft(),
                new SetFactionIcon(),
                new SetFactionDisplayName(),
                new FrankenEdit(),
                new ShowFrankenBag(),
                new ShowFrankenHand(),
                new FrankenViewCard(),
                new Ban(),
                new DraftLimits(),
                new BanFaction(),
                new ApplyDraftBags(),
                new SetHomeSystemPosition(),
                new BuildMap());
    }
}
