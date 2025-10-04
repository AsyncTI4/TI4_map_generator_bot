package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.FactionDraftableSettings;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.helpers.settingsFramework.menus.SourceSettings;
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

public class FactionDraftable extends SinglePickDraftable {

    private List<String> draftFactions = new ArrayList<>();
    private String keleresFlavor;

    @Getter
    private static final List<String> keleresFlavors = List.of("mentak", "xxcha", "argent");

    public void initialize(
            int numFactions, List<ComponentSource> sources, List<String> presetFactions, List<String> bannedFactions) {

        List<String> availableFactions = new ArrayList<>(Mapper.getFactionsValues().stream()
                .filter(f -> !bannedFactions.contains(f.getAlias()))
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
            if (f.startsWith("keleres")) f = "keleresm";
            if (output.contains(f)) continue;
            output.add(f);
        }

        this.draftFactions = output;
        this.keleresFlavor = null;
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

    public void setKeleresFlavor(String flavor) {
        if (flavor == null || flavor.isBlank()) {
            throw new IllegalArgumentException("flavor cannot be null or blank");
        }
        if (!keleresFlavors.contains(flavor)) {
            throw new IllegalArgumentException("Unknown keleres flavor: " + flavor);
        }
        this.keleresFlavor = flavor;
    }

    public static FactionModel getFactionByChoice(DraftChoice choice) {
        if (choice == null || choice.getChoiceKey() == null) return null;
        return getFactionByChoice(choice.getChoiceKey());
    }

    public static FactionModel getFactionByChoice(String choiceKey) {
        return Mapper.getFaction(choiceKey);
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
            String factionName = faction.getFactionName();
            if (factionName.toLowerCase().contains("keleres")) {
                // Chop off any suffix
                factionName = factionName.replaceAll("eleres.*$", "eleres");
            }
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
                return "No factions remain to show info for.";
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
                return "No factions have been picked yet to show info for.";
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
        } else if (buttonId.startsWith("keleresflavor_")) {
            String flavor = buttonId.substring("keleresflavor_".length());
            keleresFlavor = flavor;

            // This can block setup if the draft is over
            if (draftManager.whatsStoppingDraftEnd() == null) {
                draftManager.trySetupPlayers(event);
            } else {
                String flavoredKeleres = "keleres" + flavor.substring(0, 1);
                FactionModel flavorFaction = Mapper.getFaction(flavoredKeleres);
                MessageHelper.sendMessageToChannel(
                        draftManager.getGame().getPlayer(playerUserId).getCardsInfoThread(),
                        "Set Keleres flavor to " + flavorFaction.getFactionEmoji() + " **"
                                + flavorFaction.getFactionName()
                                + "**. You can update this any time until the draft ends.");
                sendKeleresButtons(draftManager, playerUserId, false);
            }

            return DraftButtonService.DELETE_MESSAGE;
        } else {
            return "Unknown button action: " + buttonId;
        }

        return null;
    }

