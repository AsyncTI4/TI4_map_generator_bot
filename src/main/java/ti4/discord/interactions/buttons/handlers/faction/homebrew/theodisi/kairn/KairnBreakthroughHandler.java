package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kairn;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
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
    private static final String USE_OWNER_RELIC = "useKairnBtOwnerRelic_";
    private static final String USE_OTHER_RELIC = "useKairnBtOtherRelic";
    private static final String SELECT_OTHER_TARGET = "selectKairnBtOtherTarget_";
    private static final String SELECT_OTHER_TARGET_RELIC = "selectKairnBtOtherTargetRelic_";
    private static final String GIVE_OTHER_RELIC = "giveKairnBtOtherRelic_";
    private static final String READY_BREAKTHROUGH = "readyKairnBt";

    public static void refreshRelics(Game game, Player player) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough("kairnbt")) {
            return;
        }
        for (String relic : getStoredRelics(game, player)) {
            game.shuffleRelicBack(relic);
        }
        List<String> relics = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String relic = game.drawRelic();
            if (relic.isEmpty()) {
                break;
            }
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

    public static Button getOtherPlayerRelicButton(Player player) {
        return Buttons.gray(
                player.factionButtonChecker() + USE_OTHER_RELIC,
                "Use Relic Trading Hub on Another Player",
                ExploreEmojis.Relic);
    }

    public static boolean offerRelicGainInterrupt(GenericInteractionCreateEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasReadyBreakthrough("kairnbt")) {
            return false;
        }
        List<Button> buttons = new ArrayList<>();
        for (String relic : getStoredRelics(game, player)) {
            RelicModel model = Mapper.getRelic(relic.replace("extra1", "").replace("extra2", ""));
            if (model != null) {
                buttons.add(Buttons.green(
                        player.factionButtonChecker() + USE_OWNER_RELIC + relic,
                        "Gain " + model.getName(),
                        ExploreEmojis.Relic));
            }
        }
        if (buttons.isEmpty()) {
            return false;
        }
        buttons.add(Buttons.red(
                player.factionButtonChecker() + "drawRelicIgnoringKairnBt",
                "Gain a Relic Normally",
                ExploreEmojis.Relic));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", choose how to resolve your relic gain.",
                buttons);
        return true;
    }

    public static void offerReadyAfterRelicDraw(Game game, Player player) {
        if (game == null || player == null) {
            return;
        }
        for (Player owner : game.getRealPlayers()) {
            if (!owner.hasUnlockedBreakthrough("kairnbt")) {
                continue;
            }
            if (owner.hasReadyBreakthrough("kairnbt")
                    && owner != player
                    && !getStoredRelics(game, owner).isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        owner.getCardsInfoThread(),
                        owner.getRepresentation() + ", " + player.getRepresentationNoPing()
                                + " drew a relic. You may use **Relic Trading Hub** to give them a relic from it instead.");
                continue;
            }
            if (!owner.isBreakthroughExhausted("kairnbt")) {
                continue;
            }
            MessageHelper.sendMessageToChannelWithButtons(
                    owner.getCardsInfoThread(),
                    owner.getRepresentation() + ", " + player.getRepresentationNoPing()
                            + " drew a relic. You may ready **Relic Trading Hub**.",
                    List.of(Buttons.green(
                            owner.factionButtonChecker() + READY_BREAKTHROUGH,
                            "Ready Relic Trading Hub",
                            ExploreEmojis.Relic)));
        }
    }

    @ButtonHandler(VIEW_RELICS)
    public static void viewRelics(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasUnlockedBreakthrough("kairnbt")) {
            return;
        }
        List<MessageEmbed> embeds = getStoredRelics(game, player).stream()
                .map(relic -> Mapper.getRelic(relic.replace("extra1", "").replace("extra2", "")))
                .filter(java.util.Objects::nonNull)
                .map(RelicModel::getRepresentationEmbed)
                .toList();
        MessageHelper.sendMessageToChannelWithEmbeds(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + " has these relics on **Relic Trading Hub**.",
                embeds);
    }

    @ButtonHandler(USE_OWNER_RELIC)
    public static void useOwnerRelic(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String relic = buttonID.substring(USE_OWNER_RELIC.length());
        if (game == null
                || player == null
                || !player.hasReadyBreakthrough("kairnbt")
                || !getStoredRelics(game, player).contains(relic)) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setBreakthroughExhausted("kairnbt", true);
        List<String> relics = new ArrayList<>(getStoredRelics(game, player));
        relics.remove(relic);
        setStoredRelics(game, player, relics);
        String relicID = relic.replace("extra1", "").replace("extra2", "");
        player.addRelic(relicID);
        RelicModel model = Mapper.getRelic(relicID);
        if (model != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    player.getCorrectChannel(),
                    player.getRepresentationNoPing() + " gained _" + model.getName() + "_ from **Relic Trading Hub**.",
                    model.getRepresentationEmbed(false, true));
        }
        RelicHelper.resolveRelicEffects(event, game, player, relicID);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(USE_OTHER_RELIC)
    public static void useOtherRelic(ButtonInteractionEvent event, Game game, Player player) {
        if (game == null || player == null || !player.hasReadyBreakthrough("kairnbt")) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        List<Button> buttons = game.getRealPlayers().stream()
                .filter(target -> target != player && !target.getRelics().isEmpty())
                .map(target -> Buttons.green(
                        player.factionButtonChecker() + SELECT_OTHER_TARGET + target.getFaction(),
                        target.getColor(),
                        target.getFactionEmojiOrColor()))
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", choose a player whose relic you will return to the relic deck.",
                buttons);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(SELECT_OTHER_TARGET)
    public static void selectOtherTarget(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        Player target = game == null
                ? null
                : game.getPlayerFromColorOrFaction(buttonID.substring(SELECT_OTHER_TARGET.length()));
        if (target == null || player == null || !player.hasReadyBreakthrough("kairnbt") || target == player) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = target.getRelics().stream()
                .map(relic -> Mapper.getRelic(relic.replace("extra1", "").replace("extra2", "")) == null
                        ? null
                        : Buttons.green(
                                player.factionButtonChecker() + SELECT_OTHER_TARGET_RELIC + target.getFaction() + "|"
                                        + relic,
                                Mapper.getRelic(relic.replace("extra1", "").replace("extra2", ""))
                                        .getName(),
                                ExploreEmojis.Relic))
                .filter(java.util.Objects::nonNull)
                .toList();
        if (buttons.isEmpty()) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationNoPing() + ", choose the relic to return to the relic deck.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(SELECT_OTHER_TARGET_RELIC)
    public static void selectOtherTargetRelic(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(SELECT_OTHER_TARGET_RELIC.length()).split("\\|", 2);
        Player target = values.length == 2 && game != null ? game.getPlayerFromColorOrFaction(values[0]) : null;
        if (target == null
                || player == null
                || !player.hasReadyBreakthrough("kairnbt")
                || !target.getRelics().contains(values.length == 2 ? values[1] : "")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        List<Button> buttons = getStoredRelics(game, player).stream()
                .map(relic -> Mapper.getRelic(relic.replace("extra1", "").replace("extra2", "")) == null
                        ? null
                        : Buttons.green(
                                player.factionButtonChecker() + GIVE_OTHER_RELIC + target.getFaction() + "|" + values[1]
                                        + "|" + relic,
                                "Give "
                                        + Mapper.getRelic(relic.replace("extra1", "")
                                                        .replace("extra2", ""))
                                                .getName(),
                                ExploreEmojis.Relic))
                .filter(java.util.Objects::nonNull)
                .toList();
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

    @ButtonHandler(GIVE_OTHER_RELIC)
    public static void giveOtherRelic(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String[] values = buttonID.substring(GIVE_OTHER_RELIC.length()).split("\\|", 3);
        Player target = values.length == 3 && game != null ? game.getPlayerFromColorOrFaction(values[0]) : null;
        if (target == null
                || player == null
                || !player.hasReadyBreakthrough("kairnbt")
                || !target.getRelics().contains(values.length == 3 ? values[1] : "")
                || !getStoredRelics(game, player).contains(values.length == 3 ? values[2] : "")) {
            ButtonHelper.deleteMessage(event);
            return;
        }
        player.setBreakthroughExhausted("kairnbt", true);
        target.removeRelic(values[1]);
        game.shuffleRelicBack(values[1]);
        List<String> relics = new ArrayList<>(getStoredRelics(game, player));
        relics.remove(values[2]);
        setStoredRelics(game, player, relics);
        String relicID = values[2].replace("extra1", "").replace("extra2", "");
        target.addRelic(relicID);
        RelicModel model = Mapper.getRelic(relicID);
        if (model != null) {
            MessageHelper.sendMessageToChannelWithEmbed(
                    target.getCorrectChannel(),
                    target.getRepresentationNoPing() + " gained _" + model.getName() + "_ from **Relic Trading Hub**."
                            + "\nMake sure to resolve/fix any \"On Gain\" effects that may have occurred from the previously drawn relic.",
                    model.getRepresentationEmbed(false, true));
        }
        RelicHelper.resolveRelicEffects(event, game, target, relicID);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(READY_BREAKTHROUGH)
    public static void readyBreakthrough(ButtonInteractionEvent event, Player player) {
        if (player != null && player.hasUnlockedBreakthrough("kairnbt") && player.isBreakthroughExhausted("kairnbt")) {
            player.setBreakthroughExhausted("kairnbt", false);
        }
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentationNoPing() + " readied _Relic Trading Hub_.");
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("drawRelicIgnoringKairnBt")
    public static void drawRelicIgnoringKairnBreakthrough(ButtonInteractionEvent event, Game game, Player player) {
        RelicHelper.drawRelicAndNotifyIgnoringKairnBreakthrough(player, event, game);
        ButtonHelper.deleteMessage(event);
    }

    private static List<String> getStoredRelics(Game game, Player player) {
        String stored = game.getStoredValue(KAIRN_BT_RELICS + player.getFaction());
        return stored.isBlank() ? List.of() : List.of(stored.split(","));
    }

    private static void setStoredRelics(Game game, Player player, List<String> relics) {
        game.setStoredValue(KAIRN_BT_RELICS + player.getFaction(), String.join(",", relics));
    }
}
