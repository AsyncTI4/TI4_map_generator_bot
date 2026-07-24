package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kairn;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion.OblivionUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.DeckModel;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.explore.ExploreService;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class KairnBreakthroughHandler {
    private static final String PURGE_FRAGMENT = "kairnBtPurgeFragment_";
    private static final String EXPLORE_PLANET = "kairnBtExplorePlanet_";
    private static final String GAIN_FRAGMENT = "kairnBtGainFragment_";

    public static boolean canUse(Game game, Player player) {
        return player.getFragments().stream().anyMatch(fragmentId -> {
            ExploreModel fragment = Mapper.getExplore(fragmentId);
            return fragment != null && isRelicFragment(fragment) && hasMatchingPlanet(game, player, fragment.getType());
        });
    }

    public static void postInitialButtons(Game game, Player player) {
        List<Button> buttons = new ArrayList<>();
        for (String fragmentId : player.getFragments()) {
            ExploreModel fragment = Mapper.getExplore(fragmentId);
            if (fragment == null
                    || !isRelicFragment(fragment)
                    || !hasMatchingPlanet(game, player, fragment.getType())) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + PURGE_FRAGMENT + fragmentId,
                    "Purge " + fragmentLabel(fragmentId),
                    fragmentEmoji(fragmentId)));
        }
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " has no relic fragment with a matching controlled planet for _Relic Trading Hub_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + ", choose 1 relic fragment to purge for _Relic Trading Hub_.",
                buttons);
    }

    @ButtonHandler(PURGE_FRAGMENT)
    public static void purgeFragment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String fragmentId = buttonID.substring(PURGE_FRAGMENT.length());
        ExploreModel fragment = Mapper.getExplore(fragmentId);
        if (fragment == null
                || !isRelicFragment(fragment)
                || !player.getFragments().contains(fragmentId)
                || !hasMatchingPlanet(game, player, fragment.getType())) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Could not resolve _Relic Trading Hub_ because that fragment is unavailable.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String trait = fragment.getType().toLowerCase();
        player.removeFragment(fragmentId);
        game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "lanefir");
        DSHelperBreakthroughs.doLanefirBtCheck(game, player);
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " purged a " + fragmentEmoji(fragmentId)
                        + fragmentLabel(fragmentId) + ". Choose 1 " + trait
                        + " planet to explore for _Relic Trading Hub_.",
                getMatchingPlanetButtons(game, player, trait));
    }

    @ButtonHandler(EXPLORE_PLANET)
    public static void explorePlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(EXPLORE_PLANET.length()).split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String trait = payload[0];
        String planetName = payload[1];
        Planet planet = game.getUnitHolderFromPlanet(planetName);
        Tile tile = game.getTileFromPlanet(planetName);
        if (planet == null
                || tile == null
                || !player.getPlanetsAllianceMode().contains(planetName)
                || !planet.getPlanetTypes().contains(trait)) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    "Could not resolve _Relic Trading Hub_ because that planet is unavailable.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        ButtonHelper.deleteMessage(event);
        ExploreService.explorePlanet(event, tile, planetName, trait.toUpperCase(), player, false, game, 1, false);
        sendFragmentGainButtons(game, player, trait);
    }

    @ButtonHandler(GAIN_FRAGMENT)
    public static void gainFragment(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(GAIN_FRAGMENT.length()).split("\\|", 2);
        if (payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        String purgedTrait = payload[0];
        String gainedTrait = payload[1];
        List<String> availableFragments = getPurgedFragments(game, gainedTrait);
        if (purgedTrait.equalsIgnoreCase(gainedTrait) || availableFragments.size() < 2) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " cannot gain 2 purged "
                            + gainedTrait
                            + " relic fragments for _Relic Trading Hub_.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        List<String> fragmentsToGain = availableFragments.subList(0, 2);
        for (String fragmentId : fragmentsToGain) {
            player.addFragment(fragmentId);
        }
        game.setNumberOfPurgedFragments(Math.max(0, game.getNumberOfPurgedFragments() - fragmentsToGain.size()));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged() + " gained 2 " + fragmentTraitEmoji(gainedTrait) + " "
                        + fragmentTraitLabel(gainedTrait) + "s from the purged fragments for _Relic Trading Hub_.");
    }

    private static List<Button> getMatchingPlanetButtons(Game game, Player player, String trait) {
        List<Button> buttons = new ArrayList<>();
        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet != null && planet.getPlanetTypes().contains(trait)) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + EXPLORE_PLANET + trait + "|" + planetName,
                        "Explore " + Helper.getPlanetRepresentation(planetName, game)));
            }
        }
        return buttons;
    }

    private static void sendFragmentGainButtons(Game game, Player player, String purgedTrait) {
        List<Button> buttons = getFragmentGainButtons(game, player, purgedTrait);

        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + " cannot gain 2 purged relic fragments of a different color for _Relic Trading Hub_.");
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentationUnfogged()
                        + ", choose a differently-colored purged fragment color to gain 2 of for _Relic Trading Hub_:",
                buttons);
    }

    private static List<Button> getFragmentGainButtons(Game game, Player player, String purgedTrait) {
        List<Button> buttons = new ArrayList<>();
        for (String trait : List.of(Constants.CULTURAL, Constants.INDUSTRIAL, Constants.HAZARDOUS)) {
            if (!trait.equals(purgedTrait) && getPurgedFragments(game, trait).size() >= 2) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + GAIN_FRAGMENT + purgedTrait + "|" + trait,
                        "Gain 2 " + trait + " fragments",
                        fragmentTraitEmoji(trait)));
            }
        }
        return buttons;
    }

    private static List<String> getPurgedFragments(Game game, String trait) {
        DeckModel explorationDeck = Mapper.getDeck(game.getExplorationDeckID());
        if (explorationDeck == null) {
            return List.of();
        }

        List<String> fragmentsInPlay = game.getPlayers().values().stream()
                .flatMap(player -> player.getFragments().stream())
                .toList();
        return explorationDeck.getNewDeck().stream()
                .filter(fragmentId -> {
                    ExploreModel fragment = Mapper.getExplore(fragmentId);
                    return fragment != null
                            && isRelicFragment(fragment)
                            && trait.equalsIgnoreCase(fragment.getType())
                            && !game.getExploreDeck(trait).contains(fragmentId)
                            && !game.getExploreDiscard(trait).contains(fragmentId)
                            && !fragmentsInPlay.contains(fragmentId);
                })
                .toList();
    }

    private static boolean isRelicFragment(ExploreModel explore) {
        return Constants.FRAGMENT.equalsIgnoreCase(explore.getResolution());
    }

    private static boolean hasMatchingPlanet(Game game, Player player, String trait) {
        for (String planetName : player.getPlanetsAllianceMode()) {
            Planet planet = game.getUnitHolderFromPlanet(planetName);
            if (planet != null && planet.getPlanetTypes().contains(trait.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private static String fragmentLabel(String fragmentId) {
        ExploreModel fragment = Mapper.getExplore(fragmentId);
        return fragment == null ? "unknown relic fragment" : fragmentTraitLabel(fragment.getType());
    }

    private static String fragmentTraitLabel(String trait) {
        return trait.toLowerCase() + " relic fragment";
    }

    private static String fragmentEmoji(String fragmentId) {
        ExploreModel fragment = Mapper.getExplore(fragmentId);
        return fragment == null ? ExploreEmojis.UFrag.toString() : fragmentTraitEmoji(fragment.getType());
    }

    private static String fragmentTraitEmoji(String trait) {
        return switch (trait.toLowerCase()) {
            case Constants.CULTURAL -> ExploreEmojis.CFrag.toString();
            case Constants.INDUSTRIAL -> ExploreEmojis.IFrag.toString();
            case Constants.HAZARDOUS -> ExploreEmojis.HFrag.toString();
            default -> ExploreEmojis.UFrag.toString();
        };
    }
}