    @Override
    public void postApplyDraftPick(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice) {

        if (choice.getChoiceKey().contains("keleres")) {
            sendKeleresButtons(draftManager, playerUserId, false);
        } else if (keleresFlavor != null && choice.getChoiceKey().contains(keleresFlavor)) {
            keleresFlavor = null;
            List<String> keleresPlayers = draftManager.getPlayersWithChoiceKey(TYPE, "keleresm");
            if (!keleresPlayers.isEmpty()) {
                sendKeleresButtons(draftManager, keleresPlayers.get(0), false);
            }
        } else if (keleresFlavor == null
                && draftFactions.contains("keleresm")
                && keleresFlavors.contains(choice.getChoiceKey())) {
            List<String> keleresPlayers = draftManager.getPlayersWithChoiceKey(TYPE, "keleresm");
            if (!keleresPlayers.isEmpty()) {
                sendKeleresButtons(draftManager, keleresPlayers.get(0), false);
            }
        }
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                getType(),
                null,
                null,
                "No faction picked",
                "No faction picked",
                TI4Emoji.getRandomGoodDog().toString());
    }

    @Override
    public void onDraftEnd(DraftManager draftManager) {
        // Check for and trigger Keleres setup
        List<String> keleresPlayers = draftManager.getPlayersWithChoiceKey(TYPE, "keleresm");
        if (!keleresPlayers.isEmpty() && keleresFlavor == null) {
            sendKeleresButtons(draftManager, keleresPlayers.get(0), true);
        }
    }

    @Override
    public String whatsStoppingSetup(DraftManager draftManager) {
        List<String> keleresPlayers = draftManager.getPlayersWithChoiceKey(TYPE, "keleresm");
        if (!keleresPlayers.isEmpty() && keleresFlavor == null) {
            Player player = draftManager.getGame().getPlayer(keleresPlayers.get(0));
            return "Waiting for " + player.getPing() + " to choose a Keleres flavor.";
        }
        return null;
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {

        List<DraftChoice> playerPicks = draftManager.getPlayerPicks(playerUserId, TYPE);
        if (!playerPicks.isEmpty()) {
            String factionAlias = playerPicks.get(0).getChoiceKey();
            if (factionAlias.contains("keleres")) {
                if (keleresFlavor == null) {
                    throw new IllegalStateException(
                            "Player " + playerUserId + " picked keleres but keleresFlavor is not set");
                }
                factionAlias = "keleres" + keleresFlavor.substring(0, 1);
            }

            playerSetupState.setFaction(factionAlias);
        }

        return null;
    }

    @Override
    public String save() {
        return "keleresflavor_" + (keleresFlavor != null ? keleresFlavor : "null") + SAVE_SEPARATOR
                + String.join(SAVE_SEPARATOR, draftFactions);
    }

    @Override
    public void load(String data) {
        if (data == null || data.isBlank()) {
            draftFactions = new ArrayList<>();
            keleresFlavor = null;
        } else {
            String[] tokens = data.split(SAVE_SEPARATOR);
            draftFactions = new ArrayList<>();
            keleresFlavor =
                    tokens[0].equals("keleresflavor_null") ? null : tokens[0].substring("keleresflavor_".length());
            for (int i = 1; i < tokens.length; i++) {
                draftFactions.add(tokens[i]);
            }
        }
    }

    @Override
    public String validateState(DraftManager draftManager) {
        int numPlayers = draftManager.getPlayerStates().size();
        if (draftFactions.size() < numPlayers) {
            return "Number of factions (" + draftFactions.size() + ") is less than number of players (" + numPlayers
                    + "). Add more factions with `/draft faction add`.";
        }

        // Ensure all factions in draftFactions are valid
        Set<String> distinctFactions = new HashSet<>();
        for (String factionAlias : draftFactions) {
            FactionModel faction = Mapper.getFaction(factionAlias);
            if (faction == null) {
                return "Unknown faction in draftFactions: " + factionAlias
                        + ". Remove it with `/draft faction remove`.";
            }

            if (factionAlias.contains("keleres")) {
                factionAlias = "keleres";
            }

            if (distinctFactions.contains(factionAlias)) {
                return "Duplicate faction alias in draftFactions: " + factionAlias
                        + ". Remove it with `/draft faction remove`.";
            }
            distinctFactions.add(factionAlias);
        }

        if (keleresFlavor != null) {
            if (!keleresFlavors.contains(keleresFlavor)) {
                return "Unknown keleres flavor: " + keleresFlavor
                        + ". Fix it with `/draft faction set_keleres_flavor`.";
            }
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
        FactionDraftableSettings factionSettings = draftSystemSettings.getFactionSettings();
        SourceSettings sourceSettings = draftSystemSettings.getSourceSettings();

        initialize(
                factionSettings.getNumFactions().getVal(),
                sourceSettings.getFactionSources(),
                factionSettings.getPriFactions().getKeys().stream().toList(),
                factionSettings.getBanFactions().getKeys().stream().toList());

        return null;
    }

    private void sendKeleresButtons(DraftManager draftManager, String playerUserId, boolean draftEnded) {
        List<Button> buttons = new ArrayList<>();
        boolean hasDraftableFlavor = false;
        for (String flavor : keleresFlavors) {
            if (draftManager.hasBeenPicked(getType(), flavor)) continue;
            FactionModel flavorFaction = Mapper.getFaction(flavor);
            if (draftEnded || !draftFactions.contains(flavor)) {
                Button button = Buttons.gray(
                        makeButtonId("keleresflavor_" + flavor),
                        flavorFaction.getFactionName(),
                        flavorFaction.getFactionEmoji());
                buttons.add(button);
            } else {
                Button button = Buttons.red(
                        makeButtonId("keleresflavor_" + flavor),
                        flavorFaction.getFactionName() + " 🛑",
                        flavorFaction.getFactionEmoji());
                buttons.add(button);
                hasDraftableFlavor = true;
            }
        }

        Player player = draftManager.getGame().getPlayer(playerUserId);
        if (!draftEnded && !buttons.isEmpty()) {
            String message = player.getPing()
                    + " Pre-select which flavor of Keleres to play in this game by clicking one of these buttons!";
            message += " You can change your decision later by clicking a different button.";
            if (hasDraftableFlavor) {
                message +=
                        "\n- 🛑 Some of these factions are in the draft! 🛑 If you preset them and they get chosen, then the preset will be cancelled.";
            }
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCardsInfoThread(), message, buttons);
        } else if (draftEnded && buttons.isEmpty()) {
            for (String flavor : keleresFlavors) {
                FactionModel flavorFaction = Mapper.getFaction(flavor);
                Button button = Buttons.green(
                        makeButtonId("keleresflavor_" + flavor),
                        flavorFaction.getFactionName(),
                        flavorFaction.getFactionEmoji());
                buttons.add(button);
            }
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                    player.getCardsInfoThread(),
                    "*Hrrnnggh*\nThis is awkward, all of the Keleres flavors got drafted. I'll let you pick any of them, but don't do that again!",
                    buttons);
        } else if (draftEnded) {
            MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(
                    player.getCardsInfoThread(), player.getPing() + " choose a flavor of keleres:", buttons);
        }
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
