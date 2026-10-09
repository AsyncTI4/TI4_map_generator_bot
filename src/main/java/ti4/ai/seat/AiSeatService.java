package ti4.ai.seat;

import java.security.SecureRandom;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.ai.AiSeats;
import ti4.ai.AiSettings;
import ti4.ai.profile.AiProfile;
import ti4.ai.runtime.AiRuntime;
import ti4.game.Game;
import ti4.game.Player;
import ti4.image.PositionMapper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.draft.PlayerSetupService;
import ti4.service.draft.PlayerSetupState;
import ti4.settings.users.UserSettings;
import ti4.settings.users.UserSettingsManager;

@UtilityClass
public class AiSeatService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> TRADE_PREFERENCES_CHECKED = ConcurrentHashMap.newKeySet();

    public record AddResult(@Nullable Player seat, String message) {}

    public static AddResult addSeat(
            Game game,
            @Nullable String requestedFaction,
            @Nullable String color,
            String homeSystemPosition,
            boolean speaker,
            GenericInteractionCreateEvent event) {
        String faction = requestedFaction != null ? requestedFaction : AiGameSupport.firstFreeFaction(game);
        if (faction == null) return new AddResult(null, "Every faction the AI can play is already taken.");
        String refusal = AiGameSupport.refusalReason(game, faction);
        if (refusal != null) return new AddResult(null, refusal);
        if (!PositionMapper.isTilePositionValid(homeSystemPosition)) {
            return new AddResult(null, "`" + homeSystemPosition + "` is not a valid tile position.");
        }
        if (occupiedHome(game, homeSystemPosition)) {
            return new AddResult(null, "Position `" + homeSystemPosition + "` is already a player's home system.");
        }

        boolean firstSeat = !AiSeats.hasAiSeat(game);
        String seatId = AiSeats.newSeatId(game);
        Player seat = game.addPlayer(seatId, AiSettings.seatName(faction));
        AiSetupRestore.remember(game, seat, homeSystemPosition);
        try {
            PlayerSetupService.setupPlayer(
                    new PlayerSetupState(color, faction, homeSystemPosition, speaker), seat, game, event);
        } catch (RuntimeException e) {
            BotLogger.error("Could not set up the AI seat in " + game.getName(), e);
        }
        if (!seat.isRealPlayer()
                || seat.getHomeSystemTile() == null
                || seat.getPlanets().isEmpty()) {
            AiSetupRestore.restore(game, seat);
            game.removePlayer(seatId);
            return new AddResult(null, "The AI seat could not be set up at `" + homeSystemPosition + "`.");
        }

        writeSeatPreferences(seatId);
        AiProfile.createHidden(AiSettings.brainFor(faction), RANDOM).writeTo(seat);
        game.setNewTransactionMethod(true);
        game.setHomebrew(true);
        AiRuntime.register(game.getName());
        announce(game, seat, firstSeat);
        return new AddResult(seat, "Added " + seat.getRepresentationNoPing() + " as an AI seat.");
    }

    public static List<Player> findSeats(Game game, @Nullable String factionOrColor) {
        List<Player> seats = AiSeats.aiSeats(game);
        if (StringUtils.isBlank(factionOrColor)) return seats;
        Player named = game.getPlayerFromColorOrFaction(factionOrColor);
        return seats.stream().filter(seat -> seat == named).toList();
    }

    @Nullable
    public static String removeSeat(Game game, Player seat) {
        if (!AiSeats.isAiSeat(seat)) return seat.getRepresentationNoPing() + " is not an AI seat.";
        if (AiGameSupport.hasStarted(game)) {
            return "The game has started, so AI seats can't be removed. Use `/game replace` to hand one to a"
                    + " human player instead.";
        }
        AiSetupRestore.restore(game, seat);
        game.removePlayer(seat.getUserID());
        if (!AiSeats.hasAiSeat(game)) AiRuntime.forget(game.getName());
        return null;
    }

    public static void setPaused(Player seat, boolean paused) {
        AiProfile profile = AiProfile.of(seat);
        profile.withPause(paused ? AiProfile.PauseState.MANUAL : AiProfile.PauseState.RUNNING)
                .writeTo(seat);
    }

    private static boolean occupiedHome(Game game, String position) {
        return game.getRealPlayers().stream()
                .map(Player::getHomeSystemTile)
                .anyMatch(home -> home != null && position.equals(home.getPosition()));
    }

    public static void ensureTradePreferences(String seatId) {
        if (!TRADE_PREFERENCES_CHECKED.add(seatId)) return;
        UserSettings settings = UserSettingsManager.get(seatId);
        if (!settings.isPrefersAutoDebtClearance()) return;
        settings.setPrefersAutoDebtClearance(false);
        UserSettingsManager.save(settings);
    }

    static void writeSeatPreferences(String seatId) {
        UserSettings settings = UserSettingsManager.get(seatId);
        settings.setSandbagPref("bot");
        settings.setPrefersDistanceBasedTacticalActions(true);
        settings.setPrefersWrongButtonEphemeral(true);
        settings.setHasAnsweredSurvey(true);
        settings.setHasIndicatedStatPreferences(true);
        settings.setActivityTracking(false);
        settings.setPrefersAutoDebtClearance(false);
        UserSettingsManager.save(settings);
    }

    private static void announce(Game game, Player seat, boolean firstSeat) {
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(), "🤖 " + seat.getRepresentationNoPing() + " has joined as an **AI player**.");
        if (!firstSeat || game.getTableTalkChannel() == null) return;
        game.getTableTalkChannel()
                .sendMessage(howToDealWithTheAi())
                .queue(
                        message -> message.pin().queue(Consumers.nop(), BotLogger::catchRestError),
                        BotLogger::catchRestError);
    }

    static String howToDealWithTheAi() {
        return """
                ## 🤖 How to deal with the AI players
                - They play their own turns. Give them a moment after one becomes active.
                - **Trades.** They answer offers quickly: they accept deals worth about as much to them as to you, \
                counter-offer small differences, and wash commodities 1:1 with anyone not about to win. They pay extra \
                for trade goods they need to score, and never feed a player who could win with the help. They can't \
                judge action cards, relics or "Specify Deal" text, so they counter without them.
                - **Trade card.** An AI that plays Trade posts its terms. Pressing "Replenish Commodities" accepts \
                them, and it sends you the deal.
                - **Debt.** They record IOUs as debt, pay their own as soon as they can trade with you (commodities \
                first), and offer less to players who don't pay.
                - When one doesn't know what to do, it posts its options and **any player** may choose for it.
                - They never read chat, and they keep the promises they make in accepted deals.""";
    }
}
