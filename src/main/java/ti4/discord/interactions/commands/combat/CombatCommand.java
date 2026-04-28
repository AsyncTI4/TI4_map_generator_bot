package ti4.discord.interactions.commands.combat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import ti4.contest.replay.core.CombatContestSettings;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.spring.context.SpringContext;

public class CombatCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = buildSubcommands();

    private Map<String, Subcommand> buildSubcommands() {
        List<Subcommand> commands = new ArrayList<>(List.of(new CombatRoll(), new StartCombat()));
        if (isReplayDebugEnabled()) {
            commands.add(new CombatReplayDebugPanel());
        }
        return commands.stream().collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));
    }

    private boolean isReplayDebugEnabled() {
        return SpringContext.getBean(CombatContestSettings.class).getRuntime().isDevMode();
    }

    @Override
    public String getName() {
        return Constants.COMBAT;
    }

    @Override
    public String getDescription() {
        return "Combat";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
