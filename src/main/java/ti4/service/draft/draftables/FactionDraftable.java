package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import ti4.service.draft.PartialMapService;
import ti4.service.draft.PlayerDraftState;
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

    public static final DraftableType TYPE = new DraftableType("Faction");

    @Override
    public DraftableType getType() {
        return TYPE;
    }

    @Override
    public List<DraftChoice> getAllDraftChoices() {
        List<DraftChoice> choices = new ArrayList<>();
        for (String factionAlias : draftFactions) {
            FactionModel faction = Mapper.getFaction(factionAlias);
            if (faction == null) continue;
            String choiceKey = factionAlias;
            String buttonText = faction.getFactionEmoji() + " " + faction.getFactionName();
            String simpleName = faction.getFactionName();
            String inlineSummary = faction.getFactionEmoji();
            String buttonSuffix = factionAlias;
            DraftChoice choice =
                    new DraftChoice(getType(), choiceKey, buttonText, simpleName, inlineSummary, buttonSuffix);
            choices.add(choice);
        }
        return choices;
    }

    @Override
    public int getNumChoicesPerPlayer() {
        return 1;
    }

    @Override
    public String getButtonPrefix() {
        return "faction_";
    }

    @Override
    public List<Button> getCustomButtons() {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue(getButtonPrefix() + "remaininginfo", "Remaining faction info"));
        buttons.add(Buttons.blue(getButtonPrefix() + "pickedinfo", "Picked faction info"));
        buttons.add(Buttons.blue(getButtonPrefix() + "allinfo", "All faction info"));
        return buttons;
    }

    @Override
    public String handleCustomButtonPress(
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
        if (!CommonDraftableValidators.hasRemainingChoices(
                draftManager, playerUserId, getType(), getNumChoicesPerPlayer())) {
            return "You have already picked your faction.";
        }

        return null;
    }

    @Override
    public void draftChoiceSideEffects(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice) {
        PartialMapService.tryUpdateMap(event, draftManager);
    }

    @Override
    public String getChoiceHeader() {
        return "**__Factions:__**";
    }

    @Override
    public boolean hasInlineSummary() {
        return true;
    }

    @Override
    public String getDefaultInlineSummary() {
        return TI4Emoji.getRandomGoodDog().toString();
    }

    @Override
    public void setupPlayer(DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {
        PlayerDraftState pState = draftManager.getPlayerStates().get(playerUserId);
        if (!pState.getPicks().containsKey(getType())
                || pState.getPicks().get(getType()).isEmpty()) {
            playerSetupState.setFaction(null);
        } else {
            String factionAlias = pState.getPicks().get(getType()).get(0).getChoiceKey();
            playerSetupState.setFaction(factionAlias);
        }
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
    public void validateState(DraftManager draftManager) {
        int numPlayers = draftManager.getPlayerStates().size();
        if (draftFactions.size() < numPlayers) {
            throw new IllegalStateException("Number of factions (" + draftFactions.size()
                    + ") is less than number of players (" + numPlayers + ")");
        }

        // Ensure all factions in draftFactions are valid
        Set<String> distinctFactions = new HashSet<>(draftFactions);
        for (String factionAlias : draftFactions) {
            FactionModel faction = Mapper.getFaction(factionAlias);
            if (faction == null) {
                throw new IllegalStateException("Invalid faction alias in draftFactions: " + factionAlias);
            }

            if (factionAlias.startsWith("keleres")) {
                factionAlias = "keleres";
            }

            if (distinctFactions.contains(factionAlias)) {
                throw new IllegalStateException("Duplicate faction alias in draftFactions: " + factionAlias);
            }
            distinctFactions.add(factionAlias);
        }
    }
}
