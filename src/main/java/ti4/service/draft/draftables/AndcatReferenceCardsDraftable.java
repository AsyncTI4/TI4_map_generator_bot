package ti4.service.draft.draftables;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.Getter;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.collections4.ListUtils;
import ti4.buttons.Buttons;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.MapTemplateHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper;
import ti4.helpers.omega_phase.PriorityTrackHelper.PriorityTrackMode;
import ti4.helpers.settingsFramework.menus.AndcatReferenceCardsDraftableSettings;
import ti4.helpers.settingsFramework.menus.DraftSystemSettings;
import ti4.helpers.settingsFramework.menus.SettingsMenu;
import ti4.helpers.settingsFramework.menus.SourceSettings;
import ti4.helpers.twilightsfall.TwilightsFallInfoHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.message.logging.BotLogger;
import ti4.message.logging.LogOrigin;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.service.draft.AndcatReferenceCardsMessageHelper;
import ti4.service.draft.DraftButtonService;
import ti4.service.draft.DraftChoice;
import ti4.service.draft.DraftManager;
import ti4.service.draft.DraftableType;
import ti4.service.draft.PartialMapService;
import ti4.service.draft.PlayerDraftState;
import ti4.service.draft.PlayerSetupService.PlayerSetupState;
import ti4.service.emoji.TI4Emoji;
import ti4.service.milty.MiltyService;
import ti4.service.planet.AddPlanetService;
import ti4.service.unit.AddUnitService;

public class AndcatReferenceCardsDraftable extends SinglePickDraftable {

    public record ReferenceCardPackage(
            Integer key,
            List<String> factions,
            String homeSystemFaction,
            String startingUnitsFaction,
            String speakerOrderFaction,
            Boolean choicesFinal) {}

    @Getter
    private final Map<Integer, ReferenceCardPackage> referenceCardPackages = new HashMap<>();

    public static final DraftableType TYPE = DraftableType.of("AndcatRefPackage");

    @Override
    public DraftableType getType() {
        return TYPE;
    }

    @Override
    public String getDisplayName() {
        return "Faction Reference Cards";
    }

    public String initialize(int numPackages, List<ComponentSource> sources, List<String> bannedFactions) {

        List<String> availableFactions = new ArrayList<>(Mapper.getFactionsValues().stream()
                .filter(f -> !bannedFactions.contains(f.getAlias()))
                .filter(f -> f.getPriorityNumber() != null)
                .filter(f -> sources.contains(f.getSource()))
                .filter(f -> !f.getAlias().contains("keleres")
                        || "keleresm".equals(f.getAlias())) // Limit the pool to only 1 keleres flavor
                .map(FactionModel::getAlias)
                .toList());

        // Sort the available factions by priority number
        List<String> sortedFactions = availableFactions.stream()
                .sorted((p1, p2) -> {
                    FactionModel f1 = Mapper.getFaction(p1);
                    FactionModel f2 = Mapper.getFaction(p2);
                    return Integer.compare(f1.getPriorityNumber(), f2.getPriorityNumber());
                })
                .collect(Collectors.toList());

        // Break factions into 3 same-size groups, segmented by priority number
        int partitionSize = (int) Math.ceil((double) sortedFactions.size() / 3); // Typically 10
        List<List<String>> partitions = ListUtils.partition(sortedFactions, partitionSize);

        // Shuffle each faction group
        for (List<String> partition : partitions) {
            Collections.shuffle(partition);
        }

        // To get a nice distribution of factions, we want to build at least ~80% of all possible packages
        // This results in pulling out more of the powerful/weak factions before picking the fully random final faction.
        int packagesPossible = partitionSize;
        if (numPackages > packagesPossible) {
            return "Not enough factions to fill Andcat Reference Card Packages. "
                    + "Requested " + numPackages + " packages but only "
                    + packagesPossible + " unique packages possible with " + availableFactions.size() + " factions.";
        }
        int buildPackages = Math.max(numPackages, (int) Math.ceil(packagesPossible * 0.8));

        List<List<String>> packages = new ArrayList<>(buildPackages);

        List<String> strongFactions = new ArrayList<>(partitions.get(0));
        List<String> midFactions = new ArrayList<>(partitions.get(1));
        List<String> weakFactions = new ArrayList<>(partitions.get(2));

        // First add powerful factions
        for (int i = 0; i < buildPackages; i++) {
            packages.add(new ArrayList<>());
            packages.get(i).add(strongFactions.remove(0));
        }
        // Then add weak factions
        for (int i = 0; i < buildPackages; i++) {
            packages.get(i).add(weakFactions.remove(0));
        }
        // Then mix ALL remaining factions together
        List<String> remainingFactions = new ArrayList<>();
        remainingFactions.addAll(strongFactions);
        remainingFactions.addAll(weakFactions);
        remainingFactions.addAll(midFactions); // Medium factions last to mix better
        Collections.shuffle(remainingFactions);

        // Finish filling packages from the remaining pool
        // We only need to finish numPackages
        for (int i = 0; i < numPackages; i++) {
            List<String> currentPackage = packages.get(i);
            currentPackage.add(remainingFactions.remove(0));
            currentPackage.sort((p1, p2) -> {
                FactionModel f1 = Mapper.getFaction(p1);
                FactionModel f2 = Mapper.getFaction(p2);
                return Integer.compare(f1.getPriorityNumber(), f2.getPriorityNumber());
            });

            // Add package to finalized list
            int packageKey = i + 1;
            ReferenceCardPackage refCardPackage =
                    new ReferenceCardPackage(packageKey, currentPackage, null, null, null, null);
            referenceCardPackages.put(packageKey, refCardPackage);
        }

        return null;
    }

