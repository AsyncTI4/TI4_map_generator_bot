package ti4.service.objectives;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;
import ti4.model.PublicObjectiveModel;
import ti4.model.SecretObjectiveModel;

@UtilityClass
public class OPlusPlusCouncilService {

    private static final int DEALT_STAGE1 = 5;
    private static final int DEALT_STAGE2 = 5;
    private static final int DEALT_SECRETS = 8;
    private static final List<Integer> CATEGORY_ACCENTS = List.of(0xff0000, 0x1900ff, 0x0dd117);
    private static final String STATUS_MESSAGE_ID_KEY = "oplusplusCouncilStatusMessageID";
    private static final String PEEKABLE_S1_COUNT_KEY = "oplusplusCouncilPeekableS1Count";
    private static final String PEEKABLE_S2_COUNT_KEY = "oplusplusCouncilPeekableS2Count";
    private static final int DEFAULT_PEEKABLE_COUNT = 5;
    private static final String PICK_S1_PREFIX = "oplusplusCouncilPickS1_";
    private static final String PICK_S2_PREFIX = "oplusplusCouncilPickS2_";
    private static final String PICK_SO_PREFIX = "oplusplusCouncilPickSO_";
    private static final String CONFIRM_PREFIX = "oplusplusCouncilConfirm_";

    public static void start(Game game, ButtonInteractionEvent event) {
        // Objectives can already be sitting in the peekable preview lists (from the game's normal
        // pre-council setup, or a homebrew mode like 4/4/4 that changes how many are peekable)
        // rather than in the deck itself - merge them back in first so dealing draws from the whole
        // pool, and remember the configured count so finish() can restore it instead of assuming 5.
        game.setStoredValue(
                PEEKABLE_S1_COUNT_KEY,
                String.valueOf(game.getPublicObjectives1Peekable().size()));
        game.setStoredValue(
                PEEKABLE_S2_COUNT_KEY,
                String.valueOf(game.getPublicObjectives2Peekable().size()));
        game.getPublicObjectives1().addAll(game.getPublicObjectives1Peekable());
        game.getPublicObjectives1Peekable().clear();
        game.getPublicObjectives1Peeked().clear();
        game.getPublicObjectives2().addAll(game.getPublicObjectives2Peekable());
        game.getPublicObjectives2Peekable().clear();
        game.getPublicObjectives2Peeked().clear();

        for (Player player : joinedPlayers(game)) {
            player.setOplusplusCouncilConfirmed(false);
            List<String> stage1 = deal(game.getPublicObjectives1(), DEALT_STAGE1);
            List<String> stage2 = deal(game.getPublicObjectives2(), DEALT_STAGE2);
            List<String> secrets = deal(game.getSecretObjectives(), DEALT_SECRETS);
            game.setStoredValue(dealtKey("S1", player), String.join(",", stage1));
            game.setStoredValue(dealtKey("S2", player), String.join(",", stage2));
            game.setStoredValue(dealtKey("SO", player), String.join(",", secrets));
            game.removeStoredValue(pickKey("S1", player));
            game.removeStoredValue(pickKey("S2", player));
            game.removeStoredValue(pickKey("SO", player));
            sendPurgeMenus(game, player, stage1, stage2, secrets);
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "**Objective Council**: each player has been dealt "
                        + DEALT_STAGE1 + " Stage I, " + DEALT_STAGE2 + " Stage II, and " + DEALT_SECRETS
                        + " secret objectives in their `#cards-info` thread. Once everyone confirms their choices, the objective decks will be set.");
        updateCouncilStatusMessage(game);
    }

    @SelectionHandler(PICK_S1_PREFIX)
    public static void pickStage1(Game game, StringSelectInteractionEvent event, String componentID) {
        Player player = playerFromComponentID(game, componentID, PICK_S1_PREFIX);
        if (player == null) return;
        game.setStoredValue(pickKey("S1", player), String.join(",", event.getValues()));
    }

    @SelectionHandler(PICK_S2_PREFIX)
    public static void pickStage2(Game game, StringSelectInteractionEvent event, String componentID) {
        Player player = playerFromComponentID(game, componentID, PICK_S2_PREFIX);
        if (player == null) return;
        game.setStoredValue(pickKey("S2", player), String.join(",", event.getValues()));
    }

