package ti4.service.draft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.helpers.twilightsfall.TwilightsFallInfoHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.message.componentsV2.MessageV2Editor;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.FactionModel;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable;
import ti4.service.draft.draftables.AndcatReferenceCardsDraftable.ReferenceCardPackage;

public class AndcatReferenceCardsMessageHelper {
    private static final String USER_SUMMARY_PREFIX = "### Selecting which faction for what purpose\n";

    private final AndcatReferenceCardsDraftable draftable;

    public AndcatReferenceCardsMessageHelper(AndcatReferenceCardsDraftable draftable) {
        this.draftable = draftable;
    }

    public void sendPackageInfos(DraftManager draftManager, String playerUserId, List<ReferenceCardPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        Player player = draftManager.getGame().getPlayer(playerUserId);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", here is an overview of the packages:");
        sendPackageInfos(player.getCardsInfoThread(), packages);
    }

    public static void sendPackageInfos(MessageChannel channel, List<ReferenceCardPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        MessageV2Builder messageBuilder = new MessageV2Builder(channel);

        for (ReferenceCardPackage refPackage : packages) {
            StringBuilder message = new StringBuilder();

            message.append("### Faction Package ")
                    .append(refPackage.key())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());

            List<FactionModel> factionsInPackage = AndcatReferenceCardsDraftable.getFactionsInPackage(refPackage);
            for (FactionModel faction : factionsInPackage) {
                message.append(TwilightsFallInfoHelper.getFactionSetupInfo(faction))
                        .append(System.lineSeparator());
            }

            messageBuilder.append(Container.of(TextDisplay.of(message.toString())));
        }

        messageBuilder.send();
    }

    public void sendPackageButtons(DraftManager draftManager, Player player, ReferenceCardPackage refPackage) {
        ThreadChannel cardsInfoThread = player.getCardsInfoThread();
        if (cardsInfoThread == null) {
            BotLogger.warning(
                    new LogOrigin(),
                    "Cannot send reference card assignment buttons to player " + player.getUserName()
                            + " because their cards info thread is null.");
            return;
        }
        if (refPackage.choicesFinal() != null && refPackage.choicesFinal()) {
            // Choices already finalized
            return;
        }
        List<FactionModel> factionsInPackage = AndcatReferenceCardsDraftable.getFactionsInPackage(refPackage);
        MessageV2Builder messageBuilder = new MessageV2Builder(cardsInfoThread, 3);

        messageBuilder.appendLine(player.getRepresentation() + ", select how each faction will be used.");

        // Part: Home System
        String factionForPart = refPackage.homeSystemFaction();
        List<ContainerChildComponent> containerComponents = new ArrayList<>();
        containerComponents.add(TextDisplay.of("## Home System"));
        for (FactionModel faction : factionsInPackage) {
            containerComponents.add(
                    TextDisplay.of(TwilightsFallInfoHelper.getFactionSetupInfo(faction, false, true, false)));
            boolean isSelectedAnywhere = faction.getAlias().equals(refPackage.startingUnitsFaction())
                    || faction.getAlias().equals(refPackage.speakerOrderFaction())
                    || faction.getAlias().equals(refPackage.homeSystemFaction());
            containerComponents.add(ActionRow.of(makeFactionButton(
                    "hs",
                    faction.getAlias(),
                    isSelectedAnywhere,
                    factionForPart,
                    false,
                    faction.getShortName(),
                    faction.getFactionEmoji())));
        }
        messageBuilder.append(Container.of(containerComponents));

        // Part: Starting Units
        factionForPart = refPackage.startingUnitsFaction();
        containerComponents = new ArrayList<>();
        containerComponents.add(TextDisplay.of("## Starting Units"));
        for (FactionModel faction : factionsInPackage) {
            containerComponents.add(
                    TextDisplay.of(TwilightsFallInfoHelper.getFactionSetupInfo(faction, true, false, false)));
            boolean isSelectedAnywhere = faction.getAlias().equals(refPackage.startingUnitsFaction())
                    || faction.getAlias().equals(refPackage.speakerOrderFaction())
                    || faction.getAlias().equals(refPackage.homeSystemFaction());
            containerComponents.add(ActionRow.of(makeFactionButton(
                    "units",
                    faction.getAlias(),
                    isSelectedAnywhere,
                    factionForPart,
                    false,
                    faction.getShortName(),
                    faction.getFactionEmoji())));
        }
        messageBuilder.append(Container.of(containerComponents));

        // Part: Speaker Order Priority
        factionForPart = refPackage.speakerOrderFaction();
        containerComponents = new ArrayList<>();
        containerComponents.add(
                TextDisplay.of("## Speaker Order Priority" + System.lineSeparator() + "-# Lower is better"));
        for (FactionModel faction : factionsInPackage) {
            containerComponents.add(
                    TextDisplay.of(TwilightsFallInfoHelper.getFactionSetupInfo(faction, false, false, true)));
            boolean isSelectedAnywhere = faction.getAlias().equals(refPackage.startingUnitsFaction())
                    || faction.getAlias().equals(refPackage.speakerOrderFaction())
                    || faction.getAlias().equals(refPackage.homeSystemFaction());
            containerComponents.add(ActionRow.of(makeFactionButton(
                    "priority",
                    faction.getAlias(),
                    isSelectedAnywhere,
                    factionForPart,
                    false,
                    faction.getShortName(),
                    faction.getFactionEmoji())));
        }
        messageBuilder.append(Container.of(containerComponents));

        messageBuilder.appendLine("When you're satisfied with your choices, lock them in:");
        Button finalizeButton = Buttons.gray(draftable.makeButtonId("assign_complete"), "Finish assigning factions");
        boolean canFinalize = refPackage.homeSystemFaction() != null
                && refPackage.startingUnitsFaction() != null
                && refPackage.speakerOrderFaction() != null;
        if (!canFinalize) {
            finalizeButton = finalizeButton.asDisabled();
        }
        messageBuilder.append(finalizeButton);

        // Send the message
        messageBuilder.send();
    }

    public void updatePackagePickSummary(DraftManager draftManager) {
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(USER_SUMMARY_PREFIX);
        for (String playerUserId : draftManager.getPlayerUserIds()) {
            Player player = draftManager.getGame().getPlayer(playerUserId);
            String playerRepresentation = player.getRepresentation(true, false, true, true);
            List<DraftChoice> playerPicks = draftManager.getPlayerPicks(playerUserId, draftable.getType());
            messageBuilder.append(System.lineSeparator());
            if (playerPicks.isEmpty()) {
                messageBuilder
                        .append(playerRepresentation)
                        .append(" has not picked a package of faction reference cards.");
                continue;
            }

            ReferenceCardPackage refPackage =
                    draftable.getPackageByChoiceKey(playerPicks.getFirst().getChoiceKey());
            if (refPackage == null) {
                messageBuilder.append(playerRepresentation).append(" has an invalid package pick.");
                continue;
            }

            if (refPackage.choicesFinal() != null && refPackage.choicesFinal()) {
                messageBuilder.append(playerRepresentation).append(": 🔒");
                if (refPackage.homeSystemFaction() != null
                        && refPackage.startingUnitsFaction() != null
                        && refPackage.speakerOrderFaction() != null) {
                    continue;
                }
            }

            messageBuilder.append(playerRepresentation).append(": ");
            messageBuilder.append(System.lineSeparator()).append("- Home System: ");
            if (refPackage.homeSystemFaction() != null) {
                messageBuilder.append("✅");
            } else {
                messageBuilder.append("❌");
            }

            messageBuilder.append(System.lineSeparator()).append("- Starting Units: ");
            if (refPackage.startingUnitsFaction() != null) {
                messageBuilder.append("✅");
            } else {
                messageBuilder.append("❌");
            }

            messageBuilder.append(System.lineSeparator()).append("- Priority Number: ");
            if (refPackage.speakerOrderFaction() != null) {
                messageBuilder.append("✅");
            } else {
                messageBuilder.append("❌");
            }
        }

        draftManager.getGame().getActionsChannel().getHistory().retrievePast(6).queue(history -> {
            // Try to update existing message if possible
            for (var message : history) {
                if (message.getAuthor().isBot() && message.getContentRaw().startsWith(USER_SUMMARY_PREFIX)) {
                    message.editMessage(messageBuilder.toString()).queue(Consumers.nop(), BotLogger::catchRestError);
                    return;
                }
            }

            Button refreshButton = Buttons.blue(draftable.makeButtonId("assign_refresh"), "Re-send buttons");

            // Fall back to sending it again
            draftManager
                    .getGame()
                    .getActionsChannel()
                    .sendMessage(messageBuilder.toString())
                    .setComponents(ActionRow.of(refreshButton))
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        });
    }

    public void updatePackageButtons(
            GenericInteractionCreateEvent event,
            DraftManager draftManager,
            Player player,
            ReferenceCardPackage refPackage) {
        MessageChannel eventChannel = event.getMessageChannel();
        List<FactionModel> factionsInPackage = AndcatReferenceCardsDraftable.getFactionsInPackage(refPackage);
        MessageV2Editor messageEditor = new MessageV2Editor();

        boolean isChoiceFinalized = refPackage.choicesFinal() != null && refPackage.choicesFinal();

        // Part: Home System
        String factionForPart = refPackage.homeSystemFaction();
        for (FactionModel faction : factionsInPackage) {
            boolean isSelectedAnywhere = faction.getAlias().equals(refPackage.startingUnitsFaction())
                    || faction.getAlias().equals(refPackage.speakerOrderFaction())
                    || faction.getAlias().equals(refPackage.homeSystemFaction());
            Button button = makeFactionButton(
                    "hs",
                    faction.getAlias(),
                    isSelectedAnywhere,
                    factionForPart,
                    isChoiceFinalized,
                    faction.getShortName(),
                    faction.getFactionEmoji());
            messageEditor.replace(button.getCustomId(), button);
        }

        // Part: Starting Units
        factionForPart = refPackage.startingUnitsFaction();
        for (FactionModel faction : factionsInPackage) {
            boolean isSelectedAnywhere = faction.getAlias().equals(refPackage.startingUnitsFaction())
                    || faction.getAlias().equals(refPackage.speakerOrderFaction())
                    || faction.getAlias().equals(refPackage.homeSystemFaction());
            Button button = makeFactionButton(
                    "units",
                    faction.getAlias(),
                    isSelectedAnywhere,
                    factionForPart,
                    isChoiceFinalized,
                    faction.getShortName(),
                    faction.getFactionEmoji());
            messageEditor.replace(button.getCustomId(), button);
        }

        // Part: Speaker Order Priority
        factionForPart = refPackage.speakerOrderFaction();
        for (FactionModel faction : factionsInPackage) {
            boolean isSelectedAnywhere = faction.getAlias().equals(refPackage.startingUnitsFaction())
                    || faction.getAlias().equals(refPackage.speakerOrderFaction())
                    || faction.getAlias().equals(refPackage.homeSystemFaction());
            Button button = makeFactionButton(
                    "priority",
                    faction.getAlias(),
                    isSelectedAnywhere,
                    factionForPart,
                    isChoiceFinalized,
                    faction.getShortName(),
                    faction.getFactionEmoji());
            messageEditor.replace(button.getCustomId(), button);
        }

        // Finalize choices button
        String buttonLabel = isChoiceFinalized ? "Choices locked!" : "Finish assigning factions";
        Button finalizeButton = Buttons.gray(draftable.makeButtonId("assign_complete"), buttonLabel);
        boolean canFinalize = refPackage.homeSystemFaction() != null
                && refPackage.startingUnitsFaction() != null
                && refPackage.speakerOrderFaction() != null
                && !isChoiceFinalized;
        if (!canFinalize) {
            finalizeButton = finalizeButton.asDisabled();
        }
        messageEditor.replace(finalizeButton.getCustomId(), finalizeButton);

        // Apply the changes to button's message if possible
        if (event instanceof ButtonInteractionEvent buttonEvent) {
            messageEditor.applyAroundMessage(buttonEvent.getMessage(), 6, madeChanges -> {
                // Fallback to resending all buttons
                if (!madeChanges) {
                    sendPackageButtons(draftManager, player, refPackage);
                }
            });
            return;
        }

        // Apply changes to recent messages in the event channel
        messageEditor.applyToRecentMessages(eventChannel, 10, madeChanges -> {
            // Fallback to resending all buttons
            if (!madeChanges) {
                sendPackageButtons(draftManager, player, refPackage);
            }
        });
    }

    private Button makeFactionButton(
            String setupPart,
            String factionAlias,
            boolean isFactionInUse,
            String factionForPart,
            boolean isChoiceFinalized,
            String factionName,
            String factionEmoji) {
        String buttonId = draftable.makeButtonId("assign_" + setupPart + "_" + factionAlias);
        if (factionName.toLowerCase().contains("keleres")) {
            factionName = "Keleres";
        }
        Button button = Buttons.green(buttonId, "Pick " + factionName, factionEmoji);
        if (factionAlias.equals(factionForPart)) {
            button = Buttons.red(buttonId, "Unpick " + factionName, factionEmoji);
            if (isChoiceFinalized) {
                button = button.asDisabled();
            }
        } else if (factionForPart != null || isFactionInUse) {
            button = button.asDisabled();
        }

        return button;
    }

    public String handleAssignButton(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String commandKey) {
        if (commandKey.startsWith("assign_")) {
            commandKey = commandKey.substring("assign_".length());
        }
        String[] tokens = commandKey.split("_");
        if (tokens.length > 2) {
            return "Invalid assign command: " + commandKey;
        }
        String setupPart = tokens[0];

        if ("refresh".equals(setupPart)) {
            for (Entry<String, PlayerDraftState> entry : draftManager.playerStates.entrySet()) {
                String otherPlayerUserId = entry.getKey();
                Player otherPlayer = draftManager.getGame().getPlayer(otherPlayerUserId);
                List<DraftChoice> otherPlayerPicks =
                        draftManager.getPlayerPicks(otherPlayerUserId, draftable.getType());
                if (otherPlayerPicks.isEmpty()) {
                    continue;
                }
                ReferenceCardPackage otherRefPackage = draftable.getPackageByChoiceKey(
                        otherPlayerPicks.getFirst().getChoiceKey());
                if (otherRefPackage == null) {
                    continue;
                }

                updatePackageButtons(event, draftManager, otherPlayer, otherRefPackage);
            }
            updatePackagePickSummary(draftManager);

            return null;
        }

        String playerChoiceKey = draftManager.getPlayerPicks(playerUserId, draftable.getType()).stream()
                .map(DraftChoice::getChoiceKey)
                .findFirst()
                .orElse(null);
        if (playerChoiceKey == null) {
            return "You have not picked a reference card package.";
        }
        ReferenceCardPackage refPackage = draftable.getPackageByChoiceKey(playerChoiceKey);
        if (refPackage == null) {
            return "Cannot find reference card package for your pick.";
        }
        if (refPackage.choicesFinal() != null && refPackage.choicesFinal()) {
            return DraftButtonService.USER_MISTAKE_PREFIX
                    + "You have already finalized your reference card assignments.";
        }

        if ("complete".equals(setupPart)) {
            // Make sure everything is picked
            if (refPackage.homeSystemFaction() == null
                    || refPackage.startingUnitsFaction() == null
                    || refPackage.speakerOrderFaction() == null) {
                return DraftButtonService.USER_MISTAKE_PREFIX
                        + "You must assign all factions before finalizing your choices.";
            }

            // Finalize choices
            refPackage = new ReferenceCardPackage(
                    refPackage.key(),
                    refPackage.factions(),
                    refPackage.homeSystemFaction(),
                    refPackage.startingUnitsFaction(),
                    refPackage.speakerOrderFaction(),
                    true);
            Integer packageKey = Integer.parseInt(playerChoiceKey.substring("package".length()));
            draftable.getReferenceCardPackages().put(packageKey, refPackage);

            // Disable buttons
            Player player = draftManager.getGame().getPlayer(playerUserId);
            updatePackageButtons(event, draftManager, player, refPackage);
            updatePackagePickSummary(draftManager);

            if (draftable.whatsStoppingSetup(draftManager) == null) {
                printChoices(draftManager);
                printSpeakerOrder(draftManager);

                // TODO: Make sure setup messages go to the main game channel, not the event channel (e.g. frontier
                // tokens)
                draftManager.trySetupPlayers(event);
            }

            return null;
        }

        String factionAlias = tokens[1];
        if (factionAlias == null || factionAlias.isEmpty()) {
            return "Invalid faction alias in command: " + commandKey;
        }
        if (refPackage.factions().stream().noneMatch(f -> f.equals(factionAlias))) {
            return DraftButtonService.USER_MISTAKE_PREFIX + "The faction " + factionAlias
                    + " is not in your picked reference card package.";
        }

        switch (setupPart) {
            case "hs" -> {
                if (factionAlias.equals(refPackage.homeSystemFaction())) {
                    refPackage = new ReferenceCardPackage(
                            refPackage.key(),
                            refPackage.factions(),
                            null,
                            refPackage.startingUnitsFaction(),
                            refPackage.speakerOrderFaction(),
                            refPackage.choicesFinal());
                } else {
                    if (refPackage.homeSystemFaction() != null) {
                        return DraftButtonService.USER_MISTAKE_PREFIX
                                + "You have already assigned a faction for Home System.";
                    }
                    if (factionAlias.equals(refPackage.startingUnitsFaction())
                            || factionAlias.equals(refPackage.speakerOrderFaction())) {
                        return DraftButtonService.USER_MISTAKE_PREFIX
                                + "That faction is already assigned to another setup part.";
                    }
                    refPackage = new ReferenceCardPackage(
                            refPackage.key(),
                            refPackage.factions(),
                            factionAlias,
                            refPackage.startingUnitsFaction(),
                            refPackage.speakerOrderFaction(),
                            refPackage.choicesFinal());
                }
            }
            case "units" -> {
                if (factionAlias.equals(refPackage.startingUnitsFaction())) {
                    refPackage = new ReferenceCardPackage(
                            refPackage.key(),
                            refPackage.factions(),
                            refPackage.homeSystemFaction(),
                            null,
                            refPackage.speakerOrderFaction(),
                            refPackage.choicesFinal());
                } else {
                    if (refPackage.startingUnitsFaction() != null) {
                        return DraftButtonService.USER_MISTAKE_PREFIX
                                + "You have already assigned a faction for Starting Units.";
                    }
                    if (factionAlias.equals(refPackage.homeSystemFaction())
                            || factionAlias.equals(refPackage.speakerOrderFaction())) {
                        return DraftButtonService.USER_MISTAKE_PREFIX
                                + "That faction is already assigned to another setup part.";
                    }
                    refPackage = new ReferenceCardPackage(
                            refPackage.key(),
                            refPackage.factions(),
                            refPackage.homeSystemFaction(),
                            factionAlias,
                            refPackage.speakerOrderFaction(),
                            refPackage.choicesFinal());
                }
            }
            case "priority" -> {
                if (factionAlias.equals(refPackage.speakerOrderFaction())) {
                    refPackage = new ReferenceCardPackage(
                            refPackage.key(),
                            refPackage.factions(),
                            refPackage.homeSystemFaction(),
                            refPackage.startingUnitsFaction(),
                            null,
                            refPackage.choicesFinal());
                } else {
                    if (refPackage.speakerOrderFaction() != null) {
                        return DraftButtonService.USER_MISTAKE_PREFIX
                                + "You have already assigned a faction for Speaker Order Priority.";
                    }
                    if (factionAlias.equals(refPackage.homeSystemFaction())
                            || factionAlias.equals(refPackage.startingUnitsFaction())) {
                        return DraftButtonService.USER_MISTAKE_PREFIX
                                + "That faction is already assigned to another setup part.";
                    }
                    refPackage = new ReferenceCardPackage(
                            refPackage.key(),
                            refPackage.factions(),
                            refPackage.homeSystemFaction(),
                            refPackage.startingUnitsFaction(),
                            factionAlias,
                            refPackage.choicesFinal());
                }
            }
        }

        Integer packageKey = Integer.parseInt(playerChoiceKey.substring("package".length()));
        draftable.getReferenceCardPackages().put(packageKey, refPackage);

        // Update buttons
        Player player = draftManager.getGame().getPlayer(playerUserId);
        updatePackageButtons(event, draftManager, player, refPackage);
        updatePackagePickSummary(draftManager);

        return null;
    }

    private void printChoices(DraftManager draftManager) {
        Game game = draftManager.getGame();
        for (Entry<String, PlayerDraftState> entry : draftManager.playerStates.entrySet()) {
            String playerUserId = entry.getKey();
            String playerName = game.getPlayer(playerUserId).getRepresentation(false, true, true, true);
            PlayerDraftState playerDraftState = entry.getValue();
            List<DraftChoice> picks = playerDraftState.getPicks(AndcatReferenceCardsDraftable.TYPE);

            MessageV2Builder message = new MessageV2Builder(game.getActionsChannel());

            if (picks.isEmpty()) {
                message.append(Container.of(TextDisplay.of(playerName + " does not have any reference card picks.")));
                message.send();
                continue;
            }

            StringBuilder pickSummary = new StringBuilder(
                    playerName + "'s reference card picks: " + System.lineSeparator() + System.lineSeparator());
            ReferenceCardPackage refPackage =
                    draftable.getPackageByChoiceKey(picks.getFirst().getChoiceKey());
            if (refPackage.homeSystemFaction() != null) {
                FactionModel homeSystemFaction = Mapper.getFaction(refPackage.homeSystemFaction());
                pickSummary.append(TwilightsFallInfoHelper.getFactionSetupInfo(homeSystemFaction, false, true, false));
            } else {
                pickSummary.append(" Home System: None").append(System.lineSeparator());
            }
            if (refPackage.startingUnitsFaction() != null) {
                FactionModel startingUnitsFaction = Mapper.getFaction(refPackage.startingUnitsFaction());
                pickSummary.append(
                        TwilightsFallInfoHelper.getFactionSetupInfo(startingUnitsFaction, true, false, false));
            } else {
                pickSummary.append(" Starting Units: None").append(System.lineSeparator());
            }
            if (refPackage.speakerOrderFaction() != null) {
                FactionModel speakerOrderFaction = Mapper.getFaction(refPackage.speakerOrderFaction());
                pickSummary.append(
                        TwilightsFallInfoHelper.getFactionSetupInfo(speakerOrderFaction, false, false, true));
            } else {
                pickSummary.append(" Priority Number: None").append(System.lineSeparator());
            }
            message.append(Container.of(TextDisplay.of(pickSummary.toString())));
            message.send();
        }
    }

    private void printSpeakerOrder(DraftManager draftManager) {
        Map<Integer, String> playersByPriorityNumber = new HashMap<>();
        List<String> unorderedPlayers = new ArrayList<>();
        for (Entry<String, PlayerDraftState> entry : draftManager.playerStates.entrySet()) {
            String playerUserId = entry.getKey();
            PlayerDraftState playerDraftState = entry.getValue();
            List<DraftChoice> picks = playerDraftState.getPicks(AndcatReferenceCardsDraftable.TYPE);
            if (picks.isEmpty()) {
                unorderedPlayers.add(playerUserId);
                continue;
            }

            ReferenceCardPackage refPackage =
                    draftable.getPackageByChoiceKey(picks.getFirst().getChoiceKey());

            if (refPackage.speakerOrderFaction() == null) {
                unorderedPlayers.add(playerUserId);
                continue;
            }

            FactionModel faction = Mapper.getFaction(refPackage.speakerOrderFaction());
            if (faction == null || faction.getPriorityNumber() == null) {
                unorderedPlayers.add(playerUserId);
                continue;
            }

            playersByPriorityNumber.put(faction.getPriorityNumber(), playerUserId);
        }

        List<Entry<Integer, String>> sortedEntries = playersByPriorityNumber.entrySet().stream()
                .sorted(Entry.comparingByKey())
                .toList();

        StringBuilder message = new StringBuilder();
        message.append("### Speaker Order Priority").append(System.lineSeparator());
        int order = 1;
        for (Entry<Integer, String> entry : sortedEntries) {
            String playerUserId = entry.getValue();
            Player player = draftManager.getGame().getPlayer(playerUserId);
            Integer priorityNumber = entry.getKey();
            message.append("> ")
                    .append(order)
                    .append(". ")
                    .append(player.getRepresentation(false, true, true, true))
                    .append(" (**")
                    .append(priorityNumber)
                    .append("**)")
                    .append(System.lineSeparator());
            order++;
        }
        if (!unorderedPlayers.isEmpty()) {
            BotLogger.warning(
                    new LogOrigin(draftManager.getGame()),
                    Constants.jabberwockyPing() + " Could not place all players in speaker order by priority number");
            message.append("### Unordered Players").append(System.lineSeparator());
            for (String playerUserId : unorderedPlayers) {
                Player player = draftManager.getGame().getPlayer(playerUserId);
                message.append("> - ").append(player.getRepresentation()).append(System.lineSeparator());
            }
        }

        MessageHelper.sendMessageToChannel(draftManager.getGame().getActionsChannel(), message.toString());
    }
}
