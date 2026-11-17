package ti4.helpers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.lunarium.LunariumAbilityHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.lunarium.LunariumBreakthroughHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;
import ti4.service.info.SecretObjectiveInfoService;

public final class QueuedSecretDraws {

    public sealed interface Outcome permits Draw, Blocker {
        Player player();
    }

    public record Draw(Player player, boolean plausibleDeniability) implements Outcome {}

    public record Blocker(Player player) implements Outcome {}

    private final Game game;
    private final List<Outcome> outcomes = new ArrayList<>();

    QueuedSecretDraws(Game game) {
        this.game = game;
    }

    void addDraw(Player player, boolean plausibleDeniability) {
        outcomes.add(new Draw(player, plausibleDeniability));
    }

    void addBlocker(Player player) {
        outcomes.add(new Blocker(player));
    }

    public List<Outcome> outcomes() {
        return Collections.unmodifiableList(outcomes);
    }

    public boolean hasDraws() {
        return outcomes.stream().anyMatch(Draw.class::isInstance);
    }

    public void announce() {
        for (Outcome outcome : outcomes) {
            switch (outcome) {
                case Draw draw -> announceDraw(draw);
                case Blocker blocker -> announceBlocker(blocker.player());
            }
        }
    }

    private void announceDraw(Draw draw) {
        Player player = draw.player();
        String message = player.getRepresentationNoPing() + " drew their queued secret objective from **Imperial**. ";
        if (draw.plausibleDeniability()) {
            message += " Drew a second secret objective due to **Plausible Deniability**.";
        }
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(), player.getRepresentation() + " you drew your queued secret.");
        if (player.hasAbility("multitasking")) {
            LunariumAbilityHandler.offerFactionSheetCCButtons(game, player);
        }
        if (player.hasUnlockedBreakthrough("lunariumbt")) {
            LunariumBreakthroughHandler.offerDarkSideExploitationButtons(game, player);
        }
        SecretObjectiveInfoService.sendSecretObjectiveInfo(game, player);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        if (!game.isFowMode()) {
            ButtonHelper.sendMessageToRightStratThread(player, game, message, "imperial");
        }
    }

    private void announceBlocker(Player player) {
        if (game.isFowMode()) {
            return;
        }
        String message = player.getRepresentationUnfogged()
                + " is the one the game is currently waiting on before advancing to the next player, with regards to queued **Imperial** follows.";
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
        ButtonHelper.sendMessageToRightStratThread(player, game, message, "imperial");
    }
}
