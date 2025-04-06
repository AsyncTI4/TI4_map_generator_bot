package ti4.commands;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.admin.AdminCommand;
import ti4.commands.agenda.AgendaCommand;
import ti4.commands.bothelper.BothelperCommand;
import ti4.commands.button.GenericButtonCommand;
import ti4.commands.cardsac.ACCardsCommand;
import ti4.commands.cardspn.PNCardsCommand;
import ti4.commands.cardsso.SOCardsCommand;
import ti4.commands.combat.CombatCommand;
import ti4.commands.custom.CustomCommand;
import ti4.commands.developer.DeveloperCommand;
import ti4.commands.ds.DiscordantStarsCommand;
import ti4.commands.event.EventCommand;
import ti4.commands.explore.ExploreCommand;
import ti4.commands.fow.FOWCommand;
import ti4.commands.franken.FrankenCommand;
import ti4.commands.game.GameCommand;
import ti4.commands.help.HelpCommand;
import ti4.commands.installation.InstallationCommand;
import ti4.commands.leaders.LeaderCommand;
import ti4.commands.map.MapCommand;
import ti4.commands.milty.MiltyCommand;
import ti4.commands.omegaphase.OmegaPhaseCommand;
import ti4.commands.planet.PlanetCommand;
import ti4.commands.player.PlayerCommand;
import ti4.commands.relic.RelicCommand;
import ti4.commands.search.SearchCommand;
import ti4.commands.special.Special2Command;
import ti4.commands.special.SpecialCommand;
import ti4.commands.statistics.StatisticsCommand;
import ti4.commands.status.StatusCommand;
import ti4.commands.tech.TechCommand;
import ti4.commands.tigl.TIGLCommand;
import ti4.commands.tokens.AddCCCommand;
import ti4.commands.tokens.AddFrontierTokensCommand;
import ti4.commands.tokens.AddTokenCommand;
import ti4.commands.tokens.RemoveAllCC;
import ti4.commands.tokens.RemoveCCCommand;
import ti4.commands.tokens.RemoveTokenCommand;
import ti4.commands.transaction.Transaction;
import ti4.commands.uncategorized.AllInfoCommand;
import ti4.commands.uncategorized.CardsInfoCommand;
import ti4.commands.uncategorized.SelectionBoxDemoCommand;
import ti4.commands.uncategorized.ShowDistancesCommand;
import ti4.commands.uncategorized.ShowGameCommand;
import ti4.commands.units.AddUnitDamage;
import ti4.commands.units.AddUnits;
import ti4.commands.units.CaptureCommand;
import ti4.commands.units.ModifyUnitsButtons;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveAllUnitDamage;
import ti4.commands.units.RemoveAllUnits;
import ti4.commands.units.RemoveUnitDamage;
import ti4.commands.units.RemoveUnits;
import ti4.commands.user.UserCommand;

public class CommandManager {

    public static final Map<String, ParentCommand> commands = Stream.of(
        new AddUnits(),
        new RemoveUnits(),
        new RemoveAllUnits(),
        new AllInfoCommand(),
        new CardsInfoCommand(),
        new ShowGameCommand(),
        new ShowDistancesCommand(),
        new AddCCCommand(),
        new RemoveCCCommand(),
        new RemoveAllCC(),
        new AddFrontierTokensCommand(),
        new MoveUnits(),
        new ModifyUnitsButtons(),
        new RemoveTokenCommand(),
        new AddTokenCommand(),
        new AddUnitDamage(),
        new RemoveUnitDamage(),
        new RemoveAllUnitDamage(),
        new Transaction(),

        new MapCommand(),
        new HelpCommand(),
        new SearchCommand(),
        new ExploreCommand(),
        new RelicCommand(),

        new AdminCommand(),
        new DeveloperCommand(),
        new BothelperCommand(),
        new PlayerCommand(),
        new GameCommand(),

        new ACCardsCommand(),
        new PNCardsCommand(),
        new SOCardsCommand(),
        new StatusCommand(),
        new AgendaCommand(),
        new EventCommand(),

        new SpecialCommand(),
        new Special2Command(),
        new LeaderCommand(),
        new CombatCommand(),
        new CustomCommand(),
        new FOWCommand(),
        new InstallationCommand(),
        new MiltyCommand(),
        new FrankenCommand(),
        new CaptureCommand(),
        new GenericButtonCommand(),
        new DiscordantStarsCommand(),
        new StatisticsCommand(),
        new TechCommand(),
        new PlanetCommand(),
        new SelectionBoxDemoCommand(),
        new UserCommand(),
        new TIGLCommand(),
        new OmegaPhaseCommand()).collect(Collectors.toMap(ParentCommand::getName, command -> command));

    public static ParentCommand getCommand(String name) {
        return commands.get(name);
    }

    public static Collection<ParentCommand> getCommands() {
        return commands.values();
    }
}
