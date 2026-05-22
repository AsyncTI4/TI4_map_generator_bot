package ti4.discord.interactions.commands.developer;

import java.util.HashSet;
import java.util.Set;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.commands.Subcommand;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.GameStats.ActionCardPlay;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.game.persistence.GameManager;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

class RunAgainstAllGames extends Subcommand {

    RunAgainstAllGames() {
        super("run_against_all_games", "Runs this custom code against all games.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Running custom command against all games.");

        Set<String> changedGames = new HashSet<>();
        ConsumeGameUtility.consumeAllGames(
                game -> {
                    boolean changed = makeChanges(game);
                    if (changed) {
                        changedGames.add(game.getName());
                        GameManager.save(
                                game, "Developer ran custom command against this game, probably migration related.");
                    }
                },
                ExecutionLockType.WRITE);

        MessageHelper.sendMessageToChannel(event.getChannel(), "Finished custom command against all games.");
        BotLogger.info("Changes made to " + changedGames.size() + " games out of " + GameManager.getGameCount()
                + " games: " + String.join(", ", changedGames));
    }

    private static boolean makeChanges(Game game) {
        boolean changed = false;
        for (ActionCardPlay play : game.getGameStats().getActionCardPlays()) {
            if ("Sabotage".equals(play.getActionCard())) {
                play.setActionCard(GameStats.SABOTAGE);
                changed = true;
            } else if ("Overrule".equals(play.getActionCard())) {
                play.setActionCard(GameStats.OVERRULE);
                changed = true;
            }
        }
        changed |= game.getGameStats()
                .getActionCardPlays()
                .removeIf(play -> !Mapper.isValidActionCard(play.getActionCard()));
        return changed;
    }
}
