package ti4.discord.interactions.commands.testbed;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.logging.BotLogger;
import ti4.service.testbed.TestBedService;

public class TestBedCommand implements ParentCommand {

    private final Map<String, Subcommand> subcommands = Stream.of(
                    new TestBedEnable(),
                    new TestBedDisable(),
                    new TestBedActAs(),
                    new TestBedApply(),
                    new TestBedReset(),
                    new TestBedPanel(),
                    new TestBedRun(),
                    new TestBedReload())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    @Override
    public String getName() {
        return "testbed";
    }

    @Override
    public String getDescription() {
        return "Developer test bed: virtual players and acting as any seat";
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!ParentCommand.super.accept(event) || !CommandHelper.acceptIfHasRoles(event, JdaService.developerRoles)) {
            return false;
        }
        if (!TestBedService.isEnabled()) {
            event.getHook()
                    .editOriginal("The test bed is disabled on this bot. A developer can enable it with "
                            + "`/developer setting setting_name:testbed_enabled setting_value:true setting_type:bool`.")
                    .queue(Consumers.nop(), BotLogger::catchRestError);
            return false;
        }
        return true;
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
