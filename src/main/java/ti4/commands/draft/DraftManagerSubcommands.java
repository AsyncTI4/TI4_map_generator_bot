package ti4.commands.draft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands.GameStateSubcommand;
import ti4.commands.Subcommand;
import ti4.commands.SubcommandGroup;
import ti4.helpers.Constants;
import ti4.helpers.settingsFramework.menus.MiltySettings;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.MapTemplateModel;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftComponentFactory;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftManager.CommandSource;
import ti4.service.draft.DraftOrchestrator;
import ti4.service.draft.DraftSaveService;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.draftables.FactionDraftable;
import ti4.service.draft.draftables.SliceDraftable;
import ti4.service.draft.draftables.SpeakerOrderDraftable;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;
import ti4.service.milty.MiltyDraftManager;
import ti4.service.milty.MiltyDraftSpec;
import ti4.service.milty.MiltyService;

public class DraftManagerSubcommands extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new DraftManagerDebug(),
                    new DraftManagerValidate(),
                    new DraftManagerStartDraft(),
                    new DraftManagerCanEndDraft(),
                    new DraftManagerTryEndDraft(),
                    new DraftManagerPostDraftWork(),
                    new DraftManagerCanSetupPlayers(),
                    new DraftManagerSetupPlayers(),
                    new DraftManagerAddDraftable(),
                    new DraftManagerAddOrchestrator(),
                    new DraftManagerAddPlayer(),
                    new DraftManagerRemovePlayer(),
                    new DraftManagerListPlayers(),
                    new DraftManagerClearMissingPlayers(),
                    new DraftManagerAddAllGamePlayers(),
                    new DraftManagerListDraftables(),
                    new DraftManagerGetOrchestrator(),
                    new DraftManagerSwapDraftingPlayers(),
                    new DraftManagerReplacePlayer(),
                    new DraftManagerMakePick(),
                    new DraftManagerSendCustomDraftableCommand(),
                    new DraftManagerSendCustomOrchestratorCommand(),
                    new DraftManagerConvertToMilty())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    protected DraftManagerSubcommands() {
        super(Constants.DRAFT_MANAGE, "Commands for managing an active draft");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }

    public static class DraftManagerDebug extends GameStateSubcommand {

        public DraftManagerDebug() {
            super(
                    Constants.DRAFT_MANAGE_DEBUG,
                    "Print the raw draft state. WARNING: Can print secret info.",
                    false,
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String saveData = DraftSaveService.saveDraftManager(draftManager);
            StringBuilder sb = new StringBuilder();
            for (String saveLine : DraftSaveService.splitLines(saveData)) {
                if (saveLine.startsWith(DraftSaveService.PLAYER_DATA + DraftSaveService.KEY_SEPARATOR)) {
                    saveLine = saveLine.replaceFirst(
                            DraftSaveService.PLAYER_DATA + DraftSaveService.KEY_SEPARATOR,
                            "Player UserIDs (w/ short codes): ");
                }
                if (saveLine.startsWith(DraftSaveService.DRAFTABLE_DATA + DraftSaveService.KEY_SEPARATOR)) {
                    saveLine = saveLine.replaceFirst(
                            DraftSaveService.DRAFTABLE_DATA + DraftSaveService.KEY_SEPARATOR,
                            "Draftable things w/ state data: ");
                }
                if (saveLine.startsWith(DraftSaveService.ORCHESTRATOR_DATA + DraftSaveService.KEY_SEPARATOR)) {
                    saveLine = saveLine.replaceFirst(
                            DraftSaveService.ORCHESTRATOR_DATA + DraftSaveService.KEY_SEPARATOR,
                            "Orchestrator w/ state data: ");
                }
                if (saveLine.startsWith(DraftSaveService.PLAYER_PICK_DATA + DraftSaveService.KEY_SEPARATOR)) {
                    saveLine = saveLine.replaceFirst(
                            DraftSaveService.PLAYER_PICK_DATA + DraftSaveService.KEY_SEPARATOR, "Player pick: ");
                }
                if (saveLine.startsWith(
                        DraftSaveService.PLAYER_ORCHESTRATOR_STATE_DATA + DraftSaveService.KEY_SEPARATOR)) {
                    saveLine = saveLine.replaceFirst(
                            DraftSaveService.PLAYER_ORCHESTRATOR_STATE_DATA + DraftSaveService.KEY_SEPARATOR,
                            "Orchestrator-specific player state: ");
                }
                sb.append(saveLine);
                sb.append(System.lineSeparator());
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
            draftManager.validateState();
        }
    }

    public static class DraftManagerValidate extends GameStateSubcommand {

        public DraftManagerValidate() {
            super(Constants.DRAFT_MANAGE_VALIDATE, "Get any reason the draft is in a bad state", false, false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            draftManager.validateState();
        }
    }

    public static class DraftManagerStartDraft extends GameStateSubcommand {

        public DraftManagerStartDraft() {
            super(
                    Constants.DRAFT_MANAGE_START,
                    "Start the draft; if you're having issues, try '/draft manage validate'",
                    true,
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            draftManager.tryStartDraft();
        }
    }

    public static class DraftManagerCanEndDraft extends GameStateSubcommand {

        public DraftManagerCanEndDraft() {
            super(Constants.DRAFT_MANAGE_CAN_END, "Check if the draft can be ended now", false, false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String reason = draftManager.whatsStoppingDraftEnd();
            if (reason == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Nothing! Ergo, the draft has ended.");
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), reason);
            }
        }
    }

    public static class DraftManagerTryEndDraft extends GameStateSubcommand {

        public DraftManagerTryEndDraft() {
            super(Constants.DRAFT_MANAGE_END, "Try to end the draft", true, false);
            addOption(
                    OptionType.BOOLEAN,
                    Constants.FORCE_OPTION,
                    "Attempt to ignore any blocking reason and end anyway",
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            boolean force = event.getOption(Constants.FORCE_OPTION, false, o -> o.getAsBoolean());
            if (force) {
                draftManager.endDraft(event);
            } else {
                draftManager.tryEndDraft(event);
            }
        }
    }

    public static class DraftManagerPostDraftWork extends GameStateSubcommand {

        public DraftManagerPostDraftWork() {
            super(
                    Constants.DRAFT_MANAGE_POST_DRAFT_WORK,
                    "Have the draft components do (or redo) the post-draft work",
                    true,
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            draftManager.getOrchestrator().onDraftEnd(draftManager);
            for (Draftable draftable : draftManager.getDraftables()) {
                draftable.onDraftEnd(draftManager);
            }
        }
    }

    public static class DraftManagerCanSetupPlayers extends GameStateSubcommand {

        public DraftManagerCanSetupPlayers() {
            super(
                    Constants.DRAFT_MANAGE_CAN_SETUP_PLAYERS,
                    "Check if the draft is ready for player setup",
                    false,
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String reason = draftManager.whatsStoppingSetup();
            if (reason == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "The draft should have set up players already.");
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), reason);
            }
        }
    }

    public static class DraftManagerSetupPlayers extends GameStateSubcommand {

        public DraftManagerSetupPlayers() {
            super(Constants.DRAFT_MANAGE_SETUP_PLAYERS, "Have the draft elements set up players", true, false);
            addOption(
                    OptionType.BOOLEAN,
                    Constants.FORCE_OPTION,
                    "Attempt to ignore any blocking reason and setup anyway",
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            boolean force = event.getOption(Constants.FORCE_OPTION, false, o -> o.getAsBoolean());
            if (force) {
                draftManager.setupPlayers(event);
            } else {
                draftManager.trySetupPlayers(event);
            }
        }
    }

    public static class DraftManagerAddDraftable extends GameStateSubcommand {

        public DraftManagerAddDraftable() {
            super(Constants.DRAFT_MANAGE_ADD_DRAFTABLE, "Add a draftable to the draft manager", true, false);
            addOption(OptionType.STRING, Constants.ADD_DRAFTABLE_OPTION, "Type of draftable to add", true, true);
            addOption(
                    OptionType.STRING,
                    Constants.SAVE_DATA_OPTION,
                    "Optional line of game save data to load into this draftable",
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String draftableType =
                    event.getOption(Constants.ADD_DRAFTABLE_OPTION, o -> o.getAsString());
            Draftable draftable = DraftComponentFactory.createDraftable(draftableType);
            if (draftable == null) {
                draftable = DraftComponentFactory.createDraftable(draftableType + "Draftable");
            }

            if (draftable == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown draftable type: " + draftableType);
                return;
            }
            draftManager.addDraftable(draftable);
            String saveData = event.getOption(Constants.SAVE_DATA_OPTION, null, o -> o.getAsString());
            if (saveData != null) {
                draftable.load(saveData);
                draftable.validateState(draftManager);
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Added and loaded draftable of type: " + draftableType);
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Added draftable of type: " + draftableType + "; be sure to configure it!");
            }
        }
    }

    public static class DraftManagerAddOrchestrator extends GameStateSubcommand {

        public DraftManagerAddOrchestrator() {
            super(Constants.DRAFT_MANAGE_SET_ORCHESTRATOR, "Set the orchestrator for the draft manager", true, false);
            addOption(OptionType.STRING, Constants.SET_ORCHESTRATOR_OPTION, "Type of orchestrator to use", true, true);
            addOption(
                    OptionType.STRING,
                    Constants.SAVE_DATA_OPTION,
                    "Optional line of game save data to load into this orchestrator",
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String orchestratorType =
                    event.getOption(Constants.SET_ORCHESTRATOR_OPTION, o -> o.getAsString());
            DraftOrchestrator orchestrator = DraftComponentFactory.createOrchestrator(orchestratorType);
            if (orchestrator == null) {
                orchestrator = DraftComponentFactory.createOrchestrator(orchestratorType + "Orchestrator");
            }
            if (orchestrator == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Unknown orchestrator type: " + orchestratorType);
                return;
            }
            draftManager.setOrchestrator(orchestrator);
            String saveData = event.getOption(Constants.SAVE_DATA_OPTION, null, o -> o.getAsString());
            if (saveData != null) {
                orchestrator.load(saveData);
                orchestrator.validateState(draftManager);
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Set and loaded orchestrator of type: " + orchestratorType);
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Set orchestrator of type: " + orchestratorType + "; be sure to configure it!");
            }
        }
    }

    public static class DraftManagerAddPlayer extends GameStateSubcommand {
        public DraftManagerAddPlayer() {
            super(Constants.DRAFT_MANAGE_ADD_PLAYER, "Add player to the draft", true, true);
            addOption(OptionType.USER, Constants.PLAYER, "Player to add", true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String playerUserId = getPlayer().getUserID();
            try {
                draftManager.addPlayer(playerUserId);
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Added player to draft: " + getPlayer().getPing());
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Could not add player to draft: " + e.getMessage());
            }
        }
    }

    // NOTE: Its possible they're not in the Game anymore
    public static class DraftManagerRemovePlayer extends GameStateSubcommand {
        public DraftManagerRemovePlayer() {
            super(Constants.DRAFT_MANAGE_REMOVE_PLAYER, "Remove player from the draft", true, false);
            addOption(OptionType.USER, Constants.PLAYER, "Player to remove", false);
            addOption(
                    OptionType.STRING,
                    Constants.UNKNOWN_DRAFT_USER_ID_OPTION,
                    "Player to remove (if not in game)",
                    false,
                    true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String playerUserId;
            if (event.getOption(Constants.PLAYER) != null) {
                playerUserId = event.getOption(Constants.PLAYER).getAsUser().getId();
            } else if (event.getOption(Constants.UNKNOWN_DRAFT_USER_ID_OPTION) != null) {
                playerUserId =
                        event.getOption(Constants.UNKNOWN_DRAFT_USER_ID_OPTION).getAsString();
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must provide a player to remove");
                return;
            }
            try {
                draftManager.removePlayer(playerUserId);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Removed player from draft: " + playerUserId);
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Could not remove player from draft: " + e.getMessage());
            }
        }
    }

    public static class DraftManagerListPlayers extends GameStateSubcommand {
        public DraftManagerListPlayers() {
            super(Constants.DRAFT_MANAGE_LIST_PLAYERS, "List players in the draft", false, false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            StringBuilder sb = new StringBuilder();
            sb.append("Players in draft:\n");
            for (String playerUserId : draftManager.getPlayerUserIds()) {
                sb.append("- ");
                if (game.getPlayer(playerUserId) != null) {
                    sb.append(game.getPlayer(playerUserId).getPing());
                } else {
                    sb.append(playerUserId + " (not in game)");
                }

                sb.append(System.lineSeparator());
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        }
    }

    public static class DraftManagerClearMissingPlayers extends GameStateSubcommand {
        public DraftManagerClearMissingPlayers() {
            super(
                    Constants.DRAFT_MANAGE_CLEAR_MISSING_PLAYERS,
                    "Remove all players that aren't in the game from the draft",
                    true,
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            List<String> removed = new ArrayList<>();
            for (String playerUserId : draftManager.getPlayerUserIds()) {
                if (game.getPlayer(playerUserId) == null) {
                    removed.add(playerUserId);
                    draftManager.removePlayer(playerUserId);
                }
            }
            if (removed.isEmpty()) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "All drafting players are in the current game.");
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Cleared all missing players from the draft: " + String.join(", ", removed));
            }
        }
    }

    public static class DraftManagerAddAllGamePlayers extends GameStateSubcommand {
        public DraftManagerAddAllGamePlayers() {
            super(
                    Constants.DRAFT_MANAGE_ADD_ALL_GAME_PLAYERS,
                    "Add all players in the game to the draft (if they aren't already)",
                    true,
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            List<String> added = new ArrayList<>();
            for (Player player : game.getPlayers().values()) {
                if (player.isDummy() || player.isNpc()) {
                    continue;
                }
                if (!draftManager.getPlayerStates().containsKey(player.getUserID())) {
                    draftManager.addPlayer(player.getUserID());
                    added.add(player.getPing());
                }
            }
            if (added.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "All players in the game are already in the draft.");
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Added all missing players from the game to the draft: " + String.join(", ", added));
            }
        }
    }

    public static class DraftManagerSwapDraftingPlayers extends GameStateSubcommand {
        public DraftManagerSwapDraftingPlayers() {
            super(Constants.DRAFT_MANAGE_SWAP_PLAYERS, "Swap two players that are in the draft", true, false);
            addOption(OptionType.USER, Constants.PLAYER1, "First player to swap", true);
            addOption(OptionType.USER, Constants.PLAYER2, "Second player to swap", true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String playerUserId1 = event.getOption(Constants.PLAYER1).getAsUser().getId();
            String playerUserId2 = event.getOption(Constants.PLAYER2).getAsUser().getId();
            try {
                draftManager.swapPlayers(playerUserId1, playerUserId2);
                if (draftManager.getOrchestrator() != null) {
                    draftManager.getOrchestrator().sendDraftButtons(draftManager);
                }
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Swapped players in draft: "
                                + game.getPlayer(playerUserId1).getPing() + " and "
                                + game.getPlayer(playerUserId2).getPing());
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Could not swap players in draft: " + e.getMessage());
            }
        }
    }

    public static class DraftManagerReplacePlayer extends GameStateSubcommand {
        public DraftManagerReplacePlayer() {
            super(
                    Constants.DRAFT_MANAGE_REPLACE_PLAYER,
                    "Replace one player in the draft with another player that's not currently drafting",
                    true,
                    false);
            addOption(OptionType.USER, Constants.PLAYER2, "Player to add to the draft", true);
            addOption(OptionType.USER, Constants.PLAYER1, "Player to remove from the draft", false);
            addOption(
                    OptionType.STRING,
                    Constants.UNKNOWN_DRAFT_USER_ID_OPTION,
                    "The user ID of the player to remove, if they're not in the game",
                    false,
                    true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String oldPlayerUserId;
            if (event.getOption(Constants.PLAYER1) != null) {
                oldPlayerUserId = event.getOption(Constants.PLAYER1).getAsUser().getId();
            } else if (event.getOption(Constants.UNKNOWN_DRAFT_USER_ID_OPTION) != null) {
                oldPlayerUserId =
                        event.getOption(Constants.UNKNOWN_DRAFT_USER_ID_OPTION).getAsString();
            } else {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Must provide a player to remove");
                return;
            }
            String newPlayerUserId = event.getOption(Constants.PLAYER2).getAsUser().getId();
            try {
                draftManager.replacePlayer(oldPlayerUserId, newPlayerUserId);
                if (draftManager.getOrchestrator() != null) {
                    draftManager.getOrchestrator().sendDraftButtons(draftManager);
                }
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Replaced player in draft: " + oldPlayerUserId + " with "
                                + game.getPlayer(newPlayerUserId).getPing());
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Could not replace player in draft: " + e.getMessage());
            }
        }
    }

    public static class DraftManagerListDraftables extends GameStateSubcommand {
        public DraftManagerListDraftables() {
            super(Constants.DRAFT_MANAGE_LIST_DRAFTABLES, "List the draftables in the draft manager", false, false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            StringBuilder sb = new StringBuilder();
            sb.append("Draftables in draft manager (in order):\n");
            int index = 1;
            for (Draftable draftable : draftManager.getDraftables()) {
                sb.append(index++);
                sb.append(". ");
                sb.append(draftable.getDisplayName());
                sb.append(System.lineSeparator());
            }
            MessageHelper.sendMessageToChannel(event.getChannel(), sb.toString());
        }
    }

    public static class DraftManagerGetOrchestrator extends GameStateSubcommand {
        public DraftManagerGetOrchestrator() {
            super(
                    Constants.DRAFT_MANAGE_DISPLAY_ORCHESTRATOR,
                    "Display the orchestrator in the draft manager",
                    false,
                    false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            DraftOrchestrator orchestrator = draftManager.getOrchestrator();
            if (orchestrator == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "No orchestrator set in the draft manager.");
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Orchestrator in draft manager: "
                                + orchestrator.getClass().getSimpleName());
            }
        }
    }

    public static class DraftManagerMakePick extends GameStateSubcommand {
        public DraftManagerMakePick() {
            super(Constants.DRAFT_MANAGE_MAKE_PICK, "Make a pick for a player in the draft", true, false);
            addOption(OptionType.USER, Constants.PLAYER, "Player to make the pick for", true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFTABLE_TYPE_OPTION,
                    "Type of draftable to make the pick from",
                    true,
                    true);
            addOption(
                    OptionType.STRING, Constants.DRAFTABLE_CHOICE_KEY_OPTION, "Key of the choice to make", true, true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String playerUserId = event.getOption(Constants.PLAYER).getAsString();
            DraftableType draftableType = DraftableType.of(
                    event.getOption(Constants.DRAFTABLE_TYPE_OPTION).getAsString());
            String choiceKey =
                    event.getOption(Constants.DRAFTABLE_CHOICE_KEY_OPTION).getAsString();
            Draftable draftable = draftManager.getDraftable(draftableType);
            if (draftable == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "No draftable of type: " + draftableType);
                return;
            }
            try {
                DraftChoice choice = draftable.getDraftChoice(choiceKey);
                if (choice == null) {
                    MessageHelper.sendMessageToChannel(
                            event.getChannel(),
                            "No choice with key: " + choiceKey + " in draftable of type: " + draftableType);
                    return;
                }
                String outcome = draftManager.routeCommand(
                        event,
                        game.getPlayer(playerUserId),
                        draftable.makeCommandKey(choiceKey),
                        CommandSource.SLASH_COMMAND);
                if (DraftButtonService.isError(outcome)) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Could not make pick: " + outcome);
                    return;
                }
                // TODO: Handle magic strings from routeCommand, such as "delete button"
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not make pick: " + e.getMessage());
            }
        }
    }

    public static class DraftManagerSendCustomDraftableCommand extends GameStateSubcommand {
        public DraftManagerSendCustomDraftableCommand() {
            super(
                    Constants.DRAFT_MANAGE_CUSTOM_COMMAND,
                    "Send a custom command to a Draftable Type in the draft",
                    true,
                    false);
            addOption(OptionType.USER, Constants.PLAYER, "Player sending the command", true);
            addOption(
                    OptionType.STRING,
                    Constants.DRAFTABLE_TYPE_OPTION,
                    "Type of draftable to receive the command",
                    true,
                    true);
            addOption(OptionType.STRING, Constants.DRAFT_COMMAND_OPTION, "Key of the command", true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String playerUserId = event.getOption(Constants.PLAYER).getAsUser().getId();
            DraftableType draftableType = DraftableType.of(
                    event.getOption(Constants.DRAFTABLE_TYPE_OPTION).getAsString());
            String commandKey = event.getOption(Constants.DRAFT_COMMAND_OPTION).getAsString();
            Draftable draftable = draftManager.getDraftable(draftableType);
            if (draftable == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "No draftable of type: " + draftableType);
                return;
            }
            try {
                String outcome = draftManager.routeCommand(
                        event,
                        game.getPlayer(playerUserId),
                        draftable.makeCommandKey(commandKey),
                        CommandSource.SLASH_COMMAND);
                if (DraftButtonService.isError(outcome)) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Could not deliver command: " + outcome);
                    return;
                }
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Sent custom command for player "
                                + game.getPlayer(playerUserId).getPing()
                                + ": "
                                + commandKey
                                + " from "
                                + draftable.getDisplayName());
                // TODO: Handle magic strings from routeCommand, such as "delete button"
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not deliver command: " + e.getMessage());
            }
        }
    }

    public static class DraftManagerSendCustomOrchestratorCommand extends GameStateSubcommand {
        public DraftManagerSendCustomOrchestratorCommand() {
            super(
                    Constants.DRAFT_MANAGE_CUSTOM_ORCHESTRATOR_COMMAND,
                    "Send a custom command to the Orchestrator in the draft",
                    true,
                    false);
            addOption(OptionType.USER, Constants.PLAYER, "Player sending the command", true);
            addOption(OptionType.STRING, Constants.DRAFT_COMMAND_OPTION, "Key of the command", true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            String playerUserId = event.getOption(Constants.PLAYER).getAsUser().getId();
            String commandKey = event.getOption(Constants.DRAFT_COMMAND_OPTION).getAsString();
            DraftOrchestrator orchestrator = draftManager.getOrchestrator();
            if (orchestrator == null) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "No orchestrator in the draft manager");
                return;
            }
            try {
                String outcome = draftManager.routeCommand(
                        event,
                        game.getPlayer(playerUserId),
                        orchestrator.getButtonPrefix() + "_" + commandKey,
                        CommandSource.SLASH_COMMAND);
                if (DraftButtonService.isError(outcome)) {
                    MessageHelper.sendMessageToChannel(event.getChannel(), "Could not deliver command: " + outcome);
                    return;
                }
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "Sent custom command for player "
                                + game.getPlayer(playerUserId).getPing()
                                + ": "
                                + commandKey
                                + " from "
                                + orchestrator.getClass().getSimpleName());
                // TODO: Handle magic strings from routeCommand, such as "delete button"
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not deliver command: " + e.getMessage());
            }
        }
    }

    public static class DraftManagerConvertToMilty extends GameStateSubcommand {
        public DraftManagerConvertToMilty() {
            super(Constants.DRAFT_MANAGE_CONVERT_TO_MILTY, "Convert the draft to a Milty draft", true, false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            Game game = getGame();
            DraftManager draftManager = game.getDraftManager();
            MiltySettings settings = game.getMiltySettingsUnsafe();
            MiltyDraftSpec spec;
            if (settings != null) {
                spec = MiltyDraftSpec.fromSettings(settings);
            } else {
                spec = new MiltyDraftSpec(game);
            }

            // SETUP SPEC

            // General
            spec.playerIDs = draftManager.getPlayerUserIds();
            String mapTemplateID = game.getMapTemplateID();
            MapTemplateModel mapTemplate = null;
            if (mapTemplateID != null) {
                mapTemplate = Mapper.getMapTemplate(mapTemplateID);
            }
            if (mapTemplate == null) {
                mapTemplate = Mapper.getDefaultMapTemplateForPlayerCount(spec.playerIDs.size());
            }
            spec.setTemplate(mapTemplate);

            // Factions
            FactionDraftable factionDraftable =
                    (FactionDraftable) draftManager.getDraftable(FactionDraftable.TYPE);
            if (factionDraftable != null) {
                spec.numFactions = factionDraftable.getAllDraftChoices().size();
                spec.priorityFactions = factionDraftable.getAllDraftChoices().stream()
                        .map(DraftChoice::getChoiceKey)
                        .collect(Collectors.toList());
            }

            // Speaker Position
            SpeakerOrderDraftable speakerOrderDraftable =
                    (SpeakerOrderDraftable) draftManager.getDraftable(SpeakerOrderDraftable.TYPE);
            // no setup

            // Slices
            SliceDraftable sliceDraftable = (SliceDraftable) draftManager.getDraftable(SliceDraftable.TYPE);
            if (sliceDraftable != null) {
                spec.numSlices = sliceDraftable.getAllDraftChoices().size();
                spec.presetSlices = sliceDraftable.getSlices();
            }

            // Public Snake
            PublicSnakeDraftOrchestrator orchestrator = (PublicSnakeDraftOrchestrator) draftManager.getOrchestrator();
            if (orchestrator != null) {
                spec.setPlayerDraftOrder(orchestrator.getPlayerOrder(draftManager));
            }

            MiltyService.startFromSpecs(event, spec);
            game.setPhaseOfGame("miltydraft");

            MiltyDraftManager miltyManager = game.getMiltyDraftManager();
            if (miltyManager == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Could not find Milty draft manager after starting Milty draft");
                return;
            }

            // APPLY PICKS
            if (orchestrator != null
                    && factionDraftable != null
                    && speakerOrderDraftable != null
                    && sliceDraftable != null) {
                List<String> playerOrder = orchestrator.getPlayerOrder(draftManager);
                int pickIndex = 0;
                boolean done = false;
                while (!done && pickIndex < 100) { // safety to avoid infinite loops
                    for (String playerID : playerOrder) {
                        PlayerDraftState state = draftManager.getPlayerStates().get(playerID);
                        if (state == null) {
                            continue;
                        }

                        List<DraftChoice> picks = state.getPicks().values().stream()
                                .flatMap(List::stream)
                                .collect(Collectors.toList());
                        if (picks.size() <= pickIndex) {
                            done = true;
                            break;
                        }

                        DraftChoice pick = picks.get(pickIndex);
                        if (pick.getType() == FactionDraftable.TYPE) {
                            miltyManager.doMiltyPick(
                                    event, game, "miltyForce_faction_" + pick.getChoiceKey(), game.getPlayer(playerID));
                        } else if (pick.getType() == SpeakerOrderDraftable.TYPE) {
                            miltyManager.doMiltyPick(
                                    event,
                                    game,
                                    "miltyForce_order_" + pick.getChoiceKey().substring("pick".length()),
                                    game.getPlayer(playerID));
                        } else if (pick.getType() == SliceDraftable.TYPE) {
                            miltyManager.doMiltyPick(
                                    event, game, "miltyForce_slice_" + pick.getChoiceKey(), game.getPlayer(playerID));
                        }
                    }
                    pickIndex++;
                    Collections.reverse(playerOrder);
                }
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(), "Could not apply picks to Milty settings, missing draft elements");
            }
        }
    }
}
