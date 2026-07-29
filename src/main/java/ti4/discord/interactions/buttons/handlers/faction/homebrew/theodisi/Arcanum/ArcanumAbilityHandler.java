package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Arcanum;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ComponentActionHelper;
import ti4.helpers.NewStuffHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class ArcanumAbilityHandler {
    private static final String PRIMORDIAL_SECRETS = "primordial_secrets";
    private static final String CHOOSE_PRIMORDIAL_PREFIX = "arcanumChoosePrimordial_";
    private static final List<String> PRIMORDIAL_TECHS =
            List.of("tharcanumpmy", "tharcanumpmg", "tharcanumpmr", "tharcanumpmb");
    private static final String USE_ROA = "useRitualOfAscension";
    private static final String PURGE_FRAG = "purgeFragForRitual_";
    private static final String RITUAL_FRAGS_PURGED = "ritualAscensionFragsPurged_";

    // Primordial Secrets
    public static void offerPrimordialSecretsButtons(Game game, Player player) {
        if (game == null || player == null || !player.hasAbility(PRIMORDIAL_SECRETS) || hasChosenPrimordial(player)) {
            return;
        }

        List<Button> buttons = new ArrayList<>();
        for (String techId : PRIMORDIAL_TECHS) {
            if (Mapper.getTech(techId) == null) {
                continue;
            }
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + CHOOSE_PRIMORDIAL_PREFIX + techId,
                    Mapper.getTech(techId).getName()));
        }
        if (buttons.isEmpty()) {
            return;
        }

        MessageHelper.sendMessageToChannelWithButtons(
                player.getCorrectChannel(),
                player.getRepresentation() + ", choose 1 primordial technology to add with **Primordial Secrets**.",
                buttons);
    }

    @ButtonHandler(CHOOSE_PRIMORDIAL_PREFIX)
    public static void resolveChoosePrimordial(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !player.hasAbility(PRIMORDIAL_SECRETS)) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        if (hasChosenPrimordial(player)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "A primordial technology has already been chosen.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        String techId = buttonID.substring(CHOOSE_PRIMORDIAL_PREFIX.length());
        if (!PRIMORDIAL_TECHS.contains(techId) || Mapper.getTech(techId) == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "That primordial technology is no longer valid.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        player.addFactionTech(techId);
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCorrectChannel(),
                player.getRepresentation() + " added " + Mapper.getTech(techId).getNameRepresentation()
                        + " using **Primordial Secrets**.",
                Mapper.getTech(techId).getRepresentationEmbed());
        if (player.getCardsInfoThread() != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCardsInfoThread(),
                    "__Primordial Technology Added__",
                    Mapper.getTech(techId).getRepresentationEmbed());
        }
        ButtonHelper.deleteMessage(event);
    }

    private static boolean hasChosenPrimordial(Player player) {
        for (String techId : PRIMORDIAL_TECHS) {
            if (player.hasTech(techId)) {
                return true;
            }
        }
        return false;
    }

    // Ritual of Ascension
    public static Button getRitualOfAscensionButton(GenericInteractionCreateEvent event, Game game, Player player) {
        return Buttons.green(player.factionButtonChecker() + USE_ROA, "Ritual of Ascension", FactionEmojis.arcanum);
    }

    @ButtonHandler(USE_ROA)
    public static void resolveRitualOfAscension(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null
                || player == null
                || !player.hasAbility("ritual_of_ascension")
                || player.getFragments().size() < 2) {
            return;
        }

        game.removeStoredValue(RITUAL_FRAGS_PURGED + player.getFaction());

        List<Button> frags = getRitualFragmentButtons(player);
        List<Button> extraButtons = List.of(Buttons.red("donePurgingRitualFrags", "Done Purging Frags"));
        List<Button> displayedButtons = frags.size() <= 24
                ? new ArrayList<>(frags)
                : NewStuffHelper.buttonPagination(
                        frags, extraButtons, player.factionButtonChecker() + PURGE_FRAG, 25, 0, false);
        if (frags.size() <= 24) {
            displayedButtons.addAll(extraButtons);
        }

        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentation()
                        + ", please choose which fragments you would like to purge to research a technology:",
                displayedButtons);
    }

    @ButtonHandler(PURGE_FRAG)
    public static void purgeFragmentForRitual(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (game == null || player == null || !player.hasAbility("ritual_of_ascension")) {
            return;
        }

        List<Button> frags = getRitualFragmentButtons(player);
        List<Button> extraButtons = List.of(Buttons.red("donePurgingRitualFrags", "Done Purging Frags"));
        String message = player.getRepresentation()
                + ", please choose which fragments you would like to purge to research a technology:";
        if (NewStuffHelper.checkAndHandlePaginationChange(
                event,
                event.getMessageChannel(),
                frags,
                extraButtons,
                message,
                player.factionButtonChecker() + PURGE_FRAG,
                buttonID)) {
            return;
        }

        String[] parts = buttonID.replace(PURGE_FRAG, "").split("\\|", 2);
        if (parts.length != 2) {
            ButtonHelper.deleteMessage(event);
            return;
        }

        Player fragmentOwner = game.getPlayerFromColorOrFaction(parts[0]);
        String fragmentId = parts[1];
        String selected = game.getStoredValue(RITUAL_FRAGS_PURGED + player.getFaction());
        List<String> selectedFragments = selected.isEmpty() ? List.of() : List.of(selected.split("\\|"));

        if (fragmentOwner != player
                || selectedFragments.size() >= 2
                || selectedFragments.contains(fragmentId)
                || !player.getFragments().contains(fragmentId)) {
            return;
        }

        player.removeFragment(fragmentId);
        game.purgeExplore(fragmentId);
        game.setStoredValue(
                RITUAL_FRAGS_PURGED + player.getFaction(),
                selected.isEmpty() ? fragmentId : selected + "|" + fragmentId);
        ButtonHelper.deleteTheOneButton(event);

        ExploreModel fragment = Mapper.getExplore(fragmentId);

        if (fragment != null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Purged " + ExploreEmojis.getFragEmoji(fragment.getType()) + fragment.getName());
        }
    }

    private static List<Button> getRitualFragmentButtons(Player player) {
        List<Button> frags = new ArrayList<>();
        for (String fragmentId : player.getFragments()) {
            ExploreModel fragment = Mapper.getExplore(fragmentId);
            if (fragment != null) {
                frags.add(Buttons.green(
                        player.factionButtonChecker() + PURGE_FRAG + player.getFaction() + "|" + fragmentId,
                        fragment.getName(),
                        ExploreEmojis.getFragEmoji(fragment.getType())));
            }
        }
        return frags;
    }

    public static void clearRitualOfAscensionStoredValues(Game game) {
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue(RITUAL_FRAGS_PURGED + player.getFaction());
        }
    }

    @ButtonHandler("donePurgingRitualFrags")
    public static void resolveRitualResearchTech(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasAbility("ritual_of_ascension")) {
            return;
        }

        String selected = game.getStoredValue(RITUAL_FRAGS_PURGED + player.getFaction());
        if (selected.isEmpty() || selected.split("\\|").length != 2) {
            MessageHelper.sendEphemeralMessageToEventChannel(event, "You must purge exactly 2 relic fragments first.");
            return;
        }
        game.removeStoredValue(RITUAL_FRAGS_PURGED + player.getFaction());
        ButtonHelper.deleteMessage(event);

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(), "Button to research a technology have been sent to your cards info thread.");

        MessageHelper.sendMessageToChannelWithButton(
                player.getCardsInfoThread(),
                player.getRepresentation() + ", you may research a technology due to _Ritual of Ascension_:",
                Buttons.green(
                        player.factionButtonChecker() + "getAllTechOfType_allTechResearchable_noPay",
                        "Research a Technology"));

        ComponentActionHelper.serveNextComponentActionButtons(event, game, player);
    }
}
