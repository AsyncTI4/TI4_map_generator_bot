package ti4.commands.draft;

import java.util.ArrayList;
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
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftOrchestrator;
import ti4.service.draft.orchestrators.PublicSnakeDraftOrchestrator;

public class PublicSnakeDraftOrchestratorSubcommands extends SubcommandGroup {

    private static final Map<String, Subcommand> subcommands = Stream.of(
                    new PublicSnakeDraftOrchestratorSetOrder(),
                    new PublicSnakeDraftOrchestratorSetDraftingPlayer(),
                    new PublicSnakeDraftOrchestratorSetDraftDirection(),
                    new PublicSnakeDraftOrchestratorSendButtons())
            .collect(Collectors.toMap(Subcommand::getName, subcommand -> subcommand));

    protected PublicSnakeDraftOrchestratorSubcommands() {
        super(Constants.DRAFT_PUBLIC_SNAKE, "Commands for managing public snake drafting");
    }

    @Override
    public Map<String, Subcommand> getGroupSubcommands() {
        return subcommands;
    }

    public static PublicSnakeDraftOrchestrator getOrchestrator(Game game) {
        DraftManager draftManager = game.getDraftManager();
        if (draftManager == null) {
            return null;
        }
        DraftOrchestrator orchestrator = draftManager.getOrchestrator();
        if (orchestrator == null || !(orchestrator instanceof PublicSnakeDraftOrchestrator)) {
            return null;
        }
        return (PublicSnakeDraftOrchestrator) orchestrator;
    }

    public static class PublicSnakeDraftOrchestratorSetOrder extends GameStateSubcommand {

        protected PublicSnakeDraftOrchestratorSetOrder() {
            super(Constants.DRAFT_PUBLIC_SNAKE_SET_ORDER, "Set the draft order for the public snake draft", true, true);
            addOption(OptionType.USER, Constants.PLAYER1, "The user ID of the first player", true);
            addOption(OptionType.USER, Constants.PLAYER2, "The user ID of the second player", false);
            addOption(OptionType.USER, Constants.PLAYER3, "The user ID of the third player", false);
            addOption(OptionType.USER, Constants.PLAYER4, "The user ID of the fourth player", false);
            addOption(OptionType.USER, Constants.PLAYER5, "The user ID of the fifth player", false);
            addOption(OptionType.USER, Constants.PLAYER6, "The user ID of the sixth player", false);
            addOption(OptionType.USER, Constants.PLAYER7, "The user ID of the seventh player", false);
            addOption(OptionType.USER, Constants.PLAYER8, "The user ID of the eighth player", false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            PublicSnakeDraftOrchestrator orchestrator = getOrchestrator(getGame());
            if (orchestrator == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "The draft isn't using the Public Snake Draft Orchestrator; you may need `/draft manage set_orchestrator public_snake`.");
                return;
            }
            List<String> userIds = new ArrayList<>();
            if (event.getOption(Constants.PLAYER1) != null) {
                userIds.add(event.getOption(Constants.PLAYER1).getAsUser().getId());
            }
            if (event.getOption(Constants.PLAYER2) != null) {
                userIds.add(event.getOption(Constants.PLAYER2).getAsUser().getId());
            }
            if (event.getOption(Constants.PLAYER3) != null) {
                userIds.add(event.getOption(Constants.PLAYER3).getAsUser().getId());
            }
            if (event.getOption(Constants.PLAYER4) != null) {
                userIds.add(event.getOption(Constants.PLAYER4).getAsUser().getId());
            }
            if (event.getOption(Constants.PLAYER5) != null) {
                userIds.add(event.getOption(Constants.PLAYER5).getAsUser().getId());
            }
            if (event.getOption(Constants.PLAYER6) != null) {
                userIds.add(event.getOption(Constants.PLAYER6).getAsUser().getId());
            }
            if (event.getOption(Constants.PLAYER7) != null) {
                userIds.add(event.getOption(Constants.PLAYER7).getAsUser().getId());
            }
            if (event.getOption(Constants.PLAYER8) != null) {
                userIds.add(event.getOption(Constants.PLAYER8).getAsUser().getId());
            }
            try {
                orchestrator.setDraftOrder(getGame().getDraftManager(), userIds);
                MessageHelper.sendMessageToChannel(event.getChannel(), "Draft order was set.");
                orchestrator.validateState(getGame().getDraftManager());
            } catch (IllegalArgumentException e) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Could not set draft order: " + e.getMessage());
            }
        }
    }

    public static class PublicSnakeDraftOrchestratorSetDraftingPlayer extends GameStateSubcommand {
        protected PublicSnakeDraftOrchestratorSetDraftingPlayer() {
            super(Constants.DRAFT_PUBLIC_SNAKE_SET_CURRENT_PLAYER, "Set the current drafting player", true, false);
            addOption(OptionType.USER, Constants.PLAYER, "The user ID of the drafting player", true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            PublicSnakeDraftOrchestrator orchestrator = getOrchestrator(getGame());
            if (orchestrator == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "The draft isn't using the Public Snake Draft Orchestrator; you may need `/draft manage set_orchestrator public_snake`.");
                return;
            }

            String userId = event.getOption(Constants.PLAYER).getAsUser().getId();
            List<String> playerOrder = orchestrator.getDraftOrder(getGame().getDraftManager());
            if (!playerOrder.contains(userId)) {
                MessageHelper.sendMessageToChannel(event.getChannel(), "Player " + userId + " is not in the draft.");
                return;
            }

            orchestrator.setCurrentPlayerIndex(playerOrder.indexOf(userId));
            MessageHelper.sendMessageToChannel(event.getChannel(), "Drafting player was set.");
            orchestrator.validateState(getGame().getDraftManager());
        }
    }

    public static class PublicSnakeDraftOrchestratorSetDraftDirection extends GameStateSubcommand {
        protected PublicSnakeDraftOrchestratorSetDraftDirection() {
            super(Constants.DRAFT_PUBLIC_SNAKE_SET_DRAFT_DIRECTION, "Set the draft direction", true, false);
            addOption(
                    OptionType.BOOLEAN,
                    Constants.DRAFT_DIRECTION_FORWARD_OPTION,
                    "Whether the draft direction is forward",
                    true);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            PublicSnakeDraftOrchestrator orchestrator = getOrchestrator(getGame());
            if (orchestrator == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "The draft isn't using the Public Snake Draft Orchestrator; you may need `/draft manage set_orchestrator public_snake`.");
                return;
            }

            boolean directionForward =
                    event.getOption(Constants.DRAFT_DIRECTION_FORWARD_OPTION).getAsBoolean();
            orchestrator.setReversing(!directionForward);
            MessageHelper.sendMessageToChannel(event.getChannel(), "Draft direction was set.");
            orchestrator.validateState(getGame().getDraftManager());
        }
    }

    public static class PublicSnakeDraftOrchestratorSendButtons extends GameStateSubcommand {
        protected PublicSnakeDraftOrchestratorSendButtons() {
            super(Constants.DRAFT_PUBLIC_SNAKE_SEND_BUTTONS, "Send draft buttons for the current draft", true, false);
        }

        @Override
        public void execute(SlashCommandInteractionEvent event) {
            PublicSnakeDraftOrchestrator orchestrator = getOrchestrator(getGame());
            if (orchestrator == null) {
                MessageHelper.sendMessageToChannel(
                        event.getChannel(),
                        "The draft isn't using the Public Snake Draft Orchestrator; you may need `/draft manage set_orchestrator public_snake`.");
                return;
            }

            orchestrator.sendDraftButtons(getGame().getDraftManager());
        }
    }
}
