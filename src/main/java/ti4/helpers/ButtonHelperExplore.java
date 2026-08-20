package ti4.helpers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion.OblivionUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.DeckModel;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.leader.CommanderUnlockCheckService;

@UtilityClass
public class ButtonHelperExplore {

    private static final String GAIN_SUPERMASSIVE_FRAGMENT = "gainSupermassiveFragment_";

    @ButtonHandler("exploreFront_")
    public static void exploreFront(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String pos = buttonID.replace("exploreFront_", "");
        ButtonHelper.resolveFullFrontierExplore(game, player, game.getTileByPosition(pos), event);
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }

    @ButtonHandler("freelancersBuild_")
    public static void freelancersBuild(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String planet = buttonID.replace("freelancersBuild_", "");
        List<Button> buttons;
        Tile tile = game.getTile(AliasHandler.resolveTile(planet));
        if (tile == null) {
            tile = game.getTileByPosition(planet);
        }
        buttons = Helper.getPlaceUnitButtons(
                event, player, game, tile, "freelancers", "placeOneNDone_dontskipfreelancers");
        String message = player.getRepresentation() + " Use the buttons to produce 1 unit. "
                + ButtonHelper.getListOfStuffAvailableToSpend(player, game);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), message, buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("purge_Frags_")
    public static void purgeFrags(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        purgeNormalFrags(game, player, event, buttonID.replace("purge_Frags_", ""));
    }

    @ButtonHandler("purgeSupermassiveFrag_")
    public static void purgeSupermassiveFrag(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String fragmentId = buttonID.replace("purgeSupermassiveFrag_", "");
        ExploreModel fragment = Mapper.getExplore(fragmentId);
        if (game == null
                || player == null
                || !player.getFragments().contains(fragmentId)
                || fragment == null
                || !fragmentId.startsWith("supermassive")) {
            return;
        }
        resolvePurgedFragments(game, player, event, List.of(fragmentId));
        offerSupermassiveFragmentGain(game, player, event, fragmentId);
    }

    @ButtonHandler(GAIN_SUPERMASSIVE_FRAGMENT)
    public static void gainSupermassiveFragment(
            Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] payload =
                buttonID.substring(GAIN_SUPERMASSIVE_FRAGMENT.length()).split("\\|", 2);
        if (game == null || player == null || payload.length != 2) {
            return;
        }

        String supermassiveFragment = payload[0];
        String fragmentToGain = payload[1];
        ExploreModel supermassiveModel = Mapper.getExplore(supermassiveFragment);
        if (supermassiveModel == null || !supermassiveFragment.startsWith("supermassive")) {
            return;
        }
        String trait = "supermassiveunknown".equals(supermassiveFragment) ? null : supermassiveModel.getType();
        List<Button> gainButtons = getSupermassiveFragmentGainButtons(game, player, supermassiveFragment);
        String message = player.getRepresentation() + ", choose a purged relic fragment to gain for _"
                + supermassiveModel.getName() + "_.";
        String buttonPrefix = player.factionButtonChecker() + GAIN_SUPERMASSIVE_FRAGMENT + supermassiveFragment + "|";
        if (player.getFragments().contains(supermassiveFragment)
                || NewStuffHelper.checkAndHandlePaginationChange(
                        event, event.getMessageChannel(), gainButtons, message, buttonPrefix, buttonID)
                || !getPurgedFragments(game, trait, supermassiveFragment).contains(fragmentToGain)) {
            return;
        }

