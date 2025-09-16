package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.image.Mapper;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.Draftable;
import ti4.service.draft.DraftableType;
import ti4.service.draft.FactionExtraSetupHelper;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.TI4Emoji;

public class FactionDraftable extends Draftable {

    private List<String> draftFactions;

    public void initialize(
            int numFactions, List<ComponentSource> sources, List<String> presetFactions, List<String> bannedFactions) {

        List<String> availableFactions = new ArrayList<>(Mapper.getFactionsValues().stream()
                .filter(f -> !bannedFactions.contains(f.getAlias()))
                .filter(f -> sources.contains(f.getSource()))
                .filter(f -> !f.getAlias().contains("keleres")
                        || "keleresm".equals(f.getAlias())) // Limit the pool to only 1 keleres flavor
                .map(FactionModel::getAlias)
                .toList());
        List<String> randomOrder = new ArrayList<>(presetFactions);
        Collections.shuffle(randomOrder);
        Collections.shuffle(availableFactions);
        randomOrder.addAll(availableFactions);

        int i = 0;
        List<String> output = new ArrayList<>();
        while (output.size() < numFactions) {
            if (i >= randomOrder.size()) break;
            String f = randomOrder.get(i);
            i++;
            if (output.contains(f)) continue;
            output.add(f);
        }

        this.draftFactions = output;
    }

    public static FactionModel getFactionByChoice(DraftChoice choice) {
        if (choice == null || choice.getChoiceKey() == null) return null;
        return Mapper.getFaction(choice.getChoiceKey());
    }

    public static final DraftableType TYPE = DraftableType.of("Faction");

    @Override
    public DraftableType getType() {
        return TYPE;
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (String factionAlias : draftFactions) {
            FactionModel faction = Mapper.getFaction(factionAlias);
            if (faction == null) {
                throw new IllegalStateException("Unknown faction: " + factionAlias);
            }
            String choiceKey = factionAlias;
            String buttonText = faction.getFactionName();
            String buttonEmoji = faction.getFactionEmoji();
            String formattedName = faction.getFactionEmoji() + " **" + faction.getFactionName() + "**";
            DraftChoice choice = new DraftChoice(
                    getType(),
                    choiceKey,
                    makeChoiceButton(choiceKey, buttonText, buttonEmoji),
                    formattedName,
                    buttonEmoji);
            choices.add(choice);
        }
        return choices;
    }

    @Override
    public List<Button> getCustomChoiceButtons(List<String> restrictChoiceKeys) {
        List<Button> buttons = new ArrayList<>();
        if (restrictChoiceKeys == null) {
            buttons.add(Buttons.blue(makeButtonId("remaininginfo"), "Remaining faction info"));
            buttons.add(Buttons.blue(makeButtonId("pickedinfo"), "Picked faction info"));
            buttons.add(Buttons.blue(makeButtonId("allinfo"), "All faction info"));
        } else {
            buttons.add(Buttons.blue(
                    makeButtonId("info_" + String.join("_", restrictChoiceKeys)), "Available faction info"));
        }
        return buttons;
    }

