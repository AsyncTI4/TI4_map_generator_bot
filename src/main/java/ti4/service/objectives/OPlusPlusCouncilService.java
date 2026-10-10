package ti4.service.objectives;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.separator.Separator;
import net.dv8tion.jda.api.components.separator.Separator.Spacing;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.requests.RestAction;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.GameMessage;
import ti4.message.GameMessageManager;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.message.componentsV2.MessageV2Builder;

@UtilityClass
public class OPlusPlusCouncilService {

    private static final String RESPONDED_KEY = "oplusplusCouncilResponded";
    private static final int DEALT_STAGE1 = 5;
    private static final int DEALT_STAGE2 = 5;
    private static final int DEALT_SECRETS = 8;

    public static void start(Game game, ButtonInteractionEvent event) {
        game.setStoredValue(RESPONDED_KEY, "");

        int originalMaxSOCount = game.getMaxSOCountPerPlayer();
        game.setMaxSOCountPerPlayer(DEALT_SECRETS);
        for (Player player : joinedPlayers(game)) {
            List<String> stage1 = deal(game.getPublicObjectives1(), DEALT_STAGE1);
            List<String> stage2 = deal(game.getPublicObjectives2(), DEALT_STAGE2);
            game.setStoredValue(stage1Key(player), String.join(",", stage1));
            game.setStoredValue(stage2Key(player), String.join(",", stage2));
            for (int i = 0; i < DEALT_SECRETS; i++) {
                game.drawSecretObjective(player.getUserID());
            }
            sendPurgeMenus(game, player, stage1, stage2);
        }
        game.setMaxSOCountPerPlayer(originalMaxSOCount);
        game.getPublicObjectives1().clear();
        game.getPublicObjectives2().clear();
        game.getSecretObjectives().clear();

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "**Objective Council**: each player has been dealt "
                        + DEALT_STAGE1 + " Stage I, " + DEALT_STAGE2 + " Stage II, and " + DEALT_SECRETS
                        + " secret objectives in their `#cards-info` thread. Once everyone confirms their choices, the objective decks will be set.");
        updateCouncilStatusMessage(game);
    }

    private static String councilStatusMessage(Game game) {
        StringBuilder sb = new StringBuilder("### __Objective Council Status__:\n");
        for (Player player : joinedPlayers(game)) {
            sb.append("> ")
                    .append(alreadyResponded(game, player) ? "✅" : "❌")
                    .append(" ")
                    .append(player.getRepresentationNoPing())
                    .append('\n');
        }
        return sb.toString();
    }

    private static void updateCouncilStatusMessage(Game game) {
        String msg = councilStatusMessage(game);
        Optional<GameMessage> gm = GameMessageManager.getOne(game.getName(), GameMessageType.OPLUSPLUS_COUNCIL);
        if (gm.isPresent()) {
            MessageChannel channel = game.getActionsChannel();
            channel.retrieveMessageById(gm.get().messageId())
                    .flatMap(m -> m.editMessage(msg))
                    .onErrorFlatMap(err -> {
                        BotLogger.catchRestError(err);
                        return sendCouncilStatusMessage(game, msg);
                    })
                    .queue(Consumers.nop(), BotLogger::catchRestError);
        } else {
            sendCouncilStatusMessage(game, msg).queue(Consumers.nop(), BotLogger::catchRestError);
        }
    }

    private static RestAction<Message> sendCouncilStatusMessage(Game game, String msg) {
        String name = game.getName();
        long date = game.getLastModifiedDate();
        return game.getActionsChannel()
                .sendMessage(msg)
                .onSuccess(m -> GameMessageManager.replace(
                        name, new GameMessage(m.getId(), GameMessageType.OPLUSPLUS_COUNCIL, date)));
    }

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

    private static String stage1Key(Player player) {
        return "oplusplusCouncilS1_" + player.getUserID();
    }

    private static String stage2Key(Player player) {
        return "oplusplusCouncilS2_" + player.getUserID();
    }

    private static String pickKey(String category, Player player) {
        return "oplusplusCouncilPick" + category + "_" + player.getUserID();
    }

    private static int maxPurgePerType(Game game) {
        return joinedPlayers(game).size() == 3 ? 2 : 3;
    }

    private static List<String> idsFromStored(Game game, String key) {
        String val = game.getStoredValue(key);
        if (val == null || val.isBlank()) return new ArrayList<>();
        return new ArrayList<>(List.of(val.split(",")));
    }

    private static final List<Integer> CATEGORY_ACCENTS = List.of(0xff0000, 0x1900ff, 0x0dd117);

    private static void sendPurgeMenus(Game game, Player player, List<String> stage1, List<String> stage2) {
        ThreadChannel thread = player.getCardsInfoThread();
        if (thread == null) return;

        int max = maxPurgePerType(game);
        List<String> secrets = new ArrayList<>(player.getSecrets().keySet());

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
        List<ContainerChildComponent> components = new ArrayList<>();
        components.add(TextDisplay.of("### " + label + " — choose " + keepCount));
        for (String id : ids) {
            if (components.size() > 1) components.add(Separator.createDivider(Spacing.LARGE));
            Object model = modelLookup.apply(id);
            components.add(TextDisplay.of("**" + modelNameOf(model, id) + "**\n> " + modelTextOf(model)));
        }
        components.add(ActionRow.of(
                buildMenu(menuCustomID, "Choose " + keepCount + " " + label, ids, keepCount, modelLookup)));
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

    private static String modelNameOf(Object model, String fallback) {
        if (model instanceof ti4.model.PublicObjectiveModel po) return po.getName();
        if (model instanceof ti4.model.SecretObjectiveModel so) return so.getName();
        return fallback;
    }

    private static String modelTextOf(Object model) {
        if (model instanceof ti4.model.PublicObjectiveModel po) return po.getText();
        if (model instanceof ti4.model.SecretObjectiveModel so) return so.getText();
        return "";
    }

    private static final String PICK_S1_PREFIX = "oplusplusCouncilPickS1_";
    private static final String PICK_S2_PREFIX = "oplusplusCouncilPickS2_";
    private static final String PICK_SO_PREFIX = "oplusplusCouncilPickSO_";
    private static final String CONFIRM_PREFIX = "oplusplusCouncilConfirm_";

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

        if (alreadyResponded(game, player)) {
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

        List<String> keepS1 = idsFromStored(game, pickKey("S1", player));
        game.setStoredValue(stage1Key(player), String.join(",", keepS1));

        List<String> keepS2 = idsFromStored(game, pickKey("S2", player));
        game.setStoredValue(stage2Key(player), String.join(",", keepS2));

        List<String> keepSO = idsFromStored(game, pickKey("SO", player));
        List<String> dealtSecrets = new ArrayList<>(player.getSecrets().keySet());
        dealtSecrets.removeAll(keepSO);
        for (String soID : dealtSecrets) {
            player.getSecrets().remove(soID);
        }

        game.setStoredValue(RESPONDED_KEY, game.getStoredValue(RESPONDED_KEY) + "|" + userID);
        event.getHook()
                .editOriginal("Choices confirmed. Waiting on the rest of the table.")
                .useComponentsV2(true)
                .setComponents()
                .queue(Consumers.nop(), BotLogger::catchRestError);
        updateCouncilStatusMessage(game);

        if (readyToFinish(game)) {
            finish(game);
        }
    }

    private static boolean hasPicked(Game game, String key) {
        return game.getStoredValueMap().containsKey(key);
    }

    private static boolean alreadyResponded(Game game, Player player) {
        String val = game.getStoredValue(RESPONDED_KEY);
        return val != null && val.contains("|" + player.getUserID());
    }

    private static boolean readyToFinish(Game game) {
        return joinedPlayers(game).stream().allMatch(p -> alreadyResponded(game, p));
    }

    private static void finish(Game game) {
        List<String> finalStage1 = new ArrayList<>();
        List<String> finalStage2 = new ArrayList<>();
        List<String> finalSecrets = new ArrayList<>();

        for (Player player : joinedPlayers(game)) {
            finalStage1.addAll(idsFromStored(game, stage1Key(player)));
            finalStage2.addAll(idsFromStored(game, stage2Key(player)));
            finalSecrets.addAll(player.getSecrets().keySet());
            player.getSecrets().clear();
            game.removeStoredValue(stage1Key(player));
            game.removeStoredValue(stage2Key(player));
            game.removeStoredValue(pickKey("S1", player));
            game.removeStoredValue(pickKey("S2", player));
            game.removeStoredValue(pickKey("SO", player));
        }
        game.removeStoredValue(RESPONDED_KEY);
        GameMessageManager.remove(game.getName(), GameMessageType.OPLUSPLUS_COUNCIL);

        Collections.shuffle(finalStage1);
        Collections.shuffle(finalStage2);
        Collections.shuffle(finalSecrets);
        game.getPublicObjectives1().addAll(finalStage1);
        game.getPublicObjectives2().addAll(finalStage2);
        game.getSecretObjectives().addAll(finalSecrets);

        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "**Objective Council complete!** Everyone's chosen objectives have been pooled and shuffled into the Stage I ("
                        + finalStage1.size() + "), Stage II (" + finalStage2.size() + "), and secret ("
                        + finalSecrets.size() + ") decks for this game.");
    }
}
