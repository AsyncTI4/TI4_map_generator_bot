package ti4.commands;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.capture.CaptureCommand;
import ti4.commands.game.GameCommand;
import ti4.commands.map.MapCommand;
import ti4.commands.planet.PlanetCommand;
import ti4.commands.tech.TechCommand;
import ti4.commands.tokens.AddCC;
import ti4.commands.tokens.AddFrontierTokens;
import ti4.commands.tokens.AddToken;
import ti4.commands.tokens.RemoveAllCC;
import ti4.commands.tokens.RemoveCC;
import ti4.commands.tokens.RemoveToken;
import ti4.commands.units.AddUnitDamage;
import ti4.commands.units.AddUnits;
import ti4.commands.units.ModifyUnits;
import ti4.commands.units.MoveUnits;
import ti4.commands.units.RemoveAllUnitDamage;
import ti4.commands.units.RemoveAllUnits;
import ti4.commands.units.RemoveUnitDamage;
import ti4.commands.units.RemoveUnits;
import ti4.commands2.admin.AdminCommand;
import ti4.commands2.agenda.AgendaCommand;
import ti4.commands2.bothelper.BothelperCommand;
import ti4.commands2.button.GenericButtonCommand;
import ti4.commands2.cardsac.ACCardsCommand;
import ti4.commands2.cardspn.PNCardsCommand;
import ti4.commands2.cardsso.SOCardsCommand;
import ti4.commands2.combat.CombatCommand;
import ti4.commands2.custom.CustomCommand;
import ti4.commands2.developer.DeveloperCommand;
import ti4.commands2.ds.DiscordantStarsCommand;
import ti4.commands2.event.EventCommand;
import ti4.commands2.explore.ExploreCommand;
import ti4.commands2.fow.FOWCommand;
import ti4.commands2.franken.FrankenCommand;
import ti4.commands2.help.HelpCommand;
import ti4.commands2.installation.InstallationCommand;
import ti4.commands2.leaders.LeaderCommand;
import ti4.commands2.milty.MiltyCommand;
import ti4.commands2.player.PlayerCommand;
import ti4.commands2.relic.RelicCommand;
import ti4.commands2.search.SearchCommand;
import ti4.commands2.special.SpecialCommand;
import ti4.commands2.statistics.StatisticsCommand;
import ti4.commands2.status.StatusCommand;
import ti4.commands2.tigl.TIGLCommand;
import ti4.commands2.uncategorized.AllInfo;
import ti4.commands2.uncategorized.CardsInfo;
import ti4.commands2.uncategorized.SelectionBoxDemo;
import ti4.commands2.uncategorized.ShowDistances;
import ti4.commands2.uncategorized.ShowGame;
import ti4.commands2.user.UserCommand;

public class CommandManager {

    public static final Map<String, Command> commands = Stream.of(
        new AddUnits(),
        new RemoveUnits(),
        new RemoveAllUnits(),
        new AllInfo(),
        new CardsInfo(),
        new ShowGame(),
        new ShowDistances(),
        new AddCC(),
        new RemoveCC(),
        new RemoveAllCC(),
        new AddFrontierTokens(),
        new MoveUnits(),
        new ModifyUnits(),
        new RemoveToken(),
        new AddToken(),
        new AddUnitDamage(),
        new RemoveUnitDamage(),
        new RemoveAllUnitDamage(),

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
        new SelectionBoxDemo(),
        new UserCommand(),
        new TIGLCommand()
    ).collect(Collectors.toMap(Command::getName, command -> command));

    public static Command getCommand(String name) {
        return commands.get(name);
    }

    public static Collection<Command> getCommands() {
        return commands.values();
    }
}