    public static List<FactionModel> getFactionsInPackage(ReferenceCardPackage refCardPackage) {
        List<FactionModel> factions = new ArrayList<>();
        for (String factionKey : refCardPackage.factions) {
            FactionModel faction = Mapper.getFaction(factionKey);
            if (faction == null) {
                BotLogger.warning(
                        new LogOrigin(),
                        "Cannot find faction model for faction " + factionKey + " in ReferenceCardPackage.");
                continue;
            }
            factions.add(faction);
        }
        return factions;
    }

    public static String getChoiceKey(Integer packageNum) {
        return "package" + packageNum;
    }

    public ReferenceCardPackage getPackageByChoiceKey(String choiceKey) {
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
            String choiceKey = getChoiceKey(packageKey);
            String unformattedName = makePackageText(factionsInPackage, false, false);
            String formattedName = makePackageText(factionsInPackage, true, true);
            String identifyingEmoji = factionsInPackage.stream()
                    .map(FactionModel::getFactionEmoji)
                    .reduce("", String::concat);
            DraftChoice choice = new DraftChoice(
                    TYPE,
                    choiceKey,
                    makeChoiceButton(choiceKey, makePackageText(factionsInPackage, true, false), null),
                    formattedName,
                    unformattedName,
                    identifyingEmoji);
            choices.add(choice);
        }
        return choices;
    }

    private String makePackageText(
            List<FactionModel> factionsInPackage, boolean includePriority, boolean includeEmojis) {
        StringBuilder formattedNameBuilder = new StringBuilder();
        for (int i = 0; i < 3; ++i) {
            FactionModel pFaction = factionsInPackage.get(i);
            if (includeEmojis) {
                formattedNameBuilder.append(pFaction.getFactionEmoji()).append(" ");
            }
            if (pFaction.getShortName().toLowerCase().contains("keleres")) {
                formattedNameBuilder.append("Keleres");
            } else {
                formattedNameBuilder.append(pFaction.getShortName());
            }
            if (includePriority) {
                formattedNameBuilder
                        .append(" [")
                        .append(pFaction.getPriorityNumber())
                        .append("]");
            }
            if (i < 2) {
                formattedNameBuilder.append(", ");
            }
        }
        return formattedNameBuilder.toString();
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
    public String handleCustomCommand(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, String commandKey) {

        List<ReferenceCardPackage> informPackages;
        if ("remaininginfo".equals(commandKey)) {
            informPackages = new ArrayList<>(referenceCardPackages.values());
            for (String pId : draftManager.getPlayerStates().keySet()) {
                List<DraftChoice> playerChoices = draftManager.getPlayerPicks(pId, TYPE);
                if (playerChoices != null) {
                    for (DraftChoice choice : playerChoices) {
                        ReferenceCardPackage refPackage = getPackageByChoiceKey(choice.getChoiceKey());
                        informPackages.remove(refPackage);
                    }
                }
            }
            if (informPackages.isEmpty()) {
                return DraftButtonService.USER_MISTAKE_PREFIX + "No packages remain to show info for.";
            } else {
                new AndcatReferenceCardsMessageHelper(this)
                        .sendPackageInfos(draftManager, playerUserId, informPackages);
            }
        } else if ("pickedinfo".equals(commandKey)) {
            informPackages = new ArrayList<>();
            for (String pId : draftManager.getPlayerStates().keySet()) {
                List<DraftChoice> playerChoices = draftManager.getPlayerPicks(pId, TYPE);
                if (playerChoices != null) {
                    for (DraftChoice choice : playerChoices) {
                        ReferenceCardPackage refPackage = getPackageByChoiceKey(choice.getChoiceKey());
                        if (!informPackages.contains(refPackage)) {
                            informPackages.add(refPackage);
                        }
                    }
                }
            }
            if (informPackages.isEmpty()) {
                return DraftButtonService.USER_MISTAKE_PREFIX + "No packages have been picked yet to show info for.";
            } else {
                new AndcatReferenceCardsMessageHelper(this)
                        .sendPackageInfos(draftManager, playerUserId, informPackages);
            }
        } else if ("allinfo".equals(commandKey)) {
            informPackages = new ArrayList<>(referenceCardPackages.values());
            new AndcatReferenceCardsMessageHelper(this).sendPackageInfos(draftManager, playerUserId, informPackages);
        } else if (commandKey.startsWith("info_")) {
            String[] tokens = commandKey.substring("info_".length()).split("_");
            informPackages =
                    Arrays.stream(tokens).map(this::getPackageByChoiceKey).toList();
            new AndcatReferenceCardsMessageHelper(this).sendPackageInfos(draftManager, playerUserId, informPackages);
        } else if (commandKey.startsWith("assign_")) {
            return new AndcatReferenceCardsMessageHelper(this)
                    .handleAssignButton(event, draftManager, playerUserId, commandKey);
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
    public void postApplyDraftPick(
            GenericInteractionCreateEvent event, DraftManager draftManager, String playerUserId, DraftChoice choice) {
        PartialMapService.tryUpdateMap(draftManager, event, true);
    }

    @Override
    public String save() {
        StringBuilder sb = new StringBuilder();
        for (Entry<Integer, ReferenceCardPackage> entry : referenceCardPackages.entrySet()) {
            Integer packageKey = entry.getKey();
            ReferenceCardPackage refPackage = entry.getValue();
            sb.append("package").append(packageKey).append(SAVE_SEPARATOR);
            sb.append(String.join(".", refPackage.factions())).append(SAVE_SEPARATOR);
            sb.append(refPackage.homeSystemFaction() != null ? refPackage.homeSystemFaction() : "null")
                    .append(SAVE_SEPARATOR);
            sb.append(refPackage.startingUnitsFaction() != null ? refPackage.startingUnitsFaction() : "null")
                    .append(SAVE_SEPARATOR);
            sb.append(refPackage.speakerOrderFaction() != null ? refPackage.speakerOrderFaction() : "null")
                    .append(SAVE_SEPARATOR);
            sb.append(
                            refPackage.choicesFinal() != null
                                    ? refPackage.choicesFinal().toString()
                                    : "null")
                    .append(SAVE_SEPARATOR);
        }
        return sb.toString();
    }

    @Override
    public void load(String data) {
        referenceCardPackages.clear();
        String[] tokens = data.split(SAVE_SEPARATOR);
        for (int i = 0; i + 5 < tokens.length; i += 6) {
            String packageToken = tokens[i];
            if (!packageToken.startsWith("package")) {
                continue;
            }
            Integer packageKey = Integer.parseInt(packageToken.substring("package".length()));
            List<String> factions = List.of(tokens[i + 1].split("\\."));
            String homeSystemFaction = "null".equals(tokens[i + 2]) ? null : tokens[i + 2];
            String startingUnitsFaction = "null".equals(tokens[i + 3]) ? null : tokens[i + 3];
            String speakerOrderFaction = "null".equals(tokens[i + 4]) ? null : tokens[i + 4];
            Boolean choicesFinal = null;
            if (!"null".equals(tokens[i + 5])) {
                choicesFinal = Boolean.parseBoolean(tokens[i + 5]);
            }
            ReferenceCardPackage refPackage = new ReferenceCardPackage(
                    packageKey, factions, homeSystemFaction, startingUnitsFaction, speakerOrderFaction, choicesFinal);
            referenceCardPackages.put(packageKey, refPackage);
        }
    }

    @Override
    // TODO: Add slash-commands to fix these issues
    public String validateState(DraftManager draftManager) {
        int numPlayers = draftManager.getPlayerStates().size();
        if (referenceCardPackages.size() < numPlayers) {
            return "Number of packages (" + referenceCardPackages.size() + ") is less than number of players ("
                    + numPlayers
                    + ").";
        }

        // Ensure all factions in referenceCardPackages are valid and unique
        // Ensure all referenceCardPackages have exactly 3 factions
        // Ensure all referenceCardPackages have only their own factions set for values
        Set<String> distinctFactions = new HashSet<>();
        for (Entry<Integer, ReferenceCardPackage> entry : referenceCardPackages.entrySet()) {
            ReferenceCardPackage refPackage = entry.getValue();
            if (refPackage.factions().size() != 3) {
                return "Reference card package " + entry.getKey() + " does not have exactly 3 factions.";
            }
            for (String factionAlias : refPackage.factions()) {
                FactionModel faction = Mapper.getFaction(factionAlias);
                if (faction == null) {
                    return "Unknown faction in draftFactions: " + factionAlias + ".";
                }

                if (distinctFactions.contains(factionAlias)) {
                    return "Duplicate faction alias in draftFactions: " + factionAlias
                            + ". Remove it with `/draft faction remove`.";
                }
                distinctFactions.add(factionAlias);
            }

            if (refPackage.homeSystemFaction() != null
                    && !refPackage.factions().contains(refPackage.homeSystemFaction())) {
                return "Reference card package " + entry.getKey()
                        + " has home system faction set to a faction not in the package.";
            }
            if (refPackage.startingUnitsFaction() != null
                    && !refPackage.factions().contains(refPackage.startingUnitsFaction())) {
                return "Reference card package " + entry.getKey()
                        + " has starting units faction set to a faction not in the package.";
            }
            if (refPackage.speakerOrderFaction() != null
                    && !refPackage.factions().contains(refPackage.speakerOrderFaction())) {
                return "Reference card package " + entry.getKey()
                        + " has speaker order faction set to a faction not in the package.";
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
        AndcatReferenceCardsDraftableSettings settings = draftSystemSettings.getAndcatReferenceCardsDraftableSettings();
        SourceSettings sourceSettings = draftSystemSettings.getSourceSettings();

        return initialize(
                settings.getNumPackages().getVal(),
                sourceSettings.getFactionSources(),
                settings.getBanFactions().getKeys().stream().toList());
    }

    @Override
    public void onDraftEnd(DraftManager draftManager) {
        // Send buttons to assign reference cards to specific setup things
        AndcatReferenceCardsMessageHelper andcatMessageHelper = new AndcatReferenceCardsMessageHelper(this);
        for (String playerId : draftManager.getPlayerStates().keySet()) {
            List<DraftChoice> playerChoices = draftManager.getPlayerPicks(playerId, TYPE);
            if (playerChoices == null || playerChoices.isEmpty()) {
                continue;
            }
            DraftChoice pick = playerChoices.getFirst();
            ReferenceCardPackage refPackage = getPackageByChoiceKey(pick.getChoiceKey());
            Player player = draftManager.getGame().getPlayer(playerId);

            andcatMessageHelper.sendPackageButtons(draftManager, player, refPackage);
        }
        andcatMessageHelper.updatePackagePickSummary(draftManager);

        // This draftable indicates an Inaugural Splice will be needed
        draftManager.getGame().setStoredValue("needsInauguralSplice", "true");
    }

    @Override
    public String whatsStoppingSetup(DraftManager draftManager) {
        for (String playerUserId : draftManager.getPlayerUserIds()) {
            Player player = draftManager.getGame().getPlayer(playerUserId);
            List<DraftChoice> playerChoices = draftManager.getPlayerPicks(playerUserId, TYPE);
            if (playerChoices == null || playerChoices.isEmpty()) {
                return "Player " + player.getRepresentation() + " has not picked a reference card package.";
            }

            DraftChoice pick = playerChoices.getFirst();
            ReferenceCardPackage refPackage = getPackageByChoiceKey(pick.getChoiceKey());
            if (refPackage.homeSystemFaction() == null) {
                return "Player " + player.getRepresentation() + " has not assigned a home system faction.";
            }
            if (refPackage.startingUnitsFaction() == null) {
                return "Player " + player.getRepresentation() + " has not assigned a starting units faction.";
            }
            if (refPackage.speakerOrderFaction() == null) {
                return "Player " + player.getRepresentation() + " has not assigned a speaker order priority faction.";
            }
            if (refPackage.choicesFinal() == null || !refPackage.choicesFinal()) {
                return "Player " + player.getRepresentation() + " has not finalized their reference card assignments.";
            }
        }
        return null;
    }

    @Override
    public Consumer<Player> setupPlayer(
            DraftManager draftManager, String playerUserId, PlayerSetupState playerSetupState) {

        Game game = draftManager.getGame();
        List<DraftChoice> playerChoices = draftManager.getPlayerPicks(playerUserId, TYPE);
        if (playerChoices == null || playerChoices.isEmpty()) {
            return null;
        }
        DraftChoice pick = playerChoices.getFirst();
        ReferenceCardPackage refPackage = getPackageByChoiceKey(pick.getChoiceKey());

        // Determine speaker position and set home system location
        List<String> speakerOrder = getSpeakerOrder(draftManager);
        int speakerPosition = speakerOrder.indexOf(playerUserId) + 1;
        playerSetupState.setSetSpeaker(speakerPosition == 1);
        String homeTilePosition =
                MapTemplateHelper.getPlayerHomeSystemLocation(speakerPosition, game.getMapTemplateID());
        if (shouldAlsoSetSeat(draftManager)) {
            playerSetupState.setPositionHS(homeTilePosition);
        }

        // If the home system faction is Keleres, we switch them here to a random unused faction.
        ReferenceCardPackage updatedPackage;
        if (refPackage.homeSystemFaction() != null
                && refPackage.homeSystemFaction().startsWith("keleres")) {

            FactionModel newKeleresFaction = getKeleresHomeSystemFaction(draftManager);
            if (newKeleresFaction != null) {
                String sb = game.getPlayer(playerUserId).getRepresentation()
                        + " had Keleres as their home system faction, and drew a random unused home system tile."
                        + System.lineSeparator()
                        + TwilightsFallInfoHelper.getFactionSetupInfo(newKeleresFaction, false, true, false);
                MessageHelper.sendMessageToChannel(game.getActionsChannel(), sb);

                updatedPackage = new ReferenceCardPackage(
                        refPackage.key(),
                        refPackage.factions(),
                        newKeleresFaction.getAlias(),
                        refPackage.startingUnitsFaction(),
                        refPackage.speakerOrderFaction(),
                        refPackage.choicesFinal());
                referenceCardPackages.put(refPackage.key(), updatedPackage);
            } else {
                BotLogger.error(
                        new LogOrigin(game),
                        Constants.jabberwockyPing()
                                + " Could not find a new faction for Keleres home system assignment.");
                updatedPackage = refPackage;
            }
        } else {
            updatedPackage = refPackage;
        }

        // After setting a home system position, the home system + slice can be placed
        PartialMapService.tryUpdateMap(draftManager, null, true);

        // Place planets and units after faction and color are properly set
        return (Player player) -> doPostSetupWork(draftManager, player, updatedPackage);
    }

    private void doPostSetupWork(DraftManager draftManager, Player player, ReferenceCardPackage refPackage) {

        Game game = draftManager.getGame();
        List<String> speakerOrder = getSpeakerOrder(draftManager);
        int speakerPosition = speakerOrder.indexOf(player.getUserID()) + 1;

        // If not setting seat, Speaker Order needs to be set via Priority Track
        if (!shouldAlsoSetSeat(draftManager)) {
            if (game.getPriorityTrackMode() == PriorityTrackMode.NONE) {
                game.setPriorityTrackMode(PriorityTrackMode.THIS_ROUND_ONLY);
            }
            PriorityTrackHelper.AssignPlayerToPriority(draftManager.getGame(), player, speakerPosition);
        }

        // Get the HS tile
        String homeTilePosition =
                MapTemplateHelper.getPlayerHomeSystemLocation(speakerPosition, game.getMapTemplateID());
        Tile hsTile = game.getTileByPosition(homeTilePosition);

        // Add home system extra tiles if needed
        FactionModel homeSystemFaction = Mapper.getFaction(refPackage.homeSystemFaction());
        MiltyService.setupExtraFactionTiles(game, player, homeSystemFaction.getAlias(), homeTilePosition, hsTile);
        if (player.getHomeSystemPosition() != null
                && !"null".equals(player.getHomeSystemPosition())
                && !player.getHomeSystemPosition().isEmpty()) {
            // Some home systems get changed, e.g. Ghosts
            homeTilePosition = player.getHomeSystemPosition();
            hsTile = game.getTileByPosition(player.getHomeSystemPosition());
        }

        // Add home system planets to player, refreshed
        for (String planet : homeSystemFaction.getHomePlanets()) {
            if (planet.isEmpty()) {
                continue;
            }
            String planetResolved = AliasHandler.resolvePlanet(planet.toLowerCase());
            AddPlanetService.addPlanet(player, planetResolved, game, null, true);
            player.refreshPlanet(planetResolved);
        }

        // Clear existing units at the home system
        hsTile.removeAllUnits(player.getColor());

        // Parse and add units
        FactionModel startingUnitsFaction = Mapper.getFaction(refPackage.startingUnitsFaction());
        AddUnitService.addUnitsToDefaultLocations(
                null, hsTile, game, player.getColor(), startingUnitsFaction.getStartingFleet());
    }

    public List<String> getSpeakerOrder(DraftManager draftManager) {
        List<Entry<String, PlayerDraftState>> playerStates = new ArrayList<>();

        // Ensure the speaker order is set
        for (Entry<String, PlayerDraftState> entry :
                draftManager.getPlayerStates().entrySet()) {
            PlayerDraftState pState = entry.getValue();
            if (pState.getPickCount(TYPE) == 0) {
                return Collections.emptyList();
            }
            String choiceKey = pState.getPicks(TYPE).getFirst().getChoiceKey();
            ReferenceCardPackage refPackage = getPackageByChoiceKey(choiceKey);
            if (refPackage.speakerOrderFaction() == null) {
                return Collections.emptyList();
            }
            if (refPackage.choicesFinal() == null || !refPackage.choicesFinal()) {
                return Collections.emptyList();
            }

            playerStates.add(entry);
        }

        // Sort player states by speaker order priority number
        playerStates.sort((p1, p2) -> {
            DraftChoice p1Choice = p1.getValue().getPicks(TYPE).getFirst();
            ReferenceCardPackage p1Package = getPackageByChoiceKey(p1Choice.getChoiceKey());
            FactionModel p1SpeakerFaction = Mapper.getFaction(p1Package.speakerOrderFaction());

            DraftChoice p2Choice = p2.getValue().getPicks(TYPE).getFirst();
            ReferenceCardPackage p2Package = getPackageByChoiceKey(p2Choice.getChoiceKey());
            FactionModel p2SpeakerFaction = Mapper.getFaction(p2Package.speakerOrderFaction());

            return Integer.compare(p1SpeakerFaction.getPriorityNumber(), p2SpeakerFaction.getPriorityNumber());
        });

        return playerStates.stream().map(Entry::getKey).toList();
    }

    private boolean shouldAlsoSetSeat(DraftManager draftManager) {
        return draftManager.getDraftables().stream().noneMatch(d -> d instanceof SeatDraftable);
    }

    private FactionModel getKeleresHomeSystemFaction(DraftManager draftManager) {
        // Try and lookup the faction sources to get a list of possible factions
        Game game = draftManager.getGame();
        List<ComponentSource> factionSources;
        List<String> bannedFactions;
        if (game.getDraftSystemSettingsUnsafe() != null || game.getDraftSystemSettingsJson() != null) {
            factionSources =
                    game.initializeDraftSystemSettings().getSourceSettings().getFactionSources();
            bannedFactions = game
                    .initializeDraftSystemSettings()
                    .getAndcatReferenceCardsDraftableSettings()
                    .getBanFactions()
                    .getKeys()
                    .stream()
                    .toList();
        } else {
            BotLogger.warning(
                    new LogOrigin(game),
                    "Could not find draft system settings to lookup faction sources for Keleres home system assignment.");
            factionSources = List.of(ComponentSource.base, ComponentSource.pok, ComponentSource.thunders_edge);
            bannedFactions = List.of();
        }

        List<String> usedFactions = new ArrayList<>();
        for (ReferenceCardPackage packages : referenceCardPackages.values()) {
            if (packages.homeSystemFaction() != null) {
                usedFactions.add(packages.homeSystemFaction());
            }
        }

        List<FactionModel> availableFactions = Mapper.getFactionsValues().stream()
                .filter(f -> !bannedFactions.contains(f.getAlias()))
                .filter(f -> factionSources.contains(f.getSource()))
                .filter(f -> !usedFactions.contains(f.getAlias()))
                .filter(f -> f.getPriorityNumber() != null)
                .filter(f -> !f.getAlias().contains("keleres"))
                .collect(Collectors.toList());
        Collections.shuffle(availableFactions);
        return availableFactions.isEmpty() ? null : availableFactions.get(0);
    }
}
