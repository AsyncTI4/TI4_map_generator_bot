package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.migo;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechSpecialtyModel;
import ti4.model.TechSpecialtyModel.TechSpecialty;
import ti4.model.TechnologyModel;
import ti4.service.info.AbilityInfoService;

@UtilityClass
public class MigoAbilityHandler {

    private static final String HARVEST = "harvest";
    private static final String PSEUDOMORPHISM = "migoPseudomorphism_";
    private static final String PSEUDOMORPHISM_SPENT = "pseudomorphism_";
    private static final Map<TechSpecialty, String> HARVEST_CARDS = Map.of(
            TechSpecialty.PROPULSION, "harvest_blue",
            TechSpecialty.BIOTIC, "harvest_green",
            TechSpecialty.WARFARE, "harvest_red",
            TechSpecialty.CYBERNETIC, "harvest_yellow");
    private static final String YUGGOTH = "yuggoth";
    private static final String CEREBRAL_ARCHIVE = "migoCerebralArchive_";
    private static final String CEREBRAL_ARCHIVE_RESEARCH = "migoCerebralArchiveResearch";
    private static final String MECH_HARVEST_ATTACHMENT = "attachment_migomechspec.png";
    private static final String MECH_HARVEST_CARD = "migoMechHarvestCard_";
    private static final String MECH_HARVEST_PROMPT = "migoMechHarvestPrompt_";
    private static final String CHOOSE_MECH_HARVEST = "chooseMechHarvest_";

    public static void offerCerebralArchive(Game game, Player player, TechnologyModel technology) {
        int prerequisites = technology.getRequirements().orElse("").length();
        if (!player.hasPlanet(YUGGOTH)
                || player.getExhaustedPlanetsAbilities().contains(YUGGOTH)
                || prerequisites < 1) {
            return;
        }
        int commoditiesOnArchive = game.changeCommsOnPlanet(0, YUGGOTH);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", _Cerebral Archive_ currently has "
                        + commoditiesOnArchive
                        + " commodit"
                        + (commoditiesOnArchive == 1 ? "y" : "ies")
                        + " on it. You may exhaust it to place "
                        + prerequisites
                        + " more.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + CEREBRAL_ARCHIVE + technology.getAlias(),
                                "Use Cerebral Archive"),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler(CEREBRAL_ARCHIVE)
    public static void useCerebralArchive(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techId = buttonID.substring(CEREBRAL_ARCHIVE.length());
        TechnologyModel technology = Mapper.getTech(techId);

        if (!player.hasPlanet(YUGGOTH)
                || player.getExhaustedPlanetsAbilities().contains(YUGGOTH)
                || technology == null
                || !player.hasTech(techId)) {
            return;
        }

        int prerequisites = technology.getRequirements().orElse("").length();
        if (prerequisites < 1) {
            return;
        }

        player.exhaustPlanetAbility(YUGGOTH);
        int totalCommodities = game.changeCommsOnPlanet(prerequisites, YUGGOTH);

        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " exhausted _Cerebral Archive_ and placed "
                        + prerequisites
                        + " commodit"
                        + (prerequisites == 1 ? "y" : "ies")
                        + " on it. It now has "
                        + totalCommodities
                        + ".");

