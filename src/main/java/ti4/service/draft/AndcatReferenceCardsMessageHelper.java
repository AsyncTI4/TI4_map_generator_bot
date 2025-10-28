package ti4.service.draft;

import java.util.ArrayList;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.twilightsfall.TwilightsFallInfoHelper;
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
    private AndcatReferenceCardsDraftable draftable;

    public AndcatReferenceCardsMessageHelper(AndcatReferenceCardsDraftable draftable) {
        this.draftable = draftable;
    }

    public static void sendPackageInfos(
            DraftManager draftManager, String playerUserId, List<ReferenceCardPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        Player player = draftManager.getGame().getPlayer(playerUserId);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " Here's an overview of the packages:");
        sendPackageInfos(player.getCardsInfoThread(), packages);
    }

    public static void sendPackageInfos(MessageChannel channel, List<ReferenceCardPackage> packages) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        MessageV2Builder messageBuilder = new MessageV2Builder(channel);

        for (ReferenceCardPackage refPackage : packages) {
            StringBuilder message = new StringBuilder();

            message.append("### Faction Package " + refPackage.key())
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
        boolean isChoiceFinalized = refPackage.choicesFinal() != null && refPackage.choicesFinal();

        messageBuilder.appendLine(player.getRepresentation() + " Select how each faction will be used.");

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
                    isChoiceFinalized,
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
                    isChoiceFinalized,
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
                    isChoiceFinalized,
                    faction.getShortName(),
                    faction.getFactionEmoji())));
        }
        messageBuilder.append(Container.of(containerComponents));

        messageBuilder.appendLine("When you're satisfied with your choices, lock them in:");
        Button finalizeButton =
                Buttons.gray(this.draftable.makeButtonId("assign_complete"), "Finish assigning factions");
        boolean canFinalize = refPackage.homeSystemFaction() != null
                && refPackage.startingUnitsFaction() != null
                && refPackage.speakerOrderFaction() != null
                && !isChoiceFinalized;
        if (!canFinalize) {
            finalizeButton = finalizeButton.asDisabled();
        }
        messageBuilder.append(finalizeButton);

        // Send the message
        messageBuilder.send();

        // TODO: Send a summary message to the main game channel
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
        Button finalizeButton = Buttons.gray(this.draftable.makeButtonId("assign_complete"), buttonLabel);
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
        String buttonId = this.draftable.makeButtonId("assign_" + setupPart + "_" + factionAlias);
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

        String playerChoiceKey = draftManager.getPlayerPicks(playerUserId, this.draftable.getType()).stream()
                .map(DraftChoice::getChoiceKey)
                .findFirst()
                .orElse(null);
        if (playerChoiceKey == null) {
            return "You have not picked a reference card package.";
        }
        ReferenceCardPackage refPackage = this.draftable.getPackageByChoiceKey(playerChoiceKey);
        if (refPackage == null) {
            return "Cannot find reference card package for your pick.";
        }
        if (refPackage.choicesFinal() != null && refPackage.choicesFinal()) {
            return DraftButtonService.USER_MISTAKE_PREFIX
                    + "You have already finalized your reference card assignments.";
        }

        if (setupPart.equals("complete")) {
            // Finalize choices
            refPackage = new ReferenceCardPackage(
                    refPackage.key(),
                    refPackage.factions(),
                    refPackage.homeSystemFaction(),
                    refPackage.startingUnitsFaction(),
                    refPackage.speakerOrderFaction(),
                    true);
            Integer packageKey = Integer.parseInt(playerChoiceKey.substring("package".length()));
            this.draftable.getReferenceCardPackages().put(packageKey, refPackage);

            // Disable buttons
            Player player = draftManager.getGame().getPlayer(playerUserId);
            updatePackageButtons(event, draftManager, player, refPackage);

            if (this.draftable.whatsStoppingSetup(draftManager) == null) {
                // TODO: Print speaker order and priority numbers
                // TODO: Block for Keleres to pick their HS tile
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
        this.draftable.getReferenceCardPackages().put(packageKey, refPackage);

        // Update buttons
        Player player = draftManager.getGame().getPlayer(playerUserId);
        updatePackageButtons(event, draftManager, player, refPackage);

        return null;
    }
}
