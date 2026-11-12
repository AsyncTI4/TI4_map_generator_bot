package ti4.discord.interactions.buttons.handlers.faction.homebrew.wftv.kalora;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.StatusHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.Mapper;
import ti4.service.emoji.FactionEmojis;

@UtilityClass
public class KaloraAbilityHandler {

    public static boolean eusocialityRetreat(Player player, GenericInteractionCreateEvent event, Tile activeTile) {
        if (CommandCounterHelper.hasCC(event, player.getColor(), activeTile)) return false;
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji()
                        + " did not place a command token in system they retreated to due to **Eusociality**.");
        return true;
    }

    public static void carapaceRegeneration(Player player, Tile destTile, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder space = destTile.getSpaceUnitHolder();
        for (UnitType unitType : UnitType.values()) {
            if (unitType == UnitType.Mech) continue;
            int damaged = space.getDamagedUnitCount(unitType, colorID);
            if (damaged > 0) space.removeDamagedUnit(Units.getUnitKey(unitType, colorID), damaged);
        }
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji()
                        + " repaired all damaged ships that retreated due to **Carapace Regeneration**.");
    }

    /**
     * Call once for every player who scores a public objective. Outside the status phase (e.g. via Imperial) the
     * bot already gates whose turn is active, so click order and initiative order already agree and this resolves
     * immediately. During the status phase, every player's scoring buttons go out at once
     * ({@code StatusHelper#beginScoring}), so click order doesn't reflect real initiative order; scoring is instead
     * recorded here and resolved once for the whole round by {@code resolveStatusPhaseScoring}.
     */
    public static void onPublicObjectiveScored(Game game, Player player, String objectiveId) {
        if (StatusHelper.isStatusScoring(game)) {
            String key = statusScorersKey(game);
            game.setStoredValue(key, game.getStoredValue(key) + objectiveId + "#" + player.getUserID() + ";");
            return;
        }
        if (player.hasAbility("primordial")) {
            Player sharedTreasureHolder = findSharedTreasureHolder(game, player);
            boolean isFirst = isFirstIgnoringPair(game, objectiveId, player, sharedTreasureHolder);
            grantFirstToScoreBonus(player, game, objectiveId, isFirst, KaloraCard.PRIMORDIAL);
        }
        if (player.getPromissoryNotesInPlayArea().contains("bapnkalo")) {
            Player kalora = game.getPlayerFromColorOrFaction("kalora");
            boolean isFirst = isFirstIgnoringPair(game, objectiveId, player, kalora);
            grantFirstToScoreBonus(player, game, objectiveId, isFirst, KaloraCard.SHARED_TREASURE);
        }
    }

    private static String statusScorersKey(Game game) {
        return "kaloraStatusScorersRound" + game.getRound();
    }

    /**
     * Resolves every public objective scored during this round's status-phase scoring, once scoring for the round
     * is over (called from {@code StatusHelper#commitStatusScoringEvent}) so every scorer is known before deciding
     * who was actually first by initiative.
     */
    public static void resolveStatusPhaseScoring(Game game) {
        String key = statusScorersKey(game);
        String recorded = game.getStoredValue(key);
        if (recorded.isEmpty()) return;
        game.removeStoredValue(key);

        Map<String, List<Player>> scorersThisRoundByObjective = new LinkedHashMap<>();
        for (String entry : recorded.split(";")) {
            if (entry.isBlank()) continue;
            String[] parts = entry.split("#", 2);
            if (parts.length < 2) continue;
            String objectiveId = parts[0];
            String userId = parts[1];
            game.getRealPlayers().stream()
                    .filter(p -> p.getUserID().equals(userId))
                    .findFirst()
                    .ifPresent(p -> scorersThisRoundByObjective
                            .computeIfAbsent(objectiveId, k -> new ArrayList<>())
                            .add(p));
        }
        if (scorersThisRoundByObjective.isEmpty()) return;

        Player kalora = game.getPlayerFromColorOrFaction("kalora");
        Player sharedTreasureHolder = findSharedTreasureHolder(game, kalora);
        if (kalora == null && sharedTreasureHolder == null) return;

        for (Map.Entry<String, List<Player>> entry : scorersThisRoundByObjective.entrySet()) {
            String objectiveId = entry.getKey();
            List<Player> scorersThisRound = entry.getValue();

            // Someone outside this round's status scoring already has this objective (an earlier round, or this
            // same round via e.g. Imperial during the action phase, which always resolves before status scoring) —
            // that scorer unambiguously came first, so nobody new can be first for it this round.
            Set<String> scorerUserIdsThisRound =
                    scorersThisRound.stream().map(Player::getUserID).collect(Collectors.toSet());
            List<String> lifetimeScorerUserIds =
                    game.getScoredPublicObjectives().getOrDefault(objectiveId, List.of());
            boolean hasScorerOutsideThisRound =
                    lifetimeScorerUserIds.stream().anyMatch(uid -> !scorerUserIdsThisRound.contains(uid));
            if (hasScorerOutsideThisRound) continue;

            int outsiderMinInitiative = scorersThisRound.stream()
                    .filter(p -> p != kalora && p != sharedTreasureHolder)
                    .mapToInt(Player::getInitiative)
                    .min()
                    .orElse(Integer.MAX_VALUE);

            if (kalora != null && scorersThisRound.contains(kalora) && kalora.hasAbility("primordial")) {
                grantFirstToScoreBonus(
                        kalora,
                        game,
                        objectiveId,
                        kalora.getInitiative() <= outsiderMinInitiative,
                        KaloraCard.PRIMORDIAL);
            }
            if (sharedTreasureHolder != null && scorersThisRound.contains(sharedTreasureHolder)) {
                grantFirstToScoreBonus(
                        sharedTreasureHolder,
                        game,
                        objectiveId,
                        sharedTreasureHolder.getInitiative() <= outsiderMinInitiative,
                        KaloraCard.SHARED_TREASURE);
            }
        }
    }

    private static Player findSharedTreasureHolder(Game game, Player kalora) {
        for (Player p : game.getRealPlayers()) {
            if (!Objects.equals(p, kalora) && p.getPromissoryNotesInPlayArea().contains("bapnkalo")) {
                return p;
            }
        }
        return null;
    }

    /** Whether {@code self} is first to score {@code objectiveId}, ignoring any scoring by {@code partner}. */
    private static boolean isFirstIgnoringPair(Game game, String objectiveId, Player self, Player partner) {
        List<String> scorers = game.getScoredPublicObjectives().getOrDefault(objectiveId, List.of());
        String partnerUserId = partner == null ? null : partner.getUserID();
        return scorers.stream().allMatch(uid -> uid.equals(self.getUserID()) || uid.equals(partnerUserId));
    }

    private enum KaloraCard {
        PRIMORDIAL("**Primordial**", ""),
        SHARED_TREASURE(FactionEmojis.kalora + " **Shared Treasure**", " (Kalora's score doesn't count against you)");

        final String display;
        final String extraContext;

        KaloraCard(String display, String extraContext) {
            this.display = display;
            this.extraContext = extraContext;
        }
    }

    private static void grantFirstToScoreBonus(
            Player player, Game game, String objectiveId, boolean isFirst, KaloraCard card) {
        if (!isFirst) return;
        String poName = Mapper.getPublicObjective(objectiveId).getName();
        int tgAmount = game.getRealPlayers().size() - 1;
        int before = player.getTg();
        player.setTg(before + tgAmount);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + " gained " + tgAmount + " trade good"
                        + (tgAmount == 1 ? "" : "s") + " (" + before + " -> " + player.getTg() + ") for being first to"
                        + " score _" + poName + "_" + card.extraContext + " via " + card.display
                        + ". This is optional but was done automatically.");
    }

    public static void chitinShielding(Player player, Player opponent, Game game) {
        Button resolve = Buttons.green(
                player.factionButtonChecker() + "kaloraChitinShielding_" + opponent.getFaction(),
                "Resolve Chitin Shielding");
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged()
                        + ", if you win this space combat, you may exhaust _Chitin Shielding_ to place 1 of "
                        + opponent.getFactionEmoji() + "'s command tokens into the active system.",
                List.of(resolve));
    }

    @ButtonHandler("kaloraChitinShielding_")
    public static void resolveChitinShielding(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String opponentFaction = buttonID.replace("kaloraChitinShielding_", "");
        Player opponent = game.getPlayerFromColorOrFaction(opponentFaction);
        if (opponent == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    "Could not find that player. Please resolve _Chitin Shielding_ manually.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        Tile activeTile = game.getTileByPosition(game.getActiveSystem());
        player.exhaustTech("bakalor");
        CommandCounterHelper.addCC(event, opponent, activeTile);
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getFactionEmoji() + " exhausted _Chitin Shielding_ to place " + opponent.getFactionEmoji()
                        + "'s command token in the active system.");
        ButtonHelper.deleteButtonAndDeleteMessageIfEmpty(event);
    }
}
