package ti4.discord.interactions.commands.testbed;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.AbstractParentCommand;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.logging.BotLogger;
import ti4.service.testbed.TestBedService;

public class TestBedCommand extends AbstractParentCommand {

    public TestBedCommand() {
        super(
                "testbed",
                "Developer test bed: virtual players and acting as any seat",
                new TestBedEnable(),
                new TestBedDisable(),
                new TestBedActAs(),
                new TestBedApply(),
                new TestBedReset(),
                new TestBedPanel(),
                new TestBedRun(),
                new TestBedReload());
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!super.accept(event) || !CommandHelper.acceptIfHasRoles(event, JdaService.developerRoles)) {
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
}
