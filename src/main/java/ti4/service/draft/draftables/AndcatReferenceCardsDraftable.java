package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Consumer;

import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.helpers.twilightsfall.TwilightsFallInfoHelper;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.FactionModel;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.TI4Emoji;

public class AndcatReferenceCardsDraftable extends SinglePickDraftable {

    private static record ReferenceCardPackage(
            List<String> factions,
            String homeSystemFaction,
            String startingUnitsFaction,
            String speakerOrderFaction) {
    }

    private final Map<Integer, ReferenceCardPackage> referenceCardPackages = new HashMap<>();

    public static final DraftableType TYPE = DraftableType.of("Andcat");

    @Override
    public DraftableType getType() {
        return TYPE;
    }

    public static List<FactionModel> getFactionsInPackage(ReferenceCardPackage refCardPackage) {
        List<FactionModel> factions = new ArrayList<>();
        for (String factionKey : refCardPackage.factions) {
            FactionModel faction = Mapper.getFaction(factionKey);
            if (faction == null) {
                BotLogger.warning(new LogOrigin(),
                        "Cannot find faction model for faction " + factionKey + " in ReferenceCardPackage.");
                continue;
            }
            factions.add(faction);
        }
        return factions;
    }

    private ReferenceCardPackage getPackageByChoiceKey(String choiceKey) {
        if (!choiceKey.startsWith("package")) {
            return null;
        }
        try {
            Integer packageKey = Integer.parseInt(choiceKey.substring("package".length()));
            return referenceCardPackages.get(packageKey);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (Entry<Integer, ReferenceCardPackage> entry : referenceCardPackages.entrySet()) {
            Integer packageKey = entry.getKey();
            List<FactionModel> factionsInPackage = getFactionsInPackage(entry.getValue());
            String choiceKey = "package" + packageKey;
            String buttonText = "Package " + packageKey;
            String unformattedName = "Package " + packageKey;
            String formattedName = "Package " + packageKey + " (" +
                    String.join(", ", factionsInPackage.stream().map(FactionModel::getShortName).toList()) + ")";
            String identifyingEmoji = factionsInPackage.stream().map(FactionModel::getFactionEmoji).reduce("",
                    String::concat);
            DraftChoice choice = new DraftChoice(
                    getType(),
                    choiceKey,
                    makeChoiceButton(choiceKey, buttonText, null),
                    formattedName,
                    unformattedName,
                    identifyingEmoji);
            choices.add(choice);
        }
        return choices;
    }

    @Override
    public List<Button> getCustomChoiceButtons(List<String> restrictChoiceKeys) {
        List<Button> buttons = new ArrayList<>();
        if (restrictChoiceKeys == null) {
            buttons.add(Buttons.blue(makeButtonId("remaininginfo"), "Remaining package info"));
            buttons.add(Buttons.blue(makeButtonId("pickedinfo"), "Picked package info"));
            buttons.add(Buttons.blue(makeButtonId("allinfo"), "All package info"));
        } else if (!restrictChoiceKeys.isEmpty()) {
            buttons.add(Buttons.blue(
                    makeButtonId("info_" + String.join("_", restrictChoiceKeys)), "Available package info"));
        }
        return buttons;
    }

    @Override
    public String handleCustomCommand(GenericInteractionCreateEvent event, DraftManager draftManager,
            String playerUserId, String commandKey) {

        List<String> informPackages;
        if (commandKey.equals("remaininginfo")) {
            informPackages = new ArrayList<>(
                    referenceCardPackages.values().stream().map(p -> p.factions()).flatMap(List::stream).toList());
            for (String pId : draftManager.getPlayerStates().keySet()) {
                List<DraftChoice> playerChoices = draftManager.getPlayerPicks(pId, TYPE);
                if (playerChoices != null) {
                    for (DraftChoice choice : playerChoices) {
                        informPackages.remove(choice.getChoiceKey());
                    }
                }
            }
            if (informPackages.isEmpty()) {
                return DraftButtonService.USER_MISTAKE_PREFIX + "No packages remain to show info for.";
            } else {
                sendPackageInfos(draftManager, playerUserId, informPackages);
            }
        } else if (commandKey.equals("pickedinfo")) {
            informPackages = new ArrayList<>();
            for (String pId : draftManager.getPlayerStates().keySet()) {
                List<DraftChoice> playerChoices = draftManager.getPlayerPicks(pId, TYPE);
                if (playerChoices != null) {
                    for (DraftChoice choice : playerChoices) {
                        if (!informPackages.contains(choice.getChoiceKey())) {
                            informPackages.add(choice.getChoiceKey());
                        }
                    }
                }
            }
            if (informPackages.isEmpty()) {
                return DraftButtonService.USER_MISTAKE_PREFIX + "No packages have been picked yet to show info for.";
            } else {
                sendPackageInfos(draftManager, playerUserId, informPackages);
            }
        } else if (commandKey.equals("allinfo")) {
            informPackages = new ArrayList<>(referenceCardPackages.keySet().stream().map(k -> "package" + k).toList());
            sendPackageInfos(draftManager, playerUserId, informPackages);
        } else if (commandKey.startsWith("info_")) {
            String[] tokens = commandKey.substring("info_".length()).split("_");
            informPackages = List.of(tokens);
            sendPackageInfos(draftManager, playerUserId, informPackages);
        } else {
            return "Unknown command: " + commandKey;
        }

        return null;
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                TYPE,
                null,
                null,
                "No package picked",
                "No package picked",
                TI4Emoji.getRandomGoodDog().toString());
    }