    @SelectionHandler(PICK_SO_PREFIX)
    public static void pickSecrets(Game game, StringSelectInteractionEvent event, String componentID) {
        Player player = playerFromComponentID(game, componentID, PICK_SO_PREFIX);
        if (player == null) return;
        game.setStoredValue(pickKey("SO", player), String.join(",", event.getValues()));
    }

    private static Player playerFromComponentID(Game game, String componentID, String prefix) {
        String userID = componentID.substring(prefix.length());
        return joinedPlayers(game).stream()
                .filter(p -> p.getUserID().equals(userID))
                .findFirst()
                .orElse(null);
    }

    @ButtonHandler(CONFIRM_PREFIX)
    public static void confirmPurge(Game game, ButtonInteractionEvent event, String buttonID) {
        String userID = buttonID.substring(CONFIRM_PREFIX.length());
        Player player = joinedPlayers(game).stream()
                .filter(p -> p.getUserID().equals(userID))
                .findFirst()
                .orElse(null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "Couldn't find you in this game anymore - nothing was confirmed.");
            return;
        }

        if (player.isOplusplusCouncilConfirmed()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "You've already confirmed your objective choices.");
            return;
        }

        if (!hasPicked(game, pickKey("S1", player))
                || !hasPicked(game, pickKey("S2", player))
                || !hasPicked(game, pickKey("SO", player))) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(),
                    player.getRepresentation()
                            + " you need to make a selection in all three menus (Stage I, Stage II, and secrets) before confirming.");
            return;
        }

        List<String> dealtS1 = idsFromStored(game, dealtKey("S1", player));
        List<String> keepS1 = idsFromStored(game, pickKey("S1", player));
        game.setStoredValue(dealtKey("S1", player), String.join(",", keepS1));

        List<String> dealtS2 = idsFromStored(game, dealtKey("S2", player));
        List<String> keepS2 = idsFromStored(game, pickKey("S2", player));
        game.setStoredValue(dealtKey("S2", player), String.join(",", keepS2));

        List<String> dealtSO = idsFromStored(game, dealtKey("SO", player));
        List<String> keepSO = idsFromStored(game, pickKey("SO", player));
        game.setStoredValue(dealtKey("SO", player), String.join(",", keepSO));

        player.setOplusplusCouncilConfirmed(true);
        ButtonHelper.deleteMessage(event);
        sendConfirmSummary(player, dealtS1, keepS1, dealtS2, keepS2, dealtSO, keepSO);
        updateCouncilStatusMessage(game);

        if (readyToFinish(game)) {
            finish(game);
        }
    }

    private static void finish(Game game) {
        List<String> finalStage1 = new ArrayList<>();
        List<String> finalStage2 = new ArrayList<>();
        List<String> finalSecrets = new ArrayList<>();

        for (Player player : joinedPlayers(game)) {
            finalStage1.addAll(idsFromStored(game, dealtKey("S1", player)));
            finalStage2.addAll(idsFromStored(game, dealtKey("S2", player)));
            finalSecrets.addAll(idsFromStored(game, dealtKey("SO", player)));
            player.setOplusplusCouncilConfirmed(false);
            game.removeStoredValue(dealtKey("S1", player));
            game.removeStoredValue(dealtKey("S2", player));
            game.removeStoredValue(dealtKey("SO", player));
            game.removeStoredValue(pickKey("S1", player));
            game.removeStoredValue(pickKey("S2", player));
            game.removeStoredValue(pickKey("SO", player));
        }
        game.removeStoredValue(STATUS_MESSAGE_ID_KEY);

        Collections.shuffle(finalStage1);
        Collections.shuffle(finalStage2);
        Collections.shuffle(finalSecrets);
        game.getPublicObjectives1().clear();
        game.getPublicObjectives1().addAll(finalStage1);
        game.getPublicObjectives1Peekable().clear();
        game.getPublicObjectives1Peeked().clear();
        game.getPublicObjectives2().clear();
        game.getPublicObjectives2().addAll(finalStage2);
        game.getPublicObjectives2Peekable().clear();
        game.getPublicObjectives2Peeked().clear();
        game.getSecretObjectives().clear();
        game.getSecretObjectives().addAll(finalSecrets);

        // Restore whatever peekable count was configured before the council started (captured in
        // start()), rather than assuming the default 5 - a homebrew mode like 4/4/4 sets this to 4,
        // and finish() must not silently override that choice.
        int peekableS1 = peekableCountOrDefault(game, PEEKABLE_S1_COUNT_KEY);
        int peekableS2 = peekableCountOrDefault(game, PEEKABLE_S2_COUNT_KEY);
        game.removeStoredValue(PEEKABLE_S1_COUNT_KEY);
        game.removeStoredValue(PEEKABLE_S2_COUNT_KEY);
        game.setUpPeekableObjectives(peekableS1, 1);
        game.setUpPeekableObjectives(peekableS2, 2);

        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "**Objective Council complete!** Everyone's chosen objectives have been pooled and shuffled into the Stage I ("
                        + finalStage1.size() + "), Stage II (" + finalStage2.size() + "), and secret ("
                        + finalSecrets.size() + ") decks for this game.");
    }

    // ---- dealing & stored-value bookkeeping ----------------------------------------------------

    private static List<String> deal(List<String> deck, int count) {
        List<String> dealt = new ArrayList<>();
        for (int i = 0; i < count && !deck.isEmpty(); i++) {
            dealt.add(deck.removeFirst());
        }
        return dealt;
    }

    private static List<Player> joinedPlayers(Game game) {
        return game.getPlayers().values().stream()
                .filter(p -> !p.isDummy() && !p.isNpc())
                .toList();
    }

    private static String dealtKey(String category, Player player) {
        return "oplusplusCouncilDealt" + category + "_" + player.getUserID();
    }

    private static String pickKey(String category, Player player) {
        return "oplusplusCouncilPick" + category + "_" + player.getUserID();
    }

    private static int maxPurgePerType(Game game) {
        return joinedPlayers(game).size() == 3 ? 2 : 3;
    }

    private static int peekableCountOrDefault(Game game, String key) {
        String stored = game.getStoredValue(key);
        if (stored.isBlank()) return DEFAULT_PEEKABLE_COUNT;
        return Integer.parseInt(stored);
    }

    private static List<String> idsFromStored(Game game, String key) {
        String val = game.getStoredValue(key);
        if (val.isBlank()) return new ArrayList<>();
        return new ArrayList<>(List.of(val.split(",")));
    }

    private static boolean hasPicked(Game game, String key) {
        return game.getStoredValueMap().containsKey(key);
    }

    private static boolean readyToFinish(Game game) {
        return joinedPlayers(game).stream().allMatch(Player::isOplusplusCouncilConfirmed);
    }

    // ---- status message (actions channel) ------------------------------------------------------

    private static String councilStatusMessage(Game game) {
        StringBuilder sb = new StringBuilder("### __Objective Council Status__:\n");
        for (Player player : joinedPlayers(game)) {
            sb.append("> ")
                    .append(player.isOplusplusCouncilConfirmed() ? "✅" : "❌")
                    .append(" ")
                    .append(player.getRepresentationNoPing())
                    .append('\n');
        }
        return sb.toString();
    }

    private static void updateCouncilStatusMessage(Game game) {
        // Best-effort: a REST failure here must never escape, since confirmPurge calls this after
        // already mutating state (the confirmed flag, the dealt-list overwrite) that still needs to
        // reach this interaction's save regardless of whether the status message itself goes out.
        try {
            String msg = councilStatusMessage(game);
            MessageChannel channel = game.getActionsChannel();
            String existingID = game.getStoredValue(STATUS_MESSAGE_ID_KEY);
            Message sent = existingID.isBlank() ? null : tryEditStatusMessage(channel, existingID, msg);
            if (sent == null) {
                sent = channel.sendMessage(msg).complete();
            }
            // .complete(), not .queue(): the id must be set in time to be captured by this
            // interaction's own save, which runs synchronously right after this method returns.
            game.setStoredValue(STATUS_MESSAGE_ID_KEY, sent.getId());
        } catch (Exception e) {
            BotLogger.catchRestError(e);
        }
    }

    private static Message tryEditStatusMessage(MessageChannel channel, String messageId, String msg) {
        try {
            return channel.retrieveMessageById(messageId)
                    .flatMap(m -> m.editMessage(msg))
                    .complete();
        } catch (Exception e) {
            return null;
        }
    }

    // ---- purge menus (per-player cards-info thread) ---------------------------------------------

    private static void sendPurgeMenus(
            Game game, Player player, List<String> stage1, List<String> stage2, List<String> secrets) {
        ThreadChannel thread = player.getCardsInfoThread();
        if (thread == null) {
            MessageHelper.sendMessageToChannel(
                    game.getActionsChannel(),
                    "Couldn't find or create a cards-info thread for " + player.getRepresentation()
                            + " - they were dealt objectives but never got their purge menus.");
            return;
        }

        int max = maxPurgePerType(game);

        MessageV2Builder builder = new MessageV2Builder(thread);
        builder.append(
                player.getRepresentation()
                        + ", choose which of your dealt objectives you want available for this game (everything else is purged), then confirm. You can change your selections until you confirm.");
        builder.append(purgeCategoryContainer(
                "Stage I",
                max,
                stage1,
                PICK_S1_PREFIX + player.getUserID(),
                Mapper::getPublicObjective,
                CATEGORY_ACCENTS.get(0)));
        builder.append(purgeCategoryContainer(
                "Stage II",
                max,
                stage2,
                PICK_S2_PREFIX + player.getUserID(),
                Mapper::getPublicObjective,
                CATEGORY_ACCENTS.get(1)));
        builder.append(purgeCategoryContainer(
                "Secrets",
                max,
                secrets,
                PICK_SO_PREFIX + player.getUserID(),
                Mapper::getSecretObjective,
                CATEGORY_ACCENTS.get(2)));
        builder.append(Buttons.green(CONFIRM_PREFIX + player.getUserID(), "Confirm Choices"));
        builder.send();
    }

    private static Container purgeCategoryContainer(
            String label,
            int purgeCount,
            List<String> ids,
            String menuCustomID,
            Function<String, ?> modelLookup,
            int accentColor) {
        int keepCount = Math.max(ids.size() - purgeCount, 0);
        StringBuilder sb =
                new StringBuilder("### ").append(label).append(" — choose ").append(keepCount);
        for (String id : ids) {
            Object model = modelLookup.apply(id);
            sb.append("\n\n**").append(modelNameOf(model, id)).append("**\n> ").append(modelTextOf(model));
        }
        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of(sb.toString()));
        if (keepCount > 0) {
            components.add(ActionRow.of(
                    buildMenu(menuCustomID, "Choose " + keepCount + " " + label, ids, keepCount, modelLookup)));
        }
        return Container.of(components).withAccentColor(accentColor);
    }

    private static StringSelectMenu buildMenu(
            String customID, String placeholder, List<String> ids, int required, Function<String, ?> modelLookup) {
        StringSelectMenu.Builder builder = StringSelectMenu.create(customID);
        builder.setPlaceholder(placeholder);
        builder.setMinValues(required);
        builder.setMaxValues(required);
        for (String id : ids) {
            String name = modelNameOf(modelLookup.apply(id), id);
            builder.addOptions(SelectOption.of(name.length() > 100 ? name.substring(0, 100) : name, id));
        }
        return builder.build();
    }

    // ---- per-player confirmation summary (cards-info thread) -------------------------------------

    private static void sendConfirmSummary(
            Player player,
            List<String> dealtS1,
            List<String> keepS1,
            List<String> dealtS2,
            List<String> keepS2,
            List<String> dealtSecrets,
            List<String> keepSO) {
        StringBuilder sb = new StringBuilder("### __Your Objective Council Choices__\n");
        sb.append("**Stage I**\n");
        appendSummaryLines(sb, dealtS1, keepS1, Mapper::getPublicObjective);
        sb.append("**Stage II**\n");
        appendSummaryLines(sb, dealtS2, keepS2, Mapper::getPublicObjective);
        sb.append("**Secrets**\n");
        appendSummaryLines(sb, dealtSecrets, keepSO, Mapper::getSecretObjective);
        MessageHelper.sendMessageToChannel(player.getCardsInfoThread(), sb.toString());
    }

    private static void appendSummaryLines(
            StringBuilder sb, List<String> dealt, List<String> kept, Function<String, ?> modelLookup) {
        for (String id : dealt) {
            Object model = modelLookup.apply(id);
            sb.append(kept.contains(id) ? "✅" : "❌")
                    .append(" **")
                    .append(modelNameOf(model, id))
                    .append("**\n> ")
                    .append(modelTextOf(model))
                    .append('\n');
        }
    }

    // ---- objective model lookups --------------------------------------------------------------

    private static String modelNameOf(Object model, String fallback) {
        if (model instanceof PublicObjectiveModel po) return po.getName();
        if (model instanceof SecretObjectiveModel so) return so.getName();
        return fallback;
    }

    private static String modelTextOf(Object model) {
        if (model instanceof PublicObjectiveModel po) return po.getText();
        if (model instanceof SecretObjectiveModel so) return so.getText();
        return "";
    }
}
