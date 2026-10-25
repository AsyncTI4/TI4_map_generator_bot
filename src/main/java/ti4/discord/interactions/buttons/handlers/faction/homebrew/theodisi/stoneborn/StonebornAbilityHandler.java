package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class StonebornAbilityHandler {
    private static final String TROVANTOUS = "trovantous";
    private static final String FRAGMENTUM_UNLEASHED = "fragmentum_unleashed";
    private static final String DRAWN_FRAGMENTS = "stonebornDrawnFragments";
    private static final String FRAGMENTUM_TOKENS = "fragmentumTokens_";
    private static final String PLACE_FRAGMENTUM = "placeFragmentum_";
    private static final String ADAPTIVE = "adaptive";
    private static final String USE_ADAPTIVE = "useAdaptive";
    private static final String ADAPTIVE_COMPONENT = "adaptiveComponent_";
    private static final String ADAPTIVE_COST = "adaptiveCost_";
    private static final List<String> FRAGMENTUM_TRAITS = List.of("cultural", "hazardous", "industrial", "frontier");
    private static final List<String> FRAGMENTS = List.of(
            "stonebornculturalfragment",
            "stonebornculturalfragment2",
            "stonebornhazardousfragment",
            "stonebornhazardousfragment2",
            "stonebornindustrialfragment",
            "stonebornindustrialfragment2",
            "stonebornunknownfragment",
            "stonebornunknownfragment2");

    public static void drawTrovantousFragment(Game game, Player player) {
        if (!player.hasAbility(TROVANTOUS)) {
            return;
        }

        drawRandomStonebornFragment(game, player, "Trovantous");
    }

    public static String drawRandomStonebornFragment(Game game, Player player, String source) {
        List<String> availableFragments = getRemainingStonebornFragments(game);
        if (availableFragments.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    player.getRepresentation() + ", there are no Stoneborn relic fragments remaining.");
            return null;
        }

        String fragment = availableFragments.get(ThreadLocalRandom.current().nextInt(availableFragments.size()));
        List<String> drawnFragments =
                new ArrayList<>(List.of(game.getStoredValue(DRAWN_FRAGMENTS).split(",")));
        drawnFragments.removeIf(String::isEmpty);
        drawnFragments.add(fragment);
        game.setStoredValue(DRAWN_FRAGMENTS, String.join(",", drawnFragments));
        player.addFragment(fragment);
        ExploreModel fragmentModel = Mapper.getExplore(fragment);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentation() + " drew _" + fragmentModel.getName() + "_ with _" + source + "_.");
        return fragment;
    }

    public static List<String> getRemainingStonebornFragments(Game game) {
        List<String> drawnFragments =
                new ArrayList<>(List.of(game.getStoredValue(DRAWN_FRAGMENTS).split(",")));
        drawnFragments.removeIf(String::isEmpty);
        return FRAGMENTS.stream()
                .filter(fragment -> !drawnFragments.contains(fragment))
                .toList();
    }

    public static boolean returnPurgedFragment(Game game, String fragment) {
        if (!FRAGMENTS.contains(fragment)) {
            return false;
        }

        List<String> drawnFragments =
                new ArrayList<>(List.of(game.getStoredValue(DRAWN_FRAGMENTS).split(",")));
        drawnFragments.removeIf(fragment::equals);
        game.setStoredValue(DRAWN_FRAGMENTS, String.join(",", drawnFragments));
        return true;
    }

    public static void offerFragmentumTokenPlacement(
            Game game, Player player, GenericInteractionCreateEvent event, List<String> fragments) {
        if (!player.hasAbility(FRAGMENTUM_UNLEASHED) || fragments.isEmpty()) {
            return;
        }

        List<String> traits = fragments.stream()
                .map(Mapper::getExplore)
                .filter(Objects::nonNull)
                .flatMap(fragment -> getFragmentumTraits(fragment).stream())
                .distinct()
                .filter(trait -> getFragmentumTokenCount(game, player, trait) < 3)
                .toList();
        if (traits.isEmpty()) {
            return;
        }
        List<Button> buttons = traits.stream()
                .map(trait -> Buttons.gray(
                        player.factionButtonChecker() + PLACE_FRAGMENTUM + trait,
                        "Place " + getFragmentumTokenColor(trait) + " Fragmentum Token"))
                .toList();

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentation()
                        + ", you may place 1 matching Fragmentum token on your faction sheet for **Fragmentum Unleashed**.",
                buttons);
    }

    @ButtonHandler(PLACE_FRAGMENTUM)
    public static void placeFragmentumToken(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String trait = buttonID.substring(PLACE_FRAGMENTUM.length());
        if (!player.hasAbility(FRAGMENTUM_UNLEASHED) || !addFragmentumToken(game, player, trait)) {
            return;
        }

        MessageHelper.sendMessageToChannel(
                getOutputChannel(game, player, event),
                player.getRepresentationNoPing() + " placed 1 " + getFragmentumTokenColor(trait)
                        + " Fragmentum token.");
        event.getMessage().delete().queue();
    }

    public static int getFragmentumTokenCount(Game game, Player player, String trait) {
        return (int) Math.min(
                3,
                getFragmentumTokens(game, player).stream()
                        .filter(trait::equalsIgnoreCase)
                        .count());
    }

    public static boolean addFragmentumToken(Game game, Player player, String trait) {
        if (!FRAGMENTUM_TRAITS.contains(trait) || getFragmentumTokenCount(game, player, trait) >= 3) {
            return false;
        }
        List<String> tokens = getFragmentumTokens(game, player);
        tokens.add(trait);
        game.setStoredValue(getFragmentumTokensKey(player), String.join(",", tokens));
        CommanderUnlockCheckService.checkPlayer(player, "stoneborn");
        return true;
    }

    public static boolean removeFragmentumToken(Game game, Player player, String trait) {
        if (!FRAGMENTUM_TRAITS.contains(trait)) {
            return false;
        }
        List<String> tokens = getFragmentumTokens(game, player);
        if (!tokens.remove(trait)) {
            return false;
        }
        game.setStoredValue(getFragmentumTokensKey(player), String.join(",", tokens));
        return true;
    }

    public static Button getFragmentumTokensInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + "showFragmentumTokens",
                "View Fragmentum Tokens",
                FactionEmojis.stoneborn);
    }

    @ButtonHandler("showFragmentumTokens")
    public static void showFragmentumTokens(Game game, Player player, ButtonInteractionEvent event) {
        if (!player.hasAbility(FRAGMENTUM_UNLEASHED)) {
            return;
        }
        MessageHelper.sendMessageToChannel(
                getOutputChannel(game, player, event),
                player.getRepresentationNoPing() + " has the following Fragmentum tokens:\n"
                        + "- Blue: " + getFragmentumTokenCount(game, player, "cultural") + "/3\n"
                        + "- Red: " + getFragmentumTokenCount(game, player, "hazardous") + "/3\n"
                        + "- Green: " + getFragmentumTokenCount(game, player, "industrial") + "/3\n"
                        + "- Gray: " + getFragmentumTokenCount(game, player, "frontier") + "/3");
    }

    public static String getFragmentumTokenColor(String trait) {
        return switch (trait.toLowerCase()) {
            case "cultural" -> "Blue";
            case "hazardous" -> "Red";
            case "industrial" -> "Green";
            default -> "Gray";
        };
    }

    public static Button getAdaptiveStartTurnButton(Game game, Player player) {
        if (!player.hasAbility(ADAPTIVE) || getAdaptiveComponents(game, player).isEmpty()) {
            return null;
        }
        return Buttons.green(
                player.factionButtonChecker() + USE_ADAPTIVE, "UNLEASH a Stoneborn Component", FactionEmojis.stoneborn);
    }

    @ButtonHandler(USE_ADAPTIVE)
    public static void useAdaptive(Game game, Player player, ButtonInteractionEvent event) {
        if (!offerAdaptiveComponentButtons(game, player, getOutputChannel(game, player, event))) {
            return;
        }
        ButtonHelper.deleteTheOneButton(event);
    }

    public static boolean offerAdaptiveComponentButtons(Game game, Player player, MessageChannel channel) {
        if (!player.hasAbility(ADAPTIVE)) {
            return false;
        }
        List<Button> buttons = getAdaptiveComponents(game, player).stream()
                .map(component -> Buttons.green(
                        player.factionButtonChecker() + ADAPTIVE_COMPONENT + component.id, "UNLEASH " + component.name))
                .toList();
        if (buttons.isEmpty()) {
            return false;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                channel, player.getRepresentation() + ", choose a Dormant component to UNLEASH.", buttons);
        return true;
    }

    @ButtonHandler(ADAPTIVE_COMPONENT)
    public static void selectAdaptiveComponent(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        AdaptiveComponent component = AdaptiveComponent.fromId(buttonID.substring(ADAPTIVE_COMPONENT.length()));
        if (component == null || !canUnleash(game, player, component)) {
            return;
        }

        List<List<String>> costs = getAffordableCosts(game, player, component.cost);
        if (costs.isEmpty()) {
            return;
        }
        List<Button> buttons = costs.stream()
                .map(cost -> Buttons.green(
                        player.factionButtonChecker() + ADAPTIVE_COST + component.id + "_" + String.join("-", cost),
                        "Return " + formatCost(cost)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                getOutputChannel(game, player, event),
                player.getRepresentation() + ", return the required Fragmentum tokens to UNLEASH " + component.name
                        + ".",
                buttons);
        event.getMessage().delete().queue();
    }

    @ButtonHandler(ADAPTIVE_COST)
    public static void payAdaptiveCost(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] parts = buttonID.substring(ADAPTIVE_COST.length()).split("_", 2);
        if (parts.length != 2) {
            return;
        }
        AdaptiveComponent component = AdaptiveComponent.fromId(parts[0]);
        List<String> cost = List.of(parts[1].split("-"));
        if (component == null || !canUnleash(game, player, component) || !isValidCost(component.cost, cost)) {
            return;
        }
        List<String> tokens = getFragmentumTokens(game, player);
        for (String trait : cost) {
            if (!tokens.remove(trait)) {
                return;
            }
        }
        game.setStoredValue(getFragmentumTokensKey(player), String.join(",", tokens));
        unleash(game, player, component);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " returned " + formatCost(cost) + " and UNLEASHED " + component.name
                        + ".",
                getUnleashedComponentEmbed(component));
        event.getMessage().delete().queue();
    }

    private static List<AdaptiveComponent> getAdaptiveComponents(Game game, Player player) {
        return List.of(AdaptiveComponent.values()).stream()
                .filter(component -> canUnleash(game, player, component))
                .filter(component ->
                        !getAffordableCosts(game, player, component.cost).isEmpty())
                .toList();
    }

    private static boolean canUnleash(Game game, Player player, AdaptiveComponent component) {
        return switch (component) {
            case AGENT, COMMANDER, HERO -> player.getLeader(component.dormant).isPresent();
            case XENOLITH -> player.hasTech(component.dormant);
            case STALACTIUM -> player.hasTech(component.dormant) && player.hasUnit("stoneborn_dreadnought2dormant");
            case GOLEM -> player.hasUnit(component.dormant);
            case REFRAGMENT -> player.hasBreakthrough(component.dormant);
            case PETRA -> player.hasPlanet(component.dormant) && game.getTileFromPlanet(component.dormant) != null;
        };
    }

    private static List<List<String>> getAffordableCosts(Game game, Player player, AdaptiveCost cost) {
        List<String> tokens = getFragmentumTokens(game, player).stream()
                .filter(trait -> cost.requiredTrait == null || cost.requiredTrait.equals(trait))
                .toList();
        List<List<String>> costs = new ArrayList<>();
        buildCosts(tokens, cost.count, cost.different, cost.same, new ArrayList<>(), costs);
        return costs.stream()
                .distinct()
                .sorted(Comparator.comparing(StonebornAbilityHandler::formatCost))
                .toList();
    }

    private static void buildCosts(
            List<String> tokens,
            int count,
            boolean different,
            boolean same,
            List<String> selected,
            List<List<String>> costs) {
        if (selected.size() == count) {
            costs.add(selected.stream().sorted().toList());
            return;
        }
        for (int index = 0; index < tokens.size(); index++) {
            String trait = tokens.get(index);
            if (different && selected.contains(trait)) {
                continue;
            }
            if (same && !selected.isEmpty() && !selected.getFirst().equals(trait)) {
                continue;
            }
            List<String> remaining = new ArrayList<>(tokens);
            remaining.remove(index);
            selected.add(trait);
            buildCosts(remaining, count, different, same, selected, costs);
            selected.removeLast();
        }
    }

    private static boolean isValidCost(AdaptiveCost expected, List<String> cost) {
        if (cost.size() != expected.count || cost.stream().anyMatch(trait -> !FRAGMENTUM_TRAITS.contains(trait))) {
            return false;
        }
        return (expected.requiredTrait == null || cost.stream().allMatch(expected.requiredTrait::equals))
                && (!expected.different || cost.stream().distinct().count() == cost.size())
                && (!expected.same || cost.stream().distinct().count() == 1);
    }

    private static String formatCost(List<String> cost) {
        return cost.stream()
                        .map(StonebornAbilityHandler::getFragmentumTokenColor)
                        .sorted()
                        .reduce((left, right) -> left + " + " + right)
                        .orElse("")
                + " Fragmentum Token" + (cost.size() == 1 ? "" : "s");
    }

    private static void unleash(Game game, Player player, AdaptiveComponent component) {
        switch (component) {
            case AGENT, COMMANDER, HERO -> replaceLeader(player, component.dormant, component.unleashed);
            case XENOLITH, STALACTIUM -> replaceTech(player, component.dormant, component.unleashed);
            case GOLEM -> replaceUnit(player, component.dormant, component.unleashed);
            case REFRAGMENT -> player.changeBreakthrough(component.dormant, component.unleashed);
            case PETRA -> unleashPetra(game, player);
        }
    }

    private static MessageEmbed getUnleashedComponentEmbed(AdaptiveComponent component) {
        return switch (component) {
            case AGENT, COMMANDER, HERO -> Mapper.getLeader(component.unleashed).getRepresentationEmbed();
            case XENOLITH, STALACTIUM -> Mapper.getTech(component.unleashed).getRepresentationEmbed();
            case GOLEM -> Mapper.getUnit(component.unleashed).getRepresentationEmbed();
            case REFRAGMENT -> Mapper.getBreakthrough(component.unleashed).getRepresentationEmbed();
            case PETRA -> Mapper.getPlanet(component.unleashed).getRepresentationEmbed();
        };
    }

    private static void replaceLeader(Player player, String dormant, String unleashed) {
        Leader leader = player.getLeader(dormant).orElse(null);
        if (leader == null) {
            return;
        }
        player.removeLeader(leader);
        player.addLeader(new Leader(
                unleashed,
                leader.getType(),
                leader.getTgCount(),
                leader.isExhausted(),
                "stoneborncommanderunleashed".equals(unleashed) ? false : leader.isLocked(),
                leader.isActive()));
    }

    private static void replaceTech(Player player, String dormant, String unleashed) {
        player.purgeTech(dormant);
        player.addTech(unleashed);
        if ("thstoneborndn".equals(dormant)) {
            replaceUnit(player, "stoneborn_dreadnought2dormant", "stoneborn_dreadnought2unleashed");
        }
    }

    private static void replaceUnit(Player player, String dormant, String unleashed) {
        player.removeOwnedUnitByID(dormant);
        player.addOwnedUnitByID(unleashed);
    }

    private static void unleashPetra(Game game, Player player) {
        Tile oldTile = game.getTileFromPlanet("petra");
        if (oldTile == null) {
            return;
        }
        Tile newTile = new Tile("th16b", oldTile.getPosition(), oldTile.getSpaceUnitHolder());
        UnitHolder oldPlanet = oldTile.getUnitHolders().get("petra");
        UnitHolder newPlanet = newTile.getUnitHolders().get("petraunleashed");
        if (oldPlanet != null && newPlanet != null) {
            newPlanet.inheritEverythingFrom(oldPlanet);
        }
        boolean exhausted = player.getExhaustedPlanets().contains("petra");
        boolean abilityExhausted = player.getExhaustedPlanetsAbilities().contains("petra");
        player.removePlanet("petra");
        player.addPlanet("petraunleashed");
        if (exhausted) {
            player.exhaustPlanet("petraunleashed");
        }
        if (abilityExhausted) {
            player.exhaustPlanetAbility("petraunleashed");
        }
        game.setTile(newTile);
    }

    private enum AdaptiveComponent {
        AGENT("agent", "Hazorus", "stonebornagentdormant", "stonebornagentunleashed", AdaptiveCost.ONE),
        COMMANDER(
                "commander",
                "Tempus",
                "stoneborncommanderdormant",
                "stoneborncommanderunleashed",
                AdaptiveCost.TWO_DIFFERENT),
        HERO("hero", "Meteorime", "stonebornherodormant", "stonebornherounleashed", AdaptiveCost.THREE_DIFFERENT),
        XENOLITH("xenolith", "Xenolith", "thstoneborng", "thstoneborngu", AdaptiveCost.TWO_GREEN),
        STALACTIUM("stalactium", "Stalactium II", "thstoneborndn", "thstoneborndnu", AdaptiveCost.TWO_BLUE),
        GOLEM("golem", "Golem", "stoneborn_mechdormant", "stoneborn_mechunleashed", AdaptiveCost.TWO_RED),
        REFRAGMENT("refragment", "Refragment", "stonebornbt", "stonebornbtunleashed", AdaptiveCost.TWO_SAME),
        PETRA("petra", "Petra", "petra", "petraunleashed", AdaptiveCost.TWO_DIFFERENT);

        private final String id;
        private final String name;
        private final String dormant;
        private final String unleashed;
        private final AdaptiveCost cost;

        AdaptiveComponent(String id, String name, String dormant, String unleashed, AdaptiveCost cost) {
            this.id = id;
            this.name = name;
            this.dormant = dormant;
            this.unleashed = unleashed;
            this.cost = cost;
        }

        private static AdaptiveComponent fromId(String id) {
            for (AdaptiveComponent component : values()) {
                if (component.id.equals(id)) {
                    return component;
                }
            }
            return null;
        }
    }

    private enum AdaptiveCost {
        ONE(1, false, false, null),
        TWO_GREEN(2, false, false, "industrial"),
        TWO_BLUE(2, false, false, "cultural"),
        TWO_RED(2, false, false, "hazardous"),
        TWO_SAME(2, false, true, null),
        TWO_DIFFERENT(2, true, false, null),
        THREE(3, false, false, null),
        THREE_DIFFERENT(3, true, false, null);

        private final int count;
        private final boolean different;
        private final boolean same;
        private final String requiredTrait;

        AdaptiveCost(int count, boolean different, boolean same, String requiredTrait) {
            this.count = count;
            this.different = different;
            this.same = same;
            this.requiredTrait = requiredTrait;
        }
    }

    private static List<String> getFragmentumTokens(Game game, Player player) {
        List<String> tokens = new ArrayList<>(
                List.of(game.getStoredValue(getFragmentumTokensKey(player)).split(",")));
        tokens.removeIf(String::isEmpty);
        return tokens;
    }

    private static List<String> getFragmentumTraits(ExploreModel fragment) {
        if (fragment.getAlias().contains("unknown")
                || fragment.getName().toLowerCase().contains("unknown")) {
            return FRAGMENTUM_TRAITS;
        }
        return List.of(fragment.getType().toLowerCase());
    }

    private static String getFragmentumTokensKey(Player player) {
        return FRAGMENTUM_TOKENS + player.getFaction();
    }

    private static MessageChannel getOutputChannel(Game game, Player player, GenericInteractionCreateEvent event) {
        return game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel();
    }
}
