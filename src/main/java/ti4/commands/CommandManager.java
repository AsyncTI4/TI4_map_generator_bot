package ti4.commands;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ti4.commands.units.AddUnitDamage;
import ti4.commands.units.AddUnits;
import ti4.commands.units.CaptureCommand;
import ti4.commands.units.ModifyUnitsButtons;
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
import ti4.commands2.game.GameCommand;
import ti4.commands2.help.HelpCommand;
import ti4.commands2.installation.InstallationCommand;
import ti4.commands2.leaders.LeaderCommand;
import ti4.commands2.map.MapCommand;
import ti4.commands2.milty.MiltyCommand;
import ti4.commands2.planet.PlanetCommand;
import ti4.commands2.player.PlayerCommand;
import ti4.commands2.relic.RelicCommand;
import ti4.commands2.search.SearchCommand;
import ti4.commands2.special.SpecialCommand;
import ti4.commands2.statistics.StatisticsCommand;
import ti4.commands2.status.StatusCommand;
import ti4.commands2.tech.TechCommand;
import ti4.commands2.tigl.TIGLCommand;
import ti4.commands2.tokens.AddCCCommand;
import ti4.commands2.tokens.AddFrontierTokensCommand;
import ti4.commands2.tokens.AddTokenCommand;
import ti4.commands2.tokens.RemoveAllCC;
import ti4.commands2.tokens.RemoveCCCommand;
import ti4.commands2.tokens.RemoveTokenCommand;
import ti4.commands2.uncategorized.AllInfoCommand;
import ti4.commands2.uncategorized.CardsInfoCommand;
import ti4.commands2.uncategorized.SelectionBoxDemoCommand;
import ti4.commands2.uncategorized.ShowDistancesCommand;
import ti4.commands2.uncategorized.ShowGameCommand;
import ti4.commands2.user.UserCommand;

public class CommandManager {

    public static final Map<String, Command> commands = Stream.of(
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
        new SelectionBoxDemoCommand(),
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
