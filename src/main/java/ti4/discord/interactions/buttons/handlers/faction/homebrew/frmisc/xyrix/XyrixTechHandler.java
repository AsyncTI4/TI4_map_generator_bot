package ti4.discord.interactions.buttons.handlers.faction.homebrew.frmisc.xyrix;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.ButtonHelper;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.model.TechnologyModel;
import ti4.service.emoji.TechEmojis;

@UtilityClass
public class XyrixTechHandler {

    private static final String LIBRARY_LOANS = "frmiscxyrixg";
    private static final String CREPUSCULAR_COPY = "frmiscxyrixb";
    private static final String LIBRARY_TARGET = "xyrixLibraryTarget_";
    private static final String LIBRARY_CARD = "xyrixLibraryCard_";
    private static final String LIBRARY_PENDING = "xyrixLibraryPending_";
    private static final String COPY_PROMPT = "xyrixCopyPrompt_";
    private static final String COPY_TECH = "xyrixCopyTech_";
    private static final String COPY_PAGE = "xyrixCopyPage_";
    private static final String COPY_SELECTED = "xyrixCopySelected_";

    public static void offerIntergalacticLibraryLoans(Game game, Player player) {
        List<Button> buttons = game.getRealPlayersExcludingThis(player).stream()
                .map(target -> Buttons.gray(
                        player.factionButtonChecker() + LIBRARY_TARGET + target.getFaction(),
                        targetButtonLabel(game, target)))
                .toList();
        if (buttons.isEmpty() || player.getActionCards().isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "_Intergalactic Library Loans_ requires another player and an action card.");
            return;
        }
        game.setStoredValue(libraryPendingKey(player), String.valueOf(game.getRound()));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + ", choose a player to receive 1 action card for _Intergalactic Library Loans_.",
                buttons);
    }

    @ButtonHandler(LIBRARY_TARGET)
    public static void chooseLibraryLoansTarget(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!hasPendingLibraryLoans(game, player) || player.getActionCards().isEmpty()) return;
        Player target = game.getPlayerFromColorOrFaction(buttonID.replace(LIBRARY_TARGET, ""));
        if (target == null || target == player) return;
        List<Button> buttons = player.getActionCards().entrySet().stream()
                .map(entry -> Buttons.green(
                        player.factionButtonChecker() + LIBRARY_CARD + target.getFaction() + "_" + entry.getValue(),
                        Mapper.getActionCard(entry.getKey()).getName()))
                .toList();
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", choose the action card to give to "
                        + targetButtonLabel(game, target) + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(LIBRARY_CARD)
    public static void resolveIntergalacticLibraryLoans(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!hasPendingLibraryLoans(game, player)) return;
        String[] payload = buttonID.replace(LIBRARY_CARD, "").split("_");
        if (payload.length != 2) return;
        Player target = game.getPlayerFromColorOrFaction(payload[0]);
        int cardIndex;
        try {
            cardIndex = Integer.parseInt(payload[1]);
        } catch (NumberFormatException e) {
            return;
        }
        String card = player.getActionCards().entrySet().stream()
                .filter(entry -> entry.getValue() == cardIndex)
                .map(java.util.Map.Entry::getKey)
                .findFirst()
                .orElse(null);
        if (target == null || target == player || card == null) return;

        ActionCardHelper.sendActionCard(event, game, player, target, card);
        ActionCardHelper.drawActionCards(player, 2);
        game.removeStoredValue(libraryPendingKey(player));
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + " gave an action card to " + target.getRepresentationNoPing()
                        + " and drew 2 action cards with _Intergalactic Library Loans_.");
        ButtonHelper.deleteMessage(event);
    }

    public static void offerCrepuscularCopy(Game game, Player activatingPlayer, Tile activeSystem) {
        for (Player player : game.getRealPlayers()) {
            if (!player.hasTech(CREPUSCULAR_COPY)
                    || !game.getStoredValue(copyPromptKey(player)).isEmpty()
                    || !game.getStoredValue(copySelectedKey(player)).isEmpty()) continue;
            if (player != activatingPlayer && !activeSystem.containsPlayersUnits(player)) continue;
            game.setStoredValue(copyPromptKey(player), activeSystem.getPosition());
            MessageHelper.sendMessageToChannelWithButton(
                    player.getCardsInfoThread(),
                    player.getRepresentationNoPing() + ", you may choose a technology for _Crepuscular Copy_.",
                    Buttons.green(player.factionButtonChecker() + COPY_PAGE + "0", "Choose Technology"));
        }
    }

    @ButtonHandler(COPY_PAGE)
    public static void offerCrepuscularCopyTechnologies(
            ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!canResolveCopy(game, player)) return;
        int page;
        try {
            page = Integer.parseInt(buttonID.replace(COPY_PAGE, ""));
        } catch (NumberFormatException e) {
            return;
        }
        List<TechnologyModel> technologies = copyableTechnologies(game, player);
        int start = page * 24;
        if (start >= technologies.size()) return;
        int end = Math.min(start + 24, technologies.size());
        List<Button> buttons = new ArrayList<>();
        for (TechnologyModel technology : technologies.subList(start, end)) {
            buttons.add(Buttons.gray(
                    player.factionButtonChecker() + COPY_TECH + technology.getAlias(),
                    technology.getName(),
                    TechEmojis.getBasicTechEmoji(technology.getFirstType().toString())));
        }
        if (end < technologies.size()) {
            buttons.add(Buttons.blue(player.factionButtonChecker() + COPY_PAGE + (page + 1), "Next Page"));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", choose a technology for _Crepuscular Copy_.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(COPY_TECH)
    public static void resolveCrepuscularCopy(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (!canResolveCopy(game, player)) return;
        TechnologyModel technology = Mapper.getTech(buttonID.replace(COPY_TECH, ""));
        if (technology == null || !copyableTechnologies(game, player).contains(technology)) return;
        player.getTechs().add(technology.getAlias());
        game.setStoredValue(copySelectedKey(player), technology.getAlias());
        MessageHelper.sendMessageToChannelWithEmbed(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing()
                        + " is using _Crepuscular Copy_ to gain this technology's ability text until the end of the tactical action.",
                technology.getRepresentationEmbed());
        ButtonHelper.deleteMessage(event);
    }

    public static void clearCrepuscularCopy(Game game) {
        for (Player player : game.getRealPlayers()) {
            String copiedTechnology = game.getStoredValue(copySelectedKey(player));
            if (!copiedTechnology.isEmpty()) {
                player.getTechs().remove(copiedTechnology);
            }
            game.removeStoredValue(copyPromptKey(player));
            game.removeStoredValue(copySelectedKey(player));
        }
    }

    public static void clearRoundState(Game game, Player player) {
        game.removeStoredValue(libraryPendingKey(player));
    }

    private static boolean canResolveCopy(Game game, Player player) {
        return player.hasTech(CREPUSCULAR_COPY)
                && !game.getStoredValue(copyPromptKey(player)).isEmpty()
                && game.getStoredValue(copySelectedKey(player)).isEmpty();
    }

    private static boolean hasPendingLibraryLoans(Game game, Player player) {
        return player.hasTech(LIBRARY_LOANS)
                && String.valueOf(game.getRound()).equals(game.getStoredValue(libraryPendingKey(player)));
    }

    private static List<TechnologyModel> copyableTechnologies(Game game, Player player) {
        return Mapper.getTechs().values().stream()
                .filter(technology -> game.getTechnologyDeck().contains(technology.getAlias()))
                .filter(technology -> technology.getFaction() == null
                        || technology.getFaction().isEmpty())
                .filter(technology -> !technology.isUnitUpgrade())
                .filter(technology -> !player.hasTech(technology.getAlias()))
                .sorted(Comparator.comparing(TechnologyModel::getName))
                .toList();
    }

    private static String targetButtonLabel(Game game, Player target) {
        return game.isFowMode() ? target.getColor() : target.getFactionModel().getShortName();
    }

    private static String copyPromptKey(Player player) {
        return COPY_PROMPT + player.getFaction();
    }

    private static String libraryPendingKey(Player player) {
        return LIBRARY_PENDING + player.getFaction();
    }

    private static String copySelectedKey(Player player) {
        return COPY_SELECTED + player.getFaction();
    }
}