    @Override
    public String handleCustomCommand(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId) {
        List<String> factionsToInfo;
        if (buttonId.equals("remaininginfo")) {
            factionsToInfo = new ArrayList<>(draftFactions);
            for (String pId : draftManager.getPlayerStates().keySet()) {
                List<DraftChoice> playerChoices =
                        draftManager.getPlayerStates().get(pId).getPicks().get(getType());
                if (playerChoices != null) {
                    for (DraftChoice choice : playerChoices) {
                        factionsToInfo.remove(choice.getChoiceKey());
                    }
                }
            }
            if (factionsToInfo.isEmpty()) {
                return "No factions remain to show info for.";
            }
        } else if (buttonId.equals("pickedinfo")) {
            factionsToInfo = new ArrayList<>();
            for (String pId : draftManager.getPlayerStates().keySet()) {
                List<DraftChoice> playerChoices =
                        draftManager.getPlayerStates().get(pId).getPicks().get(getType());
                if (playerChoices != null) {
                    for (DraftChoice choice : playerChoices) {
                        if (!factionsToInfo.contains(choice.getChoiceKey())) {
                            factionsToInfo.add(choice.getChoiceKey());
                        }
                    }
                }
            }
            if (factionsToInfo.isEmpty()) {
                return "No factions have been picked yet to show info for.";
            }
        } else if (buttonId.equals("allinfo")) {
            factionsToInfo = new ArrayList<>(draftFactions);
        } else if (buttonId.startsWith("info_")) {
            String[] tokens = buttonId.substring("info_".length()).split("_");
            factionsToInfo = List.of(tokens);
        } else {
            return "Unknown button action: " + buttonId;
        }

        if (factionsToInfo != null && !factionsToInfo.isEmpty()) {
            Player player = draftManager.getGame().getPlayer(playerUserId);
            List<FactionModel> factions = new ArrayList<>();
            for (String factionAlias : factionsToInfo) {
                FactionModel faction = Mapper.getFaction(factionAlias);
                if (faction != null) {
                    factions.add(faction);
                }
            }

            boolean first = true;
            List<MessageEmbed> embeds =
                    factions.stream().map(FactionModel::fancyEmbed).toList();
            for (MessageEmbed e : embeds) {
                String message = "";
                if (first) message = player.getRepresentationUnfogged() + " Here's an overview of the factions:";
                MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, e);
                first = false;
            }
        }

        return null;
    }

    @Override
    public String isValidDraftChoice(DraftManager draftManager, String playerUserId, DraftChoice choice) {
        if (!CommonDraftableValidators.isChoiceKeyInList(choice, draftFactions)) {
            return "That faction is not available in this draft.";
        }
        if (!CommonDraftableValidators.hasRemainingChoices(draftManager, playerUserId, getType(), 1)) {
            return "You have already picked your faction.";
        }

        return null;
    }

    @Override
    public void postApplyDraftChoice(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice) {

        if (choice.getChoiceKey().contains("keleres")) {
            Predicate<String> isFactionTaken = f -> {
                return !draftManager.getPlayersWithChoiceKey(getType(), f).isEmpty();
            };
            Predicate<String> isInDraft = f -> {
                return draftFactions.contains(f);
            };
            FactionExtraSetupHelper.offerKeleresSetupButtons(
                    draftManager.getGame().getPlayer(playerUserId), isFactionTaken, isInDraft);
        }
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                getType(),
                null,
                null,
                "No faction picked",
                TI4Emoji.getRandomGoodDog().toString());
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {
        List<DraftChoice> playerPicks = draftManager.getPlayerChoices(playerUserId, TYPE);
        if (playerPicks.isEmpty()) {
            playerSetupState.setFaction(null);
        } else {
            playerSetupState.setFaction(playerPicks.get(0).getChoiceKey());
        }

        return null;
    }

    @Override
    public String save() {
        return String.join(",", draftFactions);
    }

    @Override
    public void load(String data) {
        if (data == null || data.isBlank()) {
            draftFactions = new ArrayList<>();
        } else {
            String[] tokens = data.split(",");
            draftFactions = new ArrayList<>();
            Collections.addAll(draftFactions, tokens);
        }
    }

    @Override
    public String validateState(DraftManager draftManager) {
        int numPlayers = draftManager.getPlayerStates().size();
        if (draftFactions.size() < numPlayers) {
            return "Number of factions (" + draftFactions.size() + ") is less than number of players (" + numPlayers
                    + ")";
        }

        // Ensure all factions in draftFactions are valid
        Set<String> distinctFactions = new HashSet<>();
        for (String factionAlias : draftFactions) {
            FactionModel faction = Mapper.getFaction(factionAlias);
            if (faction == null) {
                return "Unknown faction in draftFactions: " + factionAlias;
            }

            if (factionAlias.startsWith("keleres")) {
                factionAlias = "keleres";
            }

            if (distinctFactions.contains(factionAlias)) {
                return "Duplicate faction alias in draftFactions: " + factionAlias;
            }
            distinctFactions.add(factionAlias);
        }
        return null;
    }
}