        ButtonHelper.deleteMessage(event);
    }

    public static void offerCerebralArchiveResearch(Game game, Player player) {
        int commoditiesOnArchive = game.changeCommsOnPlanet(0, YUGGOTH);
        if (!player.hasPlanet(YUGGOTH) || commoditiesOnArchive < 2) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation()
                        + ", _Cerebral Archive_ has "
                        + commoditiesOnArchive
                        + " commodities on it. You may remove 2 to research 1 technology.",
                List.of(
                        Buttons.green(
                                player.factionButtonChecker() + CEREBRAL_ARCHIVE_RESEARCH, "Research 1 Technology"),
                        Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler(CEREBRAL_ARCHIVE_RESEARCH)
    public static void researchWithCerebralArchive(ButtonInteractionEvent event, Game game, Player player) {
        if (!player.hasPlanet(YUGGOTH) || game.changeCommsOnPlanet(0, YUGGOTH) < 2) {
            return;
        }

        game.changeCommsOnPlanet(-2, YUGGOTH);

        MessageHelper.sendMessageToChannelWithButton(
                player.getCorrectChannel(),
                player.getRepresentation() + " removed 2 commodities from _Cerebral Archive_ to research 1 technology.",
                Buttons.green(player.factionButtonChecker() + "acquireAFreeTech", "Research 1 Technology"));

        ButtonHelper.deleteMessage(event);
    }

    public static void offerPseudomorphism(
            ButtonInteractionEvent event, Game game, Player player, String planetId, String whatIsItFor) {
        if (!whatIsItFor.contains("tech")
                || !player.hasAbility("pseudomorphism")
                || !hasTechSpecialty(game, planetId)) {
            return;
        }

        Planet planet = game.getPlanetsInfo().get(planetId);
        if (planet == null) {
            return;
        }

        Button usePseudomorphism = Buttons.green(
                player.factionButtonChecker() + PSEUDOMORPHISM + planetId,
                "Use Pseudomorphism (+" + planet.getInfluence() + " resources)");

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", you exhausted "
                        + Helper.getPlanetRepresentation(planetId, game)
                        + " while paying for technology. You may use **Pseudomorphism** to discount this tech by "
                        + planet.getInfluence()
                        + ".",
                List.of(usePseudomorphism, Buttons.DECLINE_DELETE_BUTTONS));
    }

    @ButtonHandler(PSEUDOMORPHISM)
    public static void usePseudomorphism(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String planetId = buttonID.substring(PSEUDOMORPHISM.length());

        if (!player.hasAbility("pseudomorphism")
                || !hasTechSpecialty(game, planetId)
                || player.getSpentThingsThisWindow().contains(PSEUDOMORPHISM_SPENT + planetId)) {
            return;
        }

        player.addSpentThing(PSEUDOMORPHISM_SPENT + planetId);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), Helper.buildSpentThingsMessage(player, game, "restech"));
        ButtonHelper.deleteMessage(event);
    }

    public static boolean hasTechSpecialty(Game game, String planetId) {
        Planet planet = game.getPlanetsInfo().get(planetId);
        return planet != null && !planet.getTechSpecialities().isEmpty();
    }

    public static void onPlanetGained(Player player, String planetId) {
        if (!player.hasAbility(HARVEST)) {
            return;
        }

        Planet planet = player.getGame().getPlanetsInfo().get(planetId);
        if (planet == null || planet.getTechSpecialities().isEmpty()) {
            return;
        }

        List<String> gainedCards = planet.getTechSpecialities().stream()
                .map(TechSpecialtyModel::getTechSpecialtyFromString)
                .map(HARVEST_CARDS::get)
                .filter(Objects::nonNull)
                .filter(card -> !player.hasAbility(card))
                .toList();

        if (gainedCards.isEmpty()) {
            return;
        }

        gainedCards.forEach(player::addAbility);
        AbilityInfoService.sendAbilityInfo(player);
        announceHarvestCardsGained(
                player,
                gainedCards,
                "after gaining control of " + Helper.getPlanetRepresentation(planetId, player.getGame()));
    }

    public static void reconcileHarvestCards(Player player) {
        reconcileHarvestCards(player.getGame(), player);
    }

    public static void reconcileHarvestCards(Game game, Player player) {
        reconcileHarvestCards(game, player, true);
    }

    public static void syncHarvestCards(Game game, Player player) {
        reconcileHarvestCards(game, player, false);
    }

    private static void reconcileHarvestCards(Game game, Player player, boolean notify) {
        syncMechHarvestChoices(game, player);
        List<String> returnedCards = HARVEST_CARDS.entrySet().stream()
                .filter(entry -> player.hasAbility(entry.getValue()))
                .filter(entry -> !player.hasAbility(HARVEST)
                        || (!controlsSpecialty(game, player, entry.getKey())
                                && !hasMechHarvestCard(game, player, entry.getValue())))
                .map(Map.Entry::getValue)
                .toList();

        if (returnedCards.isEmpty()) {
            return;
        }

        returnedCards.forEach(player::removeAbility);
        if (notify) {
            AbilityInfoService.sendAbilityInfo(player);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), harvestCardsReturnedMessage(player, returnedCards));
        }
    }

    private static boolean controlsSpecialty(Game game, Player player, TechSpecialty specialty) {
        return player.getPlanets().stream()
                .map(game.getPlanetsInfo()::get)
                .filter(Objects::nonNull)
                .map(Planet::getTechSpecialities)
                .anyMatch(specialties -> specialties.stream().anyMatch(specialty.toString()::equalsIgnoreCase));
    }

    private static void syncMechHarvestChoices(Game game, Player player) {
        for (Planet planet : game.getPlanetsInfo().values()) {
            String cardKey = mechHarvestCardKey(player, planet.getName());
            boolean hasMechHarvestSource = hasMechHarvestSource(player, planet);
            if (!hasMechHarvestSource) {
                game.removeStoredValue(cardKey);
                game.removeStoredValue(mechHarvestPromptKey(player, planet.getName()));
                continue;
            }

            String card = game.getStoredValue(cardKey);
            if (HARVEST_CARDS.containsValue(card)) {
                if (!player.hasAbility(card)) {
                    player.addAbility(card);
                }
                continue;
            }
        }
    }

    public static void offerMechHarvestChoices(Game game, Player player) {
        for (Planet planet : game.getPlanetsInfo().values()) {
            if (hasMechHarvestSource(player, planet)
                    && game.getStoredValue(mechHarvestCardKey(player, planet.getName()))
                            .isBlank()) {
                offerMechHarvestChoice(game, player, planet);
            }
        }
    }

    private static void offerMechHarvestChoice(Game game, Player player, Planet planet) {
        String promptKey = mechHarvestPromptKey(player, planet.getName());
        if (!game.getStoredValue(promptKey).isBlank()) {
            return;
        }

        List<Button> buttons = HARVEST_CARDS.values().stream()
                .filter(card -> !player.hasAbility(card))
                .map(card -> Mapper.getAbility(card))
                .filter(Objects::nonNull)
                .map(ability -> Buttons.green(
                        player.factionButtonChecker() + CHOOSE_MECH_HARVEST + planet.getName() + "~"
                                + ability.getAlias(),
                        ability.getName()))
                .toList();
        if (buttons.isEmpty()) {
            return;
        }

        game.setStoredValue(promptKey, "true");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose 1 Harvest card to gain from your Nevermen on "
                        + Helper.getPlanetRepresentation(planet.getName(), game) + ".",
                buttons);
    }

    @ButtonHandler(CHOOSE_MECH_HARVEST)
    public static void chooseMechHarvest(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String choice = buttonID.substring(CHOOSE_MECH_HARVEST.length());
        int separator = choice.lastIndexOf('~');
        if (separator < 1) {
            return;
        }
        String planetName = choice.substring(0, separator);
        String card = choice.substring(separator + 1);
        Planet planet = game.getPlanetsInfo().get(planetName);
        if (!player.hasAbility(HARVEST)
                || !HARVEST_CARDS.containsValue(card)
                || planet == null
                || !hasMechHarvestSource(player, planet)
                || player.hasAbility(card)
                || !game.getStoredValue(mechHarvestCardKey(player, planetName)).isBlank()) {
            return;
        }

        game.setStoredValue(mechHarvestCardKey(player, planetName), card);
        game.removeStoredValue(mechHarvestPromptKey(player, planetName));
        if (!player.hasAbility(card)) {
            player.addAbility(card);
            AbilityInfoService.sendAbilityInfo(player);
            announceHarvestCardsGained(
                    player,
                    List.of(card),
                    "from their Nevermen on " + Helper.getPlanetRepresentation(planetName, game));
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasMechHarvestCard(Game game, Player player, String card) {
        return game.getPlanetsInfo().values().stream()
                .filter(planet -> hasMechHarvestSource(player, planet))
                .map(planet -> game.getStoredValue(mechHarvestCardKey(player, planet.getName())))
                .anyMatch(card::equals);
    }

    private static boolean hasMechHarvestSource(Player player, Planet planet) {
        return player.getPlanets().contains(planet.getName())
                && planet.getTokenList().contains(MECH_HARVEST_ATTACHMENT)
                && MigoUnitHandler.hasNevermenSpecialty(player, planet);
    }

    private static String mechHarvestCardKey(Player player, String planetName) {
        return MECH_HARVEST_CARD + player.getFaction() + "_" + planetName;
    }

    private static String mechHarvestPromptKey(Player player, String planetName) {
        return MECH_HARVEST_PROMPT + player.getFaction() + "_" + planetName;
    }

    private static String formatHarvestCards(List<String> cards) {
        return cards.stream()
                .map(Mapper::getAbility)
                .filter(Objects::nonNull)
                .map(ability -> "**" + ability.getName() + "**")
                .collect(Collectors.joining(" and "));
    }

    private static void announceHarvestCardsGained(Player player, List<String> cards, String reason) {
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " gained " + formatHarvestCards(cards) + " due to **Harvest** " + reason
                        + ".");
    }

    private static String harvestCardsReturnedMessage(Player player, List<String> cards) {
        return player.getRepresentation() + " returned " + formatHarvestCards(cards)
                + " to their reinforcements because "
                + cards.stream()
                        .map(card -> harvestCardReturnReason(player, card))
                        .collect(Collectors.joining(" and "))
                + ".";
    }

    private static String harvestCardReturnReason(Player player, String card) {
        if (!player.hasAbility(HARVEST)) {
            return "they no longer have **Harvest**";
        }
        TechSpecialty specialty = HARVEST_CARDS.entrySet().stream()
                .filter(entry -> entry.getValue().equals(card))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (specialty == null) {
            return "its source is no longer available";
        }
        return "they no longer control a planet with a " + specialty.getEmoji() + " " + specialty
                + " technology specialty or have Nevermen providing it";
    }
}