        player.addFragment(fragmentToGain);
        game.setNumberOfPurgedFragments(Math.max(0, game.getNumberOfPurgedFragments() - 1));
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " gained _"
                        + Mapper.getExplore(fragmentToGain).getName() + "_ from the purged fragments.");
    }

    public static List<Button> getSupermassiveFragmentPurgeButtons(Player player, String factionChecker) {
        return player.getFragments().stream()
                .filter(fragmentId -> fragmentId.startsWith("supermassive"))
                .map(Mapper::getExplore)
                .filter(fragment -> fragment != null)
                .map(fragment -> switch (fragment.getType().toLowerCase()) {
                    case "cultural" ->
                        Buttons.blue(
                                factionChecker + "purgeSupermassiveFrag_" + fragment.getAlias(),
                                "Purge " + fragment.getName());
                    case "industrial" ->
                        Buttons.green(
                                factionChecker + "purgeSupermassiveFrag_" + fragment.getAlias(),
                                "Purge " + fragment.getName());
                    case "hazardous" ->
                        Buttons.red(
                                factionChecker + "purgeSupermassiveFrag_" + fragment.getAlias(),
                                "Purge " + fragment.getName());
                    default ->
                        Buttons.gray(
                                factionChecker + "purgeSupermassiveFrag_" + fragment.getAlias(),
                                "Purge " + fragment.getName());
                })
                .toList();
    }

    public static int getNormalFragmentCount(Player player, String trait) {
        return switch (trait.toLowerCase()) {
            case "cultural" -> player.getCrf() - (player.getFragments().contains("supermassivecultural") ? 1 : 0);
            case "industrial" -> player.getIrf() - (player.getFragments().contains("supermassiveindustrial") ? 1 : 0);
            case "hazardous" -> player.getHrf() - (player.getFragments().contains("supermassivehazardous") ? 1 : 0);
            case "frontier" -> player.getUrf() - (player.getFragments().contains("supermassiveunknown") ? 1 : 0);
            default -> 0;
        };
    }

    public static int getSupermassiveFragmentCount(Player player, String trait) {
        return (int) player.getFragments().stream()
                .filter(fragmentId -> fragmentId.startsWith("supermassive"))
                .map(Mapper::getExplore)
                .filter(fragment -> fragment != null && trait.equalsIgnoreCase(fragment.getType()))
                .count();
    }

    private static void offerSupermassiveFragmentGain(
            Game game, Player player, ButtonInteractionEvent event, String supermassiveFragment) {
        ExploreModel supermassiveModel = Mapper.getExplore(supermassiveFragment);
        if (supermassiveModel == null) {
            return;
        }

        List<Button> buttons = getSupermassiveFragmentGainButtons(game, player, supermassiveFragment);
        if (buttons.isEmpty()) {
            return;
        }

        String message = player.getRepresentation() + ", choose a purged relic fragment to gain for _"
                + supermassiveModel.getName() + "_.";
        String buttonPrefix = player.factionButtonChecker() + GAIN_SUPERMASSIVE_FRAGMENT + supermassiveFragment + "|";
        List<Button> displayedButtons = buttons.size() <= 25
                ? buttons
                : NewStuffHelper.buttonPagination(buttons, null, buttonPrefix, 24, 0, true);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, displayedButtons);
    }

    private static List<Button> getSupermassiveFragmentGainButtons(
            Game game, Player player, String supermassiveFragment) {
        ExploreModel supermassiveModel = Mapper.getExplore(supermassiveFragment);
        if (supermassiveModel == null) {
            return List.of();
        }

        String trait = "supermassiveunknown".equals(supermassiveFragment) ? null : supermassiveModel.getType();
        return getPurgedFragments(game, trait, supermassiveFragment).stream()
                .map(Mapper::getExplore)
                .filter(fragment -> fragment != null)
                .map(fragment -> switch (fragment.getType().toLowerCase()) {
                    case "cultural" ->
                        Buttons.blue(
                                player.factionButtonChecker() + GAIN_SUPERMASSIVE_FRAGMENT + supermassiveFragment + "|"
                                        + fragment.getAlias(),
                                "Gain " + fragment.getName());
                    case "industrial" ->
                        Buttons.green(
                                player.factionButtonChecker() + GAIN_SUPERMASSIVE_FRAGMENT + supermassiveFragment + "|"
                                        + fragment.getAlias(),
                                "Gain " + fragment.getName());
                    case "hazardous" ->
                        Buttons.red(
                                player.factionButtonChecker() + GAIN_SUPERMASSIVE_FRAGMENT + supermassiveFragment + "|"
                                        + fragment.getAlias(),
                                "Gain " + fragment.getName());
                    default ->
                        Buttons.gray(
                                player.factionButtonChecker() + GAIN_SUPERMASSIVE_FRAGMENT + supermassiveFragment + "|"
                                        + fragment.getAlias(),
                                "Gain " + fragment.getName());
                })
                .toList();
    }

    private static List<String> getPurgedFragments(Game game, String trait, String excludedFragment) {
        DeckModel explorationDeck = Mapper.getDeck(game.getExplorationDeckID());
        if (explorationDeck == null) {
            return List.of();
        }

        Set<String> unavailableFragments = new HashSet<>();
        for (String exploreType :
                List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL, Constants.FRONTIER)) {
            unavailableFragments.addAll(game.getExploreDeck(exploreType));
            unavailableFragments.addAll(game.getExploreDiscard(exploreType));
        }
        for (Player gamePlayer : game.getPlayers().values()) {
            unavailableFragments.addAll(gamePlayer.getFragments());
        }

        return explorationDeck.getNewDeck().stream()
                .distinct()
                .filter(fragmentId ->
                        !fragmentId.equals(excludedFragment) && !unavailableFragments.contains(fragmentId))
                .filter(fragmentId -> {
                    ExploreModel fragment = Mapper.getExplore(fragmentId);
                    return fragment != null
                            && Constants.FRAGMENT.equalsIgnoreCase(fragment.getResolution())
                            && (trait == null || trait.equalsIgnoreCase(fragment.getType()));
                })
                .toList();
    }

    private static void purgeNormalFrags(Game game, Player player, ButtonInteractionEvent event, String typeNAmount) {
        String type = typeNAmount.split("_")[0];
        int count = Integer.parseInt(typeNAmount.split("_")[1]);
        String trait = getFragmentTrait(type);
        List<String> fragmentsToPurge = new ArrayList<>();
        for (String fragId : player.getFragments()) {
            ExploreModel fragment = Mapper.getExplore(fragId);
            if (fragment != null && trait.equalsIgnoreCase(fragment.getType())) {
                if (fragId.startsWith("supermassive")) {
                    continue;
                }
                fragmentsToPurge.add(fragId);
            }
        }
        if (fragmentsToPurge.size() == count) {
            ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
        }
        while (fragmentsToPurge.size() > count) {
            fragmentsToPurge.removeLast();
        }

        resolvePurgedFragments(game, player, event, fragmentsToPurge);
    }

    private static void resolvePurgedFragments(
            Game game, Player player, ButtonInteractionEvent event, List<String> fragmentsToPurge) {
        StringBuilder message = new StringBuilder(player.getRepresentation() + " purged ");
        if (fragmentsToPurge.size() == 1) {
            String fragId = fragmentsToPurge.getFirst();
            player.removeFragment(fragId);
            game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
            switch (fragId) {
                case "crf1", "crf2", "crf3", "crf4", "crf5", "crf6", "crf7", "crf8", "crf9", "supermassivecultural" ->
                    message.append(" a " + (fragId.contains("supermassive") ? "supermassive " : "")
                            + ExploreEmojis.CFrag + "cultural");
                case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7", "supermassivehazardous" ->
                    message.append(" a " + (fragId.contains("supermassive") ? "supermassive " : "")
                            + ExploreEmojis.HFrag + "hazardous");
                case "irf1", "irf2", "irf3", "irf4", "irf5", "supermassiveindustrial" ->
                    message.append(" an " + (fragId.contains("supermassive") ? "supermassive " : "")
                            + ExploreEmojis.IFrag + "industrial");
                case "urf1", "urf2", "urf3", "supermassiveunknown" ->
                    message.append(" an " + (fragId.contains("supermassive") ? "supermassive " : "")
                            + ExploreEmojis.UFrag + "unknown");
                default -> message.append(' ').append(fragId);
            }
            message.append(" relic fragment.");
        } else {
            for (String fragId : fragmentsToPurge) {
                player.removeFragment(fragId);
                game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + 1);
                switch (fragId) {
                    case "crf1",
                            "crf2",
                            "crf3",
                            "crf4",
                            "crf5",
                            "crf6",
                            "crf7",
                            "crf8",
                            "crf9",
                            "supermassivecultural" -> message.append(ExploreEmojis.CFrag);
                    case "hrf1", "hrf2", "hrf3", "hrf4", "hrf5", "hrf6", "hrf7", "supermassivehazardous" ->
                        message.append(ExploreEmojis.HFrag);
                    case "irf1", "irf2", "irf3", "irf4", "irf5", "supermassiveindustrial" ->
                        message.append(ExploreEmojis.IFrag);
                    case "urf1", "urf2", "urf3", "supermassiveunknown" -> message.append(ExploreEmojis.UFrag);
                    default -> message.append(' ').append(fragId);
                }
            }
            message.append(" relic fragments.");
        }
        CommanderUnlockCheckService.checkAllPlayersInGame(game, "lanefir");
        OblivionUnitHandler.doOblivionMechCheck(game, player);
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
        if (!game.isFowMode() && event.getMessageChannel() instanceof ThreadChannel) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message.toString());
        }

        if (player.hasTech("dslaner") && !game.isTwilightsFallMode()) {
            player.setAtsCount(player.getAtsCount() + 1);
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), player.getRepresentation() + " put 1 commodity on _ATS Armaments_.");
        }
    }

    private static String getFragmentTrait(String type) {
        return switch (type.toLowerCase()) {
            case "crf" -> "cultural";
            case "hrf" -> "hazardous";
            case "irf" -> "industrial";
            case "urf" -> "frontier";
            default -> "";
        };
    }
}
