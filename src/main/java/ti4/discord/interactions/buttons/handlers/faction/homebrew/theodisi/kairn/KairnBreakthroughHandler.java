package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.kairn;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.RelicHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.RelicModel;
import ti4.service.emoji.ExploreEmojis;

@UtilityClass
public class KairnBreakthroughHandler {
    private static final String KAIRN_BT_RELICS = "kairnBtRelics_";
    private static final String VIEW_RELICS = "viewKairnBtRelics";
    private static final String PURGE_AND_REPLACE = "kairnBtPurgeAndReplace_";
    private static final String SELECT_REPLACEMENT = "kairnBtSelectReplacement_";
    private static final String READY_BREAKTHROUGH = "readyKairnBt";

    public static void refreshRelics(Game game, Player player) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough("kairnbt")) return;
        for (String relic : getStoredRelics(game, player)) {
            game.shuffleRelicBack(relic);
        }
        List<String> relics = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String relic = game.drawRelic();
            if (relic.isEmpty()) break;
            relics.add(relic);
        }
        setStoredRelics(game, player, relics);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                player.getRepresentationNoPing() + " placed " + relics.size() + " relic"
                        + (relics.size() == 1 ? "" : "s") + " on **Relic Trading Hub**.");
    }

    public static Button getRelicsCardsInfoButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + VIEW_RELICS, "View Relic Trading Hub Relics", ExploreEmojis.Relic);
    }

    public static void offerRelicGainPrompts(Game game, Player target, String gainedRelic) {
        if (game == null || target == null || gainedRelic == null || !target.hasRelic(gainedRelic)) return;
        for (Player owner : game.getRealPlayers()) {
            if (!owner.hasUnlockedBreakthrough("kairnbt")) continue;
            List<Button> buttons = new ArrayList<>();
            if (owner.hasReadyBreakthrough("kairnbt")
                    && !getStoredRelics(game, owner).isEmpty()) {
                buttons.add(Buttons.green(
                        owner.factionButtonChecker() + PURGE_AND_REPLACE + target.getFaction() + "|" + gainedRelic,
                        "Purge and Replace",
                        ExploreEmojis.Relic));
            }
            if (owner.isBreakthroughExhausted("kairnbt")) {
                buttons.add(Buttons.gray(
                        owner.factionButtonChecker() + READY_BREAKTHROUGH,
                        "Ready Relic Trading Hub",
                        ExploreEmojis.Relic));
            }
            buttons.add(Buttons.red(owner.factionButtonChecker() + "deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(
                    owner.getCardsInfoThread(),
                    owner.getRepresentationNoPing() + ", " + target.getRepresentationNoPing()
                            + " gained a relic. You may resolve **Relic Trading Hub**.",
                    buttons);
        }
    }

    @ButtonHandler(VIEW_RELICS)
    public static void viewRelics(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough("kairnbt")) return;
        List<MessageEmbed> embeds = getStoredRelics(game, player).stream()
                .map(KairnBreakthroughHandler::getRelicModel)
                .filter(java.util.Objects::nonNull)
                .map(RelicModel::getRepresentationEmbed)
                .toList();
        MessageHelper.sendMessageToChannelWithEmbeds(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + " has these relics on **Relic Trading Hub**.",
                embeds);
    }

    @ButtonHandler(PURGE_AND_REPLACE)
    public static void purgeAndReplace(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(PURGE_AND_REPLACE.length()).split("\\|", 2);
        Player target = payload.length == 2 && game != null ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (target == null
                || player == null
                || !player.hasReadyBreakthrough("kairnbt")
                || !target.hasRelic(payload.length == 2 ? payload[1] : "")
                || getStoredRelics(game, player).isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setBreakthroughExhausted("kairnbt", true);
        target.removeRelic(payload[1]);
        target.removeExhaustedRelic(payload[1]);
        RelicHelper.resolveRelicLossEffects(game, target, payload[1]);
        RelicModel purged = getRelicModel(payload[1]);
        MessageHelper.sendMessageToChannel(
                target.getCorrectChannel(),
                target.getRepresentationNoPing() + " had "
                        + (purged == null ? "a relic" : "_" + purged.getName() + "_")
                        + " purged by **Relic Trading Hub**.");
        List<Button> buttons = new ArrayList<>();
        for (String relic : getStoredRelics(game, player)) {
            RelicModel model = getRelicModel(relic);
            if (model != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + SELECT_REPLACEMENT + target.getFaction() + "|" + relic,
                        "Give " + model.getName(),
                        ExploreEmojis.Relic));
            }
        }
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", choose the relic to give to " + target.getRepresentationNoPing()
                        + ".",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_REPLACEMENT)
    public static void selectReplacement(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] payload = buttonID.substring(SELECT_REPLACEMENT.length()).split("\\|", 2);
        Player target = payload.length == 2 && game != null ? game.getPlayerFromColorOrFaction(payload[0]) : null;
        if (target == null
                || player == null
                || !player.isBreakthroughExhausted("kairnbt")
                || !getStoredRelics(game, player).contains(payload.length == 2 ? payload[1] : "")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<String> relics = new ArrayList<>(getStoredRelics(game, player));
        relics.remove(payload[1]);
        setStoredRelics(game, player, relics);
        String relic = normalizeRelic(payload[1]);
        target.addRelic(relic);
        RelicModel model = Mapper.getRelic(relic);
        if (model != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    target.getCorrectChannel(),
                    target.getRepresentationNoPing() + " gained _" + model.getName() + "_ from **Relic Trading Hub**.",
                    model.getRepresentationEmbed(false, true));
        }
        RelicHelper.resolveRelicEffects(event, game, target, relic);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(READY_BREAKTHROUGH)
    public static void readyBreakthrough(ButtonInteractionEvent event, Player player) {
        if (player != null && player.hasUnlockedBreakthrough("kairnbt") && player.isBreakthroughExhausted("kairnbt")) {
            player.setBreakthroughExhausted("kairnbt", false);
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), player.getRepresentationNoPing() + " readied _Relic Trading Hub_.");
        }
        ButtonHelper.deleteMessage(event);
    }

    private static RelicModel getRelicModel(String relic) {
        return Mapper.getRelic(normalizeRelic(relic));
    }

    private static String normalizeRelic(String relic) {
        return relic.replace("extra1", "").replace("extra2", "");
    }

    private static List<String> getStoredRelics(Game game, Player player) {
        String stored = game.getStoredValue(KAIRN_BT_RELICS + player.getFaction());
        return stored.isBlank() ? List.of() : List.of(stored.split(","));
    }

    private static void setStoredRelics(Game game, Player player, List<String> relics) {
        game.setStoredValue(KAIRN_BT_RELICS + player.getFaction(), String.join(",", relics));
    }
}
