package ti4.commands.franken;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.ParentCommand;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;

public class FrankenCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
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
        new StartFrankenDraft(),
        new SetFactionIcon(),
        new SetFactionDisplayName(),
        new FrankenEdit(),
        new ShowFrankenBag(),
        new ShowFrankenHand(),
        new FrankenViewCard(),
        new BanAbility(),
        new BanFaction(),
        new ApplyDraftBags(),
        new SetHomeSystemPosition()).collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return Constants.FRANKEN;
    }

    @Override
    public String getDescription() {
        return "Franken";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
