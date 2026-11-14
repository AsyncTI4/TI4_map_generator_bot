package ti4.discord.interactions.commands.tournament;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.Subcommand;
import ti4.logging.BotLogger;
import ti4.service.tournament.TournamentAccess;
import ti4.spring.service.tournament.TournamentService;

public class TournamentCommand implements ParentCommand {

    public static final String NAME = "tournament";

    private final Map<String, Subcommand> subcommands = Stream.<Subcommand>of(
                    new TournamentCreate(),
                    new TournamentPostSignup(),
                    new TournamentPostInvite(),
                    new TournamentShowPanel(),
                    new TournamentRoles(),
                    new TournamentList())
            .collect(Collectors.toMap(Subcommand::getName, Function.identity(), (a, b) -> a, LinkedHashMap::new));

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!ParentCommand.super.accept(event)) return false;
        String refusal = null;
        if (TournamentService.isUnavailable()) refusal = TournamentService.UNAVAILABLE;
        else if (!TournamentAccess.isTournamentAdmin(event.getMember())) refusal = TournamentAccess.DENIED;
        if (refusal == null) return true;
        event.getHook().editOriginal(refusal).queue(Consumers.nop(), BotLogger::catchRestError);
        return false;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "Tournament sign-ups, groups and bulk game creation (Admin / Tourney Mod)";
    }

    @Override
    public Map<String, Subcommand> getSubcommands() {
        return subcommands;
    }
}
