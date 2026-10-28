package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperExplore;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;

@UtilityClass
public class StonebornBreakthroughHandler {
    private static final String DORMANT = "stonebornbt";
    private static final String UNLEASHED = "stonebornbtunleashed";
    private static final String SELECT_FRAGMENT = "refragmentFragment_";
    private static final String SELECT_TRAIT = "refragmentTrait_";
    private static final String EXPLORE_PLANET = "refragmentExplore_";
    private static final String SKIP_EXPLORE = "refragmentSkipExplore";
    private static final String GAIN_FRAGMENT = "refragmentGain_";
    private static final String DECLINE_GAIN = "refragmentDeclineGain";
    private static final String STATE = "refragmentState_";

    public static void startRefragment(
            GenericInteractionCreateEvent event, Game game, Player player, String breakthrough) {
        if (!isRefragment(breakthrough)
                || !player.hasUnlockedBreakthrough(breakthrough)
                || player.getFragments().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    getOutputChannel(game, player, event),
                    player.getRepresentation() + " has no relic fragment to purge for _Refragment_.");
            return;
        }
        game.setStoredValue(stateKey(player), breakthrough + "||0");
        sendFragmentPrompt(event, game, player);
    }

    @ButtonHandler(SELECT_FRAGMENT)
    public static void selectFragment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        RefragmentState state = getState(game, player);
        if (state == null || !player.hasUnlockedBreakthrough(state.breakthrough())) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = fragmentButtons(player);
        String message = fragmentPromptMessage(player);
        String prefix = player.factionButtonChecker() + SELECT_FRAGMENT;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                getOutputChannel(game, player, event),
                buttons,
                List.of(Buttons.DECLINE_DELETE_BUTTONS),
                message,
                prefix,
                buttonID)) {
            return;
        }

        String fragment = buttonID.substring(SELECT_FRAGMENT.length());
        ExploreModel model = Mapper.getExplore(fragment);
        if (!player.getFragments().contains(fragment)
                || model == null
                || !Constants.FRAGMENT.equalsIgnoreCase(model.getResolution())) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelperExplore.resolvePurgedFragments(game, player, event, List.of(fragment));
        if (isUnknownFragment(model)) {
            game.setStoredValue(stateKey(player), state.breakthrough() + "||" + gainCount(state.breakthrough()));
            offerTraitSelection(event, game, player);
            ButtonHelper.deleteMessage(event);
            return;
        }
        String trait = model.getType().toLowerCase();
        game.setStoredValue(
                stateKey(player), state.breakthrough() + "|" + trait + "|" + gainCount(state.breakthrough()));
        offerExploration(event, game, player, trait);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_TRAIT)
    public static void selectTrait(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        RefragmentState state = getState(game, player);
        String trait = buttonID.substring(SELECT_TRAIT.length());
        if (state == null
                || !state.trait().isEmpty()
                || !List.of("cultural", "hazardous", "industrial").contains(trait)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(stateKey(player), state.breakthrough() + "|" + trait + "|" + state.remainingGains());
        offerExploration(event, game, player, trait);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(EXPLORE_PLANET)
    public static void explorePlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        RefragmentState state = getState(game, player);
        if (state == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = explorationButtons(game, player, state.trait());
        String message = explorationPromptMessage(player, state.trait());
        String prefix = player.factionButtonChecker() + EXPLORE_PLANET;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                getOutputChannel(game, player, event),
                buttons,
                List.of(Buttons.red(player.factionButtonChecker() + SKIP_EXPLORE, "Skip Exploration")),
                message,
                prefix,
                buttonID)) {
            return;
        }
        String planetName = buttonID.substring(EXPLORE_PLANET.length());
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null
                || tile == null
                || !player.getPlanets().contains(planetName)
                || !hasPlanetTrait(planet, state.trait())) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        ExploreService.explorePlanet(event, tile, planetName, state.trait(), player, false, game, 1, false);
        offerPurgedFragmentGain(event, game, player, state);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SKIP_EXPLORE)
    public static void skipExplore(ButtonInteractionEvent event, Game game, Player player) {
        RefragmentState state = getState(game, player);
        if (state != null) {
            offerPurgedFragmentGain(event, game, player, state);
        }
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(GAIN_FRAGMENT)
    public static void gainFragment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        RefragmentState state = getState(game, player);
        if (state == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = purgedFragmentButtons(game, player, state.trait());
        String message = gainPromptMessage(player, state);
        String prefix = player.factionButtonChecker() + GAIN_FRAGMENT;
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                getOutputChannel(game, player, event),
                buttons,
                List.of(Buttons.red(player.factionButtonChecker() + DECLINE_GAIN, "Done Gaining Fragments")),
                message,
                prefix,
                buttonID)) {
            return;
        }

        String fragment = buttonID.substring(GAIN_FRAGMENT.length());
        if (!ButtonHelperExplore.getPurgedFragments(game).contains(fragment)
                || !isOtherTrait(fragment, state.trait())) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.addFragment(fragment);
        game.setNumberOfPurgedFragments(Math.max(0, game.getNumberOfPurgedFragments() - 1));
        int remaining = state.remainingGains() - 1;
        if (remaining <= 0) {
            clearState(game, player);
            ButtonHelper.deleteMessage(event);
        } else {
            game.setStoredValue(stateKey(player), state.breakthrough() + "|" + state.trait() + "|" + remaining);
            offerPurgedFragmentGain(event, game, player, getState(game, player));
            ButtonHelper.deleteMessage(event);
        }
        MessageHelper.sendMessageToChannel(
                getOutputChannel(game, player, event),
                player.getRepresentation() + " gained _"
                        + Mapper.getExplore(fragment).getName() + "_ with _Refragment_.");
    }

    @ButtonHandler(DECLINE_GAIN)
    public static void declineGain(ButtonInteractionEvent event, Game game, Player player) {
        clearState(game, player);
        ButtonHelper.deleteMessage(event);
    }

    private static void offerExploration(GenericInteractionCreateEvent event, Game game, Player player, String trait) {
        List<Button> buttons = explorationButtons(game, player, trait);
        List<Button> pinned = List.of(Buttons.red(player.factionButtonChecker() + SKIP_EXPLORE, "Skip Exploration"));
        MessageHelper.sendMessageToChannelWithButtons(
                getOutputChannel(game, player, event),
                explorationPromptMessage(player, trait),
                paginate(buttons, pinned, player.factionButtonChecker() + EXPLORE_PLANET));
    }

    private static void offerTraitSelection(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = List.of("cultural", "hazardous", "industrial").stream()
                .map(trait -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_TRAIT + trait,
                        "Choose " + trait.substring(0, 1).toUpperCase() + trait.substring(1) + " Planet",
                        ExploreEmojis.getTraitEmoji(trait)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                getOutputChannel(game, player, event),
                player.getRepresentation() + ", choose a planet trait to explore for _Refragment_.",
                buttons);
    }

    private static List<Button> explorationButtons(Game game, Player player, String trait) {
        return game.getTileMap().values().stream()
                .flatMap(tile -> tile.getPlanetUnitHolders().stream())
                .filter(planet -> player.getPlanets().contains(planet.getName()))
                .filter(planet -> hasPlanetTrait(planet, trait))
                .map(planet -> Buttons.gray(
                        player.factionButtonChecker() + EXPLORE_PLANET + planet.getName(),
                        "Explore " + planet.getRepresentation(game),
                        ExploreEmojis.getTraitEmoji(trait)))
                .toList();
    }

    private static boolean hasPlanetTrait(Planet planet, String trait) {
        return planet.getPlanetTypes().stream().anyMatch(planetTrait -> planetTrait.equalsIgnoreCase(trait));
    }

    private static String explorationPromptMessage(Player player, String trait) {
        return player.getRepresentation() + ", choose up to 1 " + trait + " planet to explore for _Refragment_.";
    }

    private static void offerPurgedFragmentGain(
            GenericInteractionCreateEvent event, Game game, Player player, RefragmentState state) {
        List<Button> buttons = purgedFragmentButtons(game, player, state.trait());
        if (buttons.isEmpty()) {
            clearState(game, player);
            MessageHelper.sendMessageToChannel(
                    getOutputChannel(game, player, event),
                    player.getRepresentation() + " has no eligible purged relic fragments to gain.");
            return;
        }
        List<Button> pinned =
                List.of(Buttons.red(player.factionButtonChecker() + DECLINE_GAIN, "Done Gaining Fragments"));
        String prefix = player.factionButtonChecker() + GAIN_FRAGMENT;
        MessageHelper.sendMessageToChannelWithButtons(
                getOutputChannel(game, player, event),
                gainPromptMessage(player, state),
                paginate(buttons, pinned, prefix));
    }

    private static void sendFragmentPrompt(GenericInteractionCreateEvent event, Game game, Player player) {
        List<Button> buttons = fragmentButtons(player);
        String message = fragmentPromptMessage(player);
        String prefix = player.factionButtonChecker() + SELECT_FRAGMENT;
        MessageHelper.sendMessageToChannelWithButtons(
                getOutputChannel(game, player, event),
                message,
                paginate(buttons, List.of(Buttons.DECLINE_DELETE_BUTTONS), prefix));
    }

    private static List<Button> fragmentButtons(Player player) {
        return player.getFragments().stream()
                .map(Mapper::getExplore)
                .filter(Objects::nonNull)
                .filter(model -> Constants.FRAGMENT.equalsIgnoreCase(model.getResolution()))
                .map(model -> Buttons.gray(
                        player.factionButtonChecker() + SELECT_FRAGMENT + model.getAlias(),
                        "Purge " + model.getName(),
                        ExploreEmojis.getFragEmoji(model.getType())))
                .toList();
    }

    private static List<Button> purgedFragmentButtons(Game game, Player player, String excludedTrait) {
        return ButtonHelperExplore.getPurgedFragments(game).stream()
                .filter(fragment -> isOtherTrait(fragment, excludedTrait))
                .map(Mapper::getExplore)
                .filter(Objects::nonNull)
                .map(model -> Buttons.gray(
                        player.factionButtonChecker() + GAIN_FRAGMENT + model.getAlias(),
                        "Gain " + model.getName(),
                        ExploreEmojis.getFragEmoji(model.getType())))
                .toList();
    }

    private static boolean isOtherTrait(String fragment, String excludedTrait) {
        ExploreModel model = Mapper.getExplore(fragment);
        return model != null && !model.getType().equalsIgnoreCase(excludedTrait);
    }

    private static boolean isUnknownFragment(ExploreModel fragment) {
        return fragment.getAlias().contains("unknown")
                || fragment.getName().toLowerCase().contains("unknown");
    }

    private static String fragmentPromptMessage(Player player) {
        return player.getRepresentation() + ", choose a relic fragment to purge for _Refragment_.";
    }

    private static String gainPromptMessage(Player player, RefragmentState state) {
        return player.getRepresentation() + ", you may gain " + state.remainingGains()
                + " purged relic fragment" + (state.remainingGains() == 1 ? "" : "s")
                + " of a different color with _Refragment_.";
    }

    private static List<Button> paginate(List<Button> buttons, List<Button> pinned, String prefix) {
        if (buttons.size() + pinned.size() <= 25) {
            List<Button> result = new ArrayList<>(buttons);
            result.addAll(pinned);
            return result;
        }
        return NewStuffHelper.buttonPagination(buttons, pinned, prefix, 25, 0, false);
    }

    private static boolean isRefragment(String breakthrough) {
        return DORMANT.equals(breakthrough) || UNLEASHED.equals(breakthrough);
    }

    private static int gainCount(String breakthrough) {
        return UNLEASHED.equals(breakthrough) ? 2 : 1;
    }

    private static RefragmentState getState(Game game, Player player) {
        String[] parts = game.getStoredValue(stateKey(player)).split("\\|", 3);
        if (parts.length != 3 || !isRefragment(parts[0])) {
            return null;
        }
        try {
            return new RefragmentState(parts[0], parts[1], Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void clearState(Game game, Player player) {
        game.removeStoredValue(stateKey(player));
    }

    private static String stateKey(Player player) {
        return STATE + player.getFaction();
    }

    private static MessageChannel getOutputChannel(Game game, Player player, GenericInteractionCreateEvent event) {
        return game.isFowMode() ? player.getCorrectChannel() : event.getMessageChannel();
    }

    private record RefragmentState(String breakthrough, String trait, int remainingGains) {}
}
