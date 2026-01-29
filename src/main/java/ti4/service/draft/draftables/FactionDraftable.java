package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.FactionDraftableSettings;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.PlanetModel;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.MiscEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;

public class FactionDraftable extends SinglePickDraftable {

    private List<String> draftFactions = new ArrayList<>();
    private String keleresFlavor;

    @Getter
    private static final List<String> keleresFlavors = List.of("mentak", "xxcha", "argent");

    public static final DraftableType TYPE = DraftableType.of("Faction");

    public void initialize(
            int numFactions, List<ComponentSource> sources, List<String> presetFactions, List<String> bannedFactions) {

        List<String> effBannedFactions = new ArrayList<>(bannedFactions);

        List<String> availableFactions = new ArrayList<>(Mapper.getFactionsValues().stream()
                .filter(f -> !effBannedFactions.contains(f.getAlias()))
                .filter(f -> sources.contains(f.getSource()))
                .filter(f -> !f.getAlias().contains("obsidian"))
                .filter(f -> !f.getAlias().contains("kaltrim"))
                .filter(f -> !f.getAlias().contains("neutral"))
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

        draftFactions = output;
        keleresFlavor = null;
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
        keleresFlavor = flavor;
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
            if (factionName.toLowerCase().contains("keleres")) {
                // Chop off any suffix
                factionName = factionName.replaceAll("eleres.*$", "eleres");
            }
            String choiceKey = factionAlias;
            String buttonText = factionName;
            if (factionName.toLowerCase().contains("naalu")) {
                buttonText += " (Uses New Agent and Mech)";
            }
            String buttonEmoji = faction.getFactionEmoji();
            String unformattedName = factionName;
            String formattedName = faction.getFactionEmoji() + " **" + factionName + "**";
            DraftChoice choice = new DraftChoice(
                    TYPE,
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
        if ("remaininginfo".equals(buttonId)) {
            informFactions = new ArrayList<>(draftFactions);
            for (String pId : draftManager.getPlayerStates().keySet()) {
                List<DraftChoice> playerChoices =
                        draftManager.getPlayerStates().get(pId).getPicks().get(TYPE);
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
        } else if ("pickedinfo".equals(buttonId)) {
            informFactions = new ArrayList<>();
            for (String pId : draftManager.getPlayerStates().keySet()) {
                List<DraftChoice> playerChoices =
                        draftManager.getPlayerStates().get(pId).getPicks().get(TYPE);
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
        } else if ("allinfo".equals(buttonId)) {
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
                // If the draft is over, try to proceed with setup
                draftManager.trySetupPlayers(event);
            } else {
                // Regenerate buttons and message based on current draft state
                sendKeleresButtons(draftManager, playerUserId, false);

                String flavoredKeleres = "keleres" + flavor.charAt(0);
                FactionModel flavorFaction = Mapper.getFaction(flavoredKeleres);
                MessageHelper.sendMessageToChannel(
                        draftManager.getGame().getPlayer(playerUserId).getCardsInfoThread(),
                        "Set Keleres flavor to " + flavorFaction.getFactionEmoji() + " **"
                                + flavorFaction.getFactionName()
                                + "**. You can update this any time until the draft ends.");
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
                sendKeleresButtons(draftManager, keleresPlayers.getFirst(), false);
            }
        } else if (keleresFlavor == null
                && draftFactions.contains("keleresm")
                && keleresFlavors.contains(choice.getChoiceKey())) {
            List<String> keleresPlayers = draftManager.getPlayersWithChoiceKey(TYPE, "keleresm");
            if (!keleresPlayers.isEmpty()) {
                sendKeleresButtons(draftManager, keleresPlayers.getFirst(), false);
            }
        }
    }

    @Override
    public DraftChoice getNothingPickedChoice() {
        return new DraftChoice(
                TYPE,
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
            sendKeleresButtons(draftManager, keleresPlayers.getFirst(), true);
        }
    }

    @Override
    public String whatsStoppingSetup(DraftManager draftManager) {
        List<String> keleresPlayers = draftManager.getPlayersWithChoiceKey(TYPE, "keleresm");
        if (!keleresPlayers.isEmpty() && keleresFlavor == null) {
            Player player = draftManager.getGame().getPlayer(keleresPlayers.getFirst());
            return "Waiting for " + player.getPing() + " to choose a Keleres flavor.";
        }
        return null;
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {

        List<DraftChoice> playerPicks = draftManager.getPlayerPicks(playerUserId, TYPE);
        if (!playerPicks.isEmpty()) {
            String factionAlias = playerPicks.getFirst().getChoiceKey();
            if (factionAlias.contains("keleres")) {
                if (keleresFlavor == null) {
                    throw new IllegalStateException(
                            "Player " + playerUserId + " picked keleres but keleresFlavor is not set");
                }
                factionAlias = "keleres" + keleresFlavor.charAt(0);
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
                    "keleresflavor_null".equals(tokens[0]) ? null : tokens[0].substring("keleresflavor_".length());
            draftFactions.addAll(Arrays.asList(tokens).subList(1, tokens.length));
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
        if (!(menu instanceof DraftSystemSettings draftSystemSettings)) {
            return "Error: Could not find parent draft system settings.";
        }
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
        List<String> summarizeFlavors = new ArrayList<>();
        boolean hasDraftableFlavor = false;
        for (String flavor : keleresFlavors) {
            if (draftManager.hasBeenPicked(TYPE, flavor)) continue;
            FactionModel flavorFaction = Mapper.getFaction(flavor);
            String factionName = flavorFaction.getFactionName();
            if (!draftEnded && draftFactions.contains(flavor)) {
                factionName += " ⚠️";
                hasDraftableFlavor = true;
            }
            Button button =
                    Buttons.gray(makeButtonId("keleresflavor_" + flavor), factionName, flavorFaction.getFactionEmoji());
            buttons.add(button);
            summarizeFlavors.add(getKeleresSummaryString(flavorFaction));
        }

        if (buttons.isEmpty() && !draftEnded) return;

        Player player = draftManager.getGame().getPlayer(playerUserId);
        String message;
        if (!draftEnded) {
            message = player.getPing()
                    + " Keleres requires you to choose a Hero, which will gain you the associated Home System. You can pre-select an option, which you can change freely until the draft ends.";
            if (hasDraftableFlavor) {
                message +=
                        "\n- ⚠️ Some of these factions are in the draft! ⚠️ Your preset will be canceled if they get chosen (you'll pick something else).";
            }
        } else if (buttons.isEmpty()) {
            for (String flavor : keleresFlavors) {
                FactionModel flavorFaction = Mapper.getFaction(flavor);
                Button button = Buttons.green(
                        makeButtonId("keleresflavor_" + flavor),
                        flavorFaction.getFactionName(),
                        flavorFaction.getFactionEmoji());
                buttons.add(button);
                summarizeFlavors.add(getKeleresSummaryString(flavorFaction));
            }
            message = "*Hrrnnggh*\n" + player.getPing()
                    + " This is awkward, all of the Keleres flavors got drafted. I'll let you pick any of them, but don't do that again!";
        } else {
            message = player.getPing() + ", please choose a flavor of Keleres";
        }

        message += "\n\n" + String.join("\n\n", summarizeFlavors);
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(player.getCardsInfoThread(), message, buttons);
    }

    private String getKeleresSummaryString(FactionModel flavorFaction) {
        FactionModel keleres =
                Mapper.getFaction("keleres" + flavorFaction.getAlias().charAt(0));

        List<String> summaryParts = new ArrayList<>();

        summaryParts.add("**__" + keleres.getFactionName() + "__**");

        List<PlanetModel> homePlanets =
                keleres.getHomePlanets().stream().map(Mapper::getPlanet).toList();
        if (!homePlanets.isEmpty()) {
            List<String> planetStrs = new ArrayList<>();
            for (PlanetModel p : homePlanets) {
                planetStrs.add(PlanetEmojis.getPlanetEmoji(p.getAlias()) + " " + p.getName() + " "
                        + MiscEmojis.getResourceEmoji(p.getResources())
                        + MiscEmojis.getInfluenceEmoji(p.getInfluence()));
            }
            summaryParts.add(String.join(", ", planetStrs));
        }

        List<String> leaderNames = keleres.getLeaders();
        List<LeaderModel> leaders = leaderNames.stream().map(Mapper::getLeader).toList();
        Optional<LeaderModel> heroOpt =
                leaders.stream().filter(l -> l.getType().equals(Constants.HERO)).findFirst();
        if (heroOpt.isPresent()) {
            LeaderModel hero = heroOpt.get();
            summaryParts.add(hero.getLeaderEmoji() + " " + hero.getName() + " - *" + hero.getAbilityWindow() + "* "
                    + hero.getAbilityText());
        }

        return String.join("\n", summaryParts);
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
                if (first) message = player.getRepresentationUnfogged() + ", here is an overview of the factions.";
                MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, e);
                first = false;
            }
            Game game = player.getGame();
            if (!game.isTwilightsFallMode() && game.isThundersEdge()) {
                List<MessageEmbed> teEmbeds = new ArrayList<>();
                for (FactionModel faction : factions) {
                    String btId = faction.getID() + "bt";
                    if (btId.contains("keleres")) {
                        btId = "keleresbt";
                    }
                    if (Mapper.getBreakthrough(btId) != null) {
                        teEmbeds.add(Mapper.getBreakthrough(btId).getRepresentationEmbed());
                    }
                }
                first = true;
                for (MessageEmbed e : teEmbeds) {
                    String message = "";
                    if (first)
                        message = player.getRepresentationUnfogged()
                                + ", here is an overview of the faction breakthroughs.";
                    MessageHelper.sendMessageToChannelWithEmbed(player.getCardsInfoThread(), message, e);
                    first = false;
                }
            }
        }
    }
}
