package ti4.discord.interactions.listeners;

import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.lang3.function.Consumers;
import ti4.contest.replay.service.CombatReplayService;
import ti4.discord.interactions.commands.Command;
import ti4.discord.interactions.commands.GameStateContainer;
import ti4.discord.interactions.commands.ParentCommand;
import ti4.discord.interactions.commands.SlashCommandManager;
import ti4.executors.ExecutionLockType;
import ti4.executors.ExecutorServiceManager;
import ti4.helpers.Constants;
import ti4.logging.BotLogger;
import ti4.logging.RollbarManager;
import ti4.service.SusSlashCommandService;
import ti4.service.game.GameNameService;
import ti4.spring.context.SpringContext;

class SlashCommandListener extends ListenerAdapter implements CommandListener {

    private static final List<String> SLASHCOMMANDS_WITH_MODALS = Arrays.asList(
            Constants.ADD_TILE_LIST,
            Constants.ADD_TILE_LIST_RANDOM,
            Constants.EDIT_TRACK_RECORD,
            Constants.IMPORT_MAP_JSON);

    @Override
    public void onSlashCommandInteraction(@Nonnull SlashCommandInteractionEvent event) {
        if (!canReceiveCommands(event)) return;

        if (!isModalCommand(event)) {
            Command<SlashCommandInteractionEvent> command = getCommand(event);
            event.getInteraction()
                    .deferReply(command.isEphemeral(event))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        }

        queue(event);
    }

    private void queue(SlashCommandInteractionEvent event) {
        Command<SlashCommandInteractionEvent> command = getCommand(event);
        ExecutionLockType lockType = getLockType(command);
        String eventString = eventToString(event);
        if (lockType == null) {
            ExecutorServiceManager.runAsync(eventString, () -> process(event));
            return;
        }
        String gameName = GameNameService.getGameName(event);
        ExecutorServiceManager.runAsyncWithLock(
                eventString, gameName, event.getMessageChannel(), () -> process(event), lockType);
    }

    public String eventToString(GenericCommandInteractionEvent event) {
        String gameName = GameNameService.getGameName(event);
        return "SlashCommandListener task for `" + event.getUser().getEffectiveName() + "`"
                + (gameName == null ? "" : " in `" + gameName + "`")
                + ": `"
                + event.getCommandString() + "`";
    }

    private void process(SlashCommandInteractionEvent event) {
        long processStartTime = System.currentTimeMillis();
        RollbarManager.putInteractionMetadata("slash_command", event);
        RollbarManager.put("command_name", event.getCommandString());
        RollbarManager.put("game_name", GameNameService.getGameName(event));

        ParentCommand command = SlashCommandManager.getCommand(event.getName());
        Command<SlashCommandInteractionEvent> resolvedCommand = getCommand(event);
        CombatReplayService combatReplayService = SpringContext.getBean(CombatReplayService.class);
        try {
            if (command.accept(event)) {
                command.preExecute(event);
                if (resolvedCommand instanceof GameStateContainer gameStateContainer) {
                    combatReplayService.setPreInteractionSnapshot(
                            combatReplayService.capturePreInteractionSnapshot(gameStateContainer.getGame()));
                }
                logSlashCommand(event);
                command.execute(event);
                command.postExecute(event);
                if (!isModalCommand(event) && !resolvedCommand.isEphemeral(event)) {
                    event.getHook().deleteOriginal().queue(Consumers.nop(), BotLogger::catchRestError);
                }
            }
        } catch (Exception e) {
            command.onException(event, e);
        } finally {
            combatReplayService.clearPreInteractionSnapshot();
            RollbarManager.clear();
        }

        warnForLongRunningCommands(event, processStartTime);
    }

    private static boolean isModalCommand(SlashCommandInteractionEvent event) {
        return SLASHCOMMANDS_WITH_MODALS.contains(event.getInteraction().getSubcommandName());
    }

    private static void logSlashCommand(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) return;

        var command = SlashCommandManager.getCommand(event.getInteraction().getName());
        String susPrefix = command.isSuspicious(event) ? "sus" : "notSus";
        String commandText =
                "```" + susPrefix + "\n" + member.getEffectiveName() + " used " + event.getCommandString() + "\n```";
        if (!event.getCommandString().contains("/rules ask")
                && !event.getCommandString().contains("/fow whisper")) {
            event.getChannel()
                    .sendMessage(commandText)
                    .queue(
                            m -> {
                                BotLogger.logSlashCommand(event, m);
                                SusSlashCommandService.checkIfShouldReportSusSlashCommand(event, m.getJumpUrl());
                            },
                            BotLogger::catchRestError);
        }
    }

    private static ExecutionLockType getLockType(Command<SlashCommandInteractionEvent> command) {
        if (command instanceof GameStateContainer gameStateContainer) {
            return gameStateContainer.isSaveGame() ? ExecutionLockType.WRITE : ExecutionLockType.READ;
        }
        return null;
    }

    private static Command<SlashCommandInteractionEvent> getCommand(SlashCommandInteractionEvent event) {
        ParentCommand command = SlashCommandManager.getCommand(event.getName());
        Command<SlashCommandInteractionEvent> subcommand = command.getSubcommand(event);
        return subcommand == null ? command : subcommand;
    }
}
