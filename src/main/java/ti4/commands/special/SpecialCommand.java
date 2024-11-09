package ti4.commands.special;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.commands.uncategorized.ShowGame;
import ti4.helpers.Constants;
import ti4.helpers.SlashCommandAcceptanceHelper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.GameSaveLoadManager;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;

public class SpecialCommand implements Command {

    private final Collection<SpecialSubcommandData> subcommandData = getSubcommands();

    @Override
    public String getActionID() {
        return Constants.SPECIAL;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return SlashCommandAcceptanceHelper.shouldAcceptIfActivePlayerOfGame(getActionID(), event);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String subcommandName = event.getInteraction().getSubcommandName();
        SpecialSubcommandData executedCommand = null;
        for (SpecialSubcommandData subcommand : subcommandData) {
            if (Objects.equals(subcommand.getName(), subcommandName)) {
                subcommand.preExecute(event);
                subcommand.execute(event);
                executedCommand = subcommand;
                break;
            }
        }
        if (executedCommand == null) {
            reply(event);
        } else {
            executedCommand.reply(event);
        }
    }

    public static void reply(SlashCommandInteractionEvent event) {
        String userID = event.getUser().getId();
        Game game = GameManager.getUserActiveGame(userID);
        GameSaveLoadManager.saveGame(game, event);
        ShowGame.simpleShowGame(game, event);
    }

    protected String getActionDescription() {
        return "Special";
    }

    private Collection<SpecialSubcommandData> getSubcommands() {
        Collection<SpecialSubcommandData> subcommands = new HashSet<>();
        subcommands.add(new AddFactionCCToFleetSupply());
        subcommands.add(new RemoveFactionCCFromFleetSupply());
        subcommands.add(new DiploSystem());
        subcommands.add(new MakeSecretIntoPO());
        subcommands.add(new AdjustRoundNumber());
        subcommands.add(new SwapTwoSystems());
        subcommands.add(new SearchWarrant());
        subcommands.add(new SleeperToken());
        subcommands.add(new IonFlip());
        subcommands.add(new SystemInfo());
        subcommands.add(new StellarConverter());
        subcommands.add(new RiseOfMessiah());
        subcommands.add(new Rematch());
        subcommands.add(new CloneGame());
        subcommands.add(new SwordsToPlowsharesTGGain());
        subcommands.add(new WormholeResearchFor());
        subcommands.add(new FighterConscription());
        subcommands.add(new SwapSC());
        //subcommands.add(new KeleresHeroMentak());
        subcommands.add(new MoveAllUnits());
        subcommands.add(new NovaSeed());
        subcommands.add(new StasisInfantry());
        subcommands.add(new NaaluCommander());
        subcommands.add(new MoveCreussWormhole());
        subcommands.add(new CheckDistance());
        subcommands.add(new CheckAllDistance());

        return subcommands;
    }

    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        commands.addCommands(
            Commands.slash(getActionID(), getActionDescription())
                .addSubcommands(getSubcommands()));
    }
}