    @Override
    public String save() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'save'");
    }

    @Override
    public void load(String data) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'load'");
    }

    @Override
    public String applySetupMenuChoices(GenericInteractionCreateEvent event, SettingsMenu menu) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'applySetupMenuChoices'");
    }

    @Override
    public void onDraftEnd(DraftManager draftManager) {
        // Send buttons to assign reference cards to specific setup things
        for (String playerId : draftManager.getPlayerStates().keySet()) {
            List<DraftChoice> playerChoices = draftManager.getPlayerPicks(playerId, TYPE);
            if (playerChoices == null || playerChoices.isEmpty()) {
                continue;
            }
            DraftChoice pick = playerChoices.get(0);
            ReferenceCardPackage refPackage = this.getPackageByChoiceKey(pick.getChoiceKey());
            Player player = draftManager.getGame().getPlayer(playerId);
        }
    }

    @Override
    public String whatsStoppingSetup(DraftManager draftManager) {
        // Ensure each faction in selected packages has been assigned to a setup thing
        List<String> keleresPlayers = draftManager.getPlayersWithChoiceKey(TYPE, "keleresm");
        if (!keleresPlayers.isEmpty() && keleresFlavor == null) {
            Player player = draftManager.getGame().getPlayer(keleresPlayers.get(0));
            return "Waiting for " + player.getPing() + " to choose a Keleres flavor.";
        }
        return null;
    }

    @Override
    public Consumer<Player> setupPlayer(DraftManager draftManager, String playerUserId,
            PlayerSetupState playerSetupState) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setupPlayer'");
    }

    private void sendPackageInfos(DraftManager draftManager, String playerUserId, List<String> choiceKeys) {
        if (choiceKeys == null || choiceKeys.isEmpty()) {
            return;
        }
        Player player = draftManager.getGame().getPlayer(playerUserId);

        boolean first = true;
        for (String choiceKey : choiceKeys) {
            StringBuilder message = new StringBuilder();
            if (first) {
                message.append(player.getRepresentationUnfogged()).append(" Here's an overview of the factions:");
            }
            ReferenceCardPackage refPackage = this.getPackageByChoiceKey(choiceKey);
            if (refPackage == null) {
                BotLogger.warning(new LogOrigin(),
                        "Cannot find ReferenceCardPackage for choice key " + choiceKey + " when sending faction info.");
                continue;
            }
            List<FactionModel> factionsInPackage = getFactionsInPackage(refPackage);
            for(FactionModel faction: factionsInPackage) {
                message.append(TwilightsFallInfoHelper.getFactionSetupInfo(faction)).append(System.lineSeparator());
            }
            MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), message.toString());
            first = false;
        }
    }

    private void sendPackageButtons(DraftManager draftManager, Player player, ReferenceCardPackage refPackage) {
        
    }
}
