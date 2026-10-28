package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.arcanum;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn.StonebornAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.stoneborn.StonebornLeadersHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperExplore;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.Constants;
import ti4.helpers.NewStuffHelper;
import ti4.helpers.RelicHelper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class ArcanumLeadersHandler {
    private static final String PURGE_COMMANDER_TECH = "arcanumCommanderPurgeTech_";
    private static final String CHOOSE_COMMANDER_FRAGMENT_TYPE = "arcanumCommanderChooseFragmentType_";
    private static final String GET_COMMANDER_RELIC = "arcanumCommanderGetRelic";
    private static final String CHOOSE_COMMANDER_RELIC_TECH = "arcanumCommanderRelicTech_";
    private static final String CHOOSE_COMMANDER_RELIC_FIRST_FRAGMENT = "arcanumCommanderRelicFirstFragment_";
    private static final String CHOOSE_COMMANDER_RELIC_SECOND_FRAGMENT = "arcanumCommanderRelicSecondFragment_";
    private static final String GAIN_HERO_FRAGMENT = "arcanumHeroGainFragment_";
    // Commander
    public static void offerArcanumCommanderTechPurge(
            Game game, Player player, GenericInteractionCreateEvent event, int purgedFragmentCount) {
        if (game == null
                || player == null
                || event == null
                || purgedFragmentCount < 2
                || !game.playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")) {
            return;
        }
        List<Button> buttons = getArcanumCommanderTechPurgeButtons(player);
        if (buttons.isEmpty()) {
            return;
        }
        List<Button> extraButtons = List.of(Buttons.DECLINE_DELETE_BUTTONS);
        List<Button> displayedButtons = buttons.size() <= 24
                ? new ArrayList<>(buttons)
                : NewStuffHelper.buttonPagination(
                        buttons, extraButtons, player.factionButtonChecker() + PURGE_COMMANDER_TECH, 25, 0, false);
        if (buttons.size() <= 24) {
            displayedButtons.addAll(extraButtons);
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", you may use Orthis Lithon, the Arcanum commander, to purge 1 technology as a relic fragment.",
                displayedButtons);
    }

    @ButtonHandler(PURGE_COMMANDER_TECH)
    public static void chooseArcanumCommanderFragmentType(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techID = buttonID.substring(PURGE_COMMANDER_TECH.length());
        if (game == null
                || player == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")
                || !player.hasTech(techID)
                || Mapper.getTech(techID) == null) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> techButtons = getArcanumCommanderTechPurgeButtons(player);
        List<Button> extraButtons = List.of(Buttons.DECLINE_DELETE_BUTTONS);
        String message = player.getRepresentation()
                + ", you may use Orthis Lithon, the Arcanum commander, to purge 1 technology as a relic fragment.";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                techButtons,
                extraButtons,
                message,
                player.factionButtonChecker() + PURGE_COMMANDER_TECH,
                buttonID)) {
            return;
        }
        List<Button> buttons = List.of(
                Buttons.blue(
                        player.factionButtonChecker() + CHOOSE_COMMANDER_FRAGMENT_TYPE + techID + "|cultural",
                        "Cultural"),
                Buttons.green(
                        player.factionButtonChecker() + CHOOSE_COMMANDER_FRAGMENT_TYPE + techID + "|industrial",
                        "Industrial"),
                Buttons.red(
                        player.factionButtonChecker() + CHOOSE_COMMANDER_FRAGMENT_TYPE + techID + "|hazardous",
                        "Hazardous"),
                Buttons.gray(
                        player.factionButtonChecker() + CHOOSE_COMMANDER_FRAGMENT_TYPE + techID + "|frontier",
                        "Frontier"),
                Buttons.DECLINE_DELETE_BUTTONS);
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the relic fragment type for "
                        + Mapper.getTech(techID).getNameRepresentation() + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_COMMANDER_FRAGMENT_TYPE)
    public static void purgeArcanumCommanderTechnology(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload =
                buttonID.substring(CHOOSE_COMMANDER_FRAGMENT_TYPE.length()).split("\\|", 2);
        if (game == null
                || player == null
                || payload.length != 2
                || !game.playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")
                || !player.hasTech(payload[0])
                || Mapper.getTech(payload[0]) == null
                || !List.of(Constants.CULTURAL, Constants.INDUSTRIAL, Constants.HAZARDOUS, Constants.FRONTIER)
                        .contains(payload[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        PlayerTechService.purgeTech(event, player, payload[0]);
        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " used Orthis Lithon to purge "
                        + Mapper.getTech(payload[0]).getNameRepresentation() + " as a " + payload[1]
                        + " relic fragment. Resolve the current fragment effect accordingly.");
    }

    private static List<Button> getArcanumCommanderTechPurgeButtons(Player player) {
        return player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .map(tech -> Buttons.red(
                        player.factionButtonChecker() + PURGE_COMMANDER_TECH + tech.getAlias(),
                        "Purge " + tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
    }

    public static Button getArcanumCommanderGetRelicButton(Game game, Player player) {
        if (game == null
                || player == null
                || player.enoughFragsForRelic()
                || player.getFragments().size() < 2
                || player.getTechs().isEmpty()
                || !game.playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")) {
            return null;
        }
        return Buttons.green(player.factionButtonChecker() + GET_COMMANDER_RELIC, "Get Relic", FactionEmojis.arcanum);
    }

    @ButtonHandler(GET_COMMANDER_RELIC)
    public static void chooseArcanumCommanderRelicTech(ButtonInteractionEvent event, Game game, Player player) {
        Button commanderRelicButton = getArcanumCommanderGetRelicButton(game, player);
        if (commanderRelicButton == null) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = player.getTechs().stream()
                .map(Mapper::getTech)
                .filter(Objects::nonNull)
                .map(tech -> Buttons.green(
                        player.factionButtonChecker() + CHOOSE_COMMANDER_RELIC_TECH + tech.getAlias(),
                        "Purge " + tech.getName(),
                        tech.getCondensedReqsEmojis(true)))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the technology to purge as a relic fragment for Orthis Lithon.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(CHOOSE_COMMANDER_RELIC_TECH)
    public static void chooseArcanumCommanderRelicFirstFragment(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String techID = buttonID.substring(CHOOSE_COMMANDER_RELIC_TECH.length());
        if (game == null
                || player == null
                || !game.playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")
                || !player.hasTech(techID)
                || player.getFragments().size() < 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = player.getFragments().stream()
                .map(Mapper::getExplore)
                .filter(Objects::nonNull)
                .map(fragment -> Buttons.green(
                        player.factionButtonChecker() + CHOOSE_COMMANDER_RELIC_FIRST_FRAGMENT + techID + "|"
                                + fragment.getAlias(),
                        fragment.getName(),
                        ExploreEmojis.getFragEmoji(fragment.getType())))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the first relic fragment to purge.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_COMMANDER_RELIC_FIRST_FRAGMENT)
    public static void chooseArcanumCommanderRelicSecondFragment(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(CHOOSE_COMMANDER_RELIC_FIRST_FRAGMENT.length())
                .split("\\|", 2);
        if (game == null
                || player == null
                || payload.length != 2
                || !game.playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")
                || !player.hasTech(payload[0])
                || !player.getFragments().contains(payload[1])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = player.getFragments().stream()
                .filter(fragmentID -> !fragmentID.equals(payload[1]))
                .map(Mapper::getExplore)
                .filter(Objects::nonNull)
                .map(fragment -> Buttons.green(
                        player.factionButtonChecker() + CHOOSE_COMMANDER_RELIC_SECOND_FRAGMENT + payload[0] + "|"
                                + payload[1] + "|" + fragment.getAlias(),
                        fragment.getName(),
                        ExploreEmojis.getFragEmoji(fragment.getType())))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation() + ", choose the second relic fragment to purge.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(CHOOSE_COMMANDER_RELIC_SECOND_FRAGMENT)
    public static void resolveArcanumCommanderRelic(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(CHOOSE_COMMANDER_RELIC_SECOND_FRAGMENT.length())
                .split("\\|", 3);
        if (game == null
                || player == null
                || payload.length != 3
                || !game.playerHasLeaderUnlockedOrAlliance(player, "arcanumcommander")
                || !player.hasTech(payload[0])
                || payload[1].equals(payload[2])
                || !player.getFragments().contains(payload[1])
                || !player.getFragments().contains(payload[2])) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.removeFragment(payload[1]);
        player.removeFragment(payload[2]);
        game.purgeExplore(payload[1]);
        game.purgeExplore(payload[2]);
        int standardFragmentsPurged = 0;
        if (!StonebornAbilityHandler.returnPurgedFragment(game, payload[1])) {
            standardFragmentsPurged++;
        }
        if (!StonebornAbilityHandler.returnPurgedFragment(game, payload[2])) {
            standardFragmentsPurged++;
        }
        game.setNumberOfPurgedFragments(game.getNumberOfPurgedFragments() + standardFragmentsPurged);
        StonebornAbilityHandler.offerFragmentumTokenPlacement(game, player, event, List.of(payload[1], payload[2]));
        ButtonHelperExplore.offerSupermassiveFragmentGainIfApplicable(game, player, event, payload[1]);
        ButtonHelperExplore.offerSupermassiveFragmentGainIfApplicable(game, player, event, payload[2]);
        PlayerTechService.purgeTech(event, player, payload[0]);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " purged 2 relic fragments with Orthis Lithon.");
        RelicHelper.drawRelicAndNotify(player, event, game);
        DSHelperBreakthroughs.doLanefirBtCheck(game, player);
        StonebornLeadersHandler.resolveTempusAfterNonActionCardComponentPurge(game, player);
        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
        ButtonHelper.deleteMessage(event);
    }

    public static void startArcanumHero(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        sendArcanumHeroFragmentButtons(event, game, player, 2, false);
        for (int i = 0; i < 2; i++) {
            MessageHelper.sendMessageToChannelWithButton(
                    event.getMessageChannel(),
                    player.getRepresentation() + ", research a technology for _Power Conduits - True Resurrection_.",
                    Buttons.green(
                            player.factionButtonChecker() + "getAllTechOfType_allTechResearchable",
                            "Research a Technology"));
        }
    }

    @ButtonHandler(GAIN_HERO_FRAGMENT)
    public static void gainArcanumHeroFragment(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(GAIN_HERO_FRAGMENT.length()).split("\\|", 2);
        if (game == null || player == null || payload.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        int remainingFragments;
        try {
            remainingFragments = Integer.parseInt(payload[0]);
        } catch (NumberFormatException e) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        String fragmentID = payload[1];
        if (remainingFragments < 1 || !getPurgedArcanumHeroFragments(game).contains(fragmentID)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.addFragment(fragmentID);
        game.setNumberOfPurgedFragments(Math.max(0, game.getNumberOfPurgedFragments() - 1));
        ExploreModel fragment = Mapper.getExplore(fragmentID);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentation() + " gained " + ExploreEmojis.getFragEmoji(fragment.getType()) + " **"
                        + fragment.getName() + "** relic fragment using _Power Conduits - True Resurrection_.");
        if (remainingFragments == 1) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        sendArcanumHeroFragmentButtons(event, game, player, remainingFragments - 1, true);
    }

    private static void sendArcanumHeroFragmentButtons(
            GenericInteractionCreateEvent event,
            Game game,
            Player player,
            int remainingFragments,
            boolean editExistingPrompt) {
        List<Button> buttons = getPurgedArcanumHeroFragments(game).stream()
                .map(Mapper::getExplore)
                .filter(Objects::nonNull)
                .map(fragment -> Buttons.green(
                        player.factionButtonChecker() + GAIN_HERO_FRAGMENT + remainingFragments + "|"
                                + fragment.getAlias(),
                        fragment.getName(),
                        ExploreEmojis.getFragEmoji(fragment.getType())))
                .toList();
        if (buttons.isEmpty()) {
            if (editExistingPrompt && event instanceof ButtonInteractionEvent buttonEvent) {
                ButtonHelper.deleteMessage(buttonEvent);
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentation() + " has no purged relic fragments available to gain.");
            return;
        }
        String message = player.getRepresentation() + ", choose a purged relic fragment to gain for "
                + "_Power Conduits - True Resurrection_. " + remainingFragments + " remaining.";
        if (editExistingPrompt && event instanceof ButtonInteractionEvent buttonEvent) {
            MessageHelper.editMessageWithButtons(buttonEvent, message, buttons);
        } else {
            MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message, buttons);
        }
    }

    private static List<String> getPurgedArcanumHeroFragments(Game game) {
        var explorationDeck = Mapper.getDeck(game.getExplorationDeckID());
        if (explorationDeck == null) {
            return List.of();
        }
        Set<String> unavailableExplores = new HashSet<>();
        for (String trait :
                List.of(Constants.CULTURAL, Constants.HAZARDOUS, Constants.INDUSTRIAL, Constants.FRONTIER)) {
            unavailableExplores.addAll(game.getExploreDeck(trait));
            unavailableExplores.addAll(game.getExploreDiscard(trait));
        }
        for (Player gamePlayer : game.getPlayers().values()) {
            unavailableExplores.addAll(gamePlayer.getFragments());
        }
        return explorationDeck.getNewDeck().stream()
                .filter(fragmentID -> !unavailableExplores.contains(fragmentID))
                .filter(fragmentID -> {
                    ExploreModel fragment = Mapper.getExplore(fragmentID);
                    return fragment != null && Constants.FRAGMENT.equalsIgnoreCase(fragment.getResolution());
                })
                .toList();
    }
}
