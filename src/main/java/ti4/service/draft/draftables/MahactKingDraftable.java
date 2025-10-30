package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.MahactKingDraftableSettings;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.TI4Emoji;

public class MahactKingDraftable extends SinglePickDraftable {

    private List<String> draftFactions = new ArrayList<>();

    public static final DraftableType TYPE = DraftableType.of("King");

    public void initialize(
            int numFactions, List<ComponentSource> sources, List<String> presetFactions, List<String> bannedFactions) {

        List<String> effBannedFactions = new ArrayList<>(bannedFactions);
        List<String> availableFactions = new ArrayList<>(Mapper.getFactionsValues().stream()
                .filter(f -> !effBannedFactions.contains(f.getAlias()))
                .filter(f -> sources.contains(f.getSource()))
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

    public void addFaction(String factionAlias) {
        if (factionAlias == null || factionAlias.isBlank()) {
            throw new IllegalArgumentException("factionAlias cannot be null or blank");
        }
        FactionModel faction = Mapper.getFaction(factionAlias);
        if (faction == null) {
            throw new IllegalArgumentException("Unknown faction: " + factionAlias);
        }
        if (factionAlias.contains("keleres")) {
            factionAlias = "keleresm";
        }
        if (draftFactions.contains(factionAlias)) {
            throw new IllegalArgumentException("Faction already in draft: " + factionAlias);
        }
        draftFactions.add(factionAlias);
    }

    public void removeFaction(String factionAlias) {
        if (factionAlias == null || factionAlias.isBlank()) {
            throw new IllegalArgumentException("factionAlias cannot be null or blank");
        }
        if (factionAlias.contains("keleres")) {
            factionAlias = "keleresm";
        }
        if (!draftFactions.contains(factionAlias)) {
            throw new IllegalArgumentException("Faction not in draft: " + factionAlias);
        }
        draftFactions.remove(factionAlias);
    }

    public static FactionModel getFactionByChoice(DraftChoice choice) {
        if (choice == null || choice.getChoiceKey() == null) return null;
        return getFactionByChoice(choice.getChoiceKey());
    }

    public static FactionModel getFactionByChoice(String choiceKey) {
        return Mapper.getFaction(choiceKey);
    }

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
            String factionName = faction.getFactionName();
            String choiceKey = factionAlias;
            String buttonText = factionName;
            String buttonEmoji = faction.getFactionEmoji();
            String unformattedName = factionName;
            String formattedName = faction.getFactionEmoji() + " **" + factionName + "**";
            DraftChoice choice = new DraftChoice(
                    getType(),
                    choiceKey,
                    makeChoiceButton(choiceKey, buttonText, buttonEmoji),
                    formattedName,
                    unformattedName,
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
        } else if (!restrictChoiceKeys.isEmpty()) {
            buttons.add(Buttons.blue(
                    makeButtonId("info_" + String.join("_", restrictChoiceKeys)), "Available faction info"));
        }
        return buttons;
    }

    @Override
    public String handleCustomCommand(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String buttonId) {
        List<String> informFactions;
        if (buttonId.equals("remaininginfo")) {
            informFactions = new ArrayList<>(draftFactions);
            for (String pId : draftManager.getPlayerStates().keySet()) {
                List<DraftChoice> playerChoices =
                        draftManager.getPlayerStates().get(pId).getPicks().get(getType());
                if (playerChoices != null) {
                    for (DraftChoice choice : playerChoices) {
                        informFactions.remove(choice.getChoiceKey());
                    }
                }
            }
            if (informFactions.isEmpty()) {
                return DraftButtonService.USER_MISTAKE_PREFIX + "No factions remain to show info for.";
            } else {
                sendFactionInfo(draftManager, playerUserId, informFactions);
            }
        } else if (buttonId.equals("pickedinfo")) {
            informFactions = new ArrayList<>();
            for (String pId : draftManager.getPlayerStates().keySet()) {
                List<DraftChoice> playerChoices =
                        draftManager.getPlayerStates().get(pId).getPicks().get(getType());
                if (playerChoices != null) {
                    for (DraftChoice choice : playerChoices) {
                        if (!informFactions.contains(choice.getChoiceKey())) {
                            informFactions.add(choice.getChoiceKey());
                        }
                    }
                }
            }
            if (informFactions.isEmpty()) {
                return DraftButtonService.USER_MISTAKE_PREFIX + "No factions have been picked yet to show info for.";
            } else {
                sendFactionInfo(draftManager, playerUserId, informFactions);
            }
        } else if (buttonId.equals("allinfo")) {
            informFactions = new ArrayList<>(draftFactions);
            sendFactionInfo(draftManager, playerUserId, informFactions);
        } else if (buttonId.startsWith("info_")) {
            String[] tokens = buttonId.substring("info_".length()).split("_");
            informFactions = List.of(tokens);
            sendFactionInfo(draftManager, playerUserId, informFactions);
        } else {
            return "Unknown button action: " + buttonId;
        }

        return null;
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                getType(),
                null,
                null,
                "No king picked",
                "No king picked",
                TI4Emoji.getRandomGoodDog().toString());
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {

        List<DraftChoice> playerPicks = draftManager.getPlayerPicks(playerUserId, TYPE);
        if (!playerPicks.isEmpty()) {
            String factionAlias = playerPicks.get(0).getChoiceKey();
            playerSetupState.setFaction(factionAlias);
        }

        return null;
    }

    @Override
    public String save() {
        return String.join(SAVE_SEPARATOR, draftFactions);
    }

    @Override
    public void load(String data) {
        if (data == null || data.isBlank()) {
            draftFactions = new ArrayList<>();
        } else {
            String[] tokens = data.split(SAVE_SEPARATOR);
            draftFactions = new ArrayList<>();
            for (int i = 0; i < tokens.length; i++) {
                draftFactions.add(tokens[i]);
            }
        }
    }

    @Override
    public String validateState(DraftManager draftManager) {
        int numPlayers = draftManager.getPlayerStates().size();
        if (draftFactions.size() < numPlayers) {
            return "Number of factions (" + draftFactions.size() + ") is less than number of players (" + numPlayers
                    + "). Add more factions with `/draft king add`.";
        }

        // Ensure all factions in draftFactions are valid
        Set<String> distinctFactions = new HashSet<>();
        for (String factionAlias : draftFactions) {
            FactionModel faction = Mapper.getFaction(factionAlias);
            if (faction == null) {
                return "Unknown faction in draftFactions: " + factionAlias + ". Remove it with `/draft king remove`.";
            }

            if (factionAlias.contains("keleres")) {
                factionAlias = "keleres";
            }

            if (distinctFactions.contains(factionAlias)) {
                return "Duplicate faction alias in draftFactions: " + factionAlias
                        + ". Remove it with `/draft king remove`.";
            }
            distinctFactions.add(factionAlias);
        }

        return super.validateState(draftManager);
    }

    @Override
    public String applySetupMenuChoices(GenericInteractionCreateEvent event, SettingsMenu menu) {
        if (menu == null || !(menu instanceof DraftSystemSettings)) {
            return "Error: Could not find parent draft system settings.";
        }
        DraftSystemSettings draftSystemSettings = (DraftSystemSettings) menu;
        Game game = draftSystemSettings.getGame();
        if (game == null) {
            return "Error: Could not find game instance.";
        }
        MahactKingDraftableSettings kingSettings = draftSystemSettings.getMahactKingDraftableSettings();
        // There may be homebrew here someday...
        // SourceSettings sourceSettings = draftSystemSettings.getSourceSettings();

        if (!game.isTwilightsFallMode()) {
            game.setupTwilightsFallMode(event);
        }

        initialize(
                kingSettings.getNumFactions().getVal(),
                List.of(ComponentSource.twilights_fall),
                kingSettings.getPriFactions().getKeys().stream().toList(),
                kingSettings.getBanFactions().getKeys().stream().toList());

        return null;
    }

    private void sendFactionInfo(DraftManager draftManager, String playerUserId, List<String> informFactions) {
        if (informFactions != null && !informFactions.isEmpty()) {
            Player player = draftManager.getGame().getPlayer(playerUserId);
            List<FactionModel> factions = new ArrayList<>();
            for (String factionAlias : informFactions) {
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
    }
}
