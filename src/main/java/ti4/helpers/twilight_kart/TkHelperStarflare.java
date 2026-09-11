package ti4.helpers.twilight_kart;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper;
import ti4.helpers.DiceHelper.Die;
import ti4.helpers.RegexHelper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
public class TkHelperStarflare {
    public static final String AC_ID = "tk-nova-starflare";
    public static final String RESOLVE_PREFIX = "resolveTkStarflare_";

    public static List<Button> getResolveButtons(Game game, Player player) {
        String buttonId = player.factionButtonChecker() + RESOLVE_PREFIX + game.getStoredValue(AC_ID);
        return List.of(Buttons.green(buttonId, "Auto-Resolve Starflare (Immediately starts rolling)"));
    }

    private static Player getAcHolder(Game game) {
        return game.getRealPlayers().stream()
                .filter(p -> p.getPlayableActionCards().contains(AC_ID))
                .findAny()
                .orElse(null);
    }

    public static void onRetreat(Game game, Tile fromTile, Tile toTile, Map<UnitKey, List<Integer>> movedUnits) {
        Player acHolder = getAcHolder(game);
        if (acHolder == null || Stream.of(fromTile, toTile).noneMatch(tile -> tile.isAnomaly(game, acHolder))) {
            return;
        }

        respondToMove(game, toTile, List.of(movedUnits));
    }

    public static void onTacticalMove(Game game, Tile toTile) {
        Player acHolder = getAcHolder(game);
        if (acHolder == null) {
            return;
        }

        var displaced = game.getTacticalActionDisplacement().entrySet().stream();
        if (!toTile.isAnomaly(game, acHolder)) {
            displaced = displaced.filter(
                    e -> game.getTileByPosition(e.getKey().split("-")[0]).isAnomaly(game, acHolder));
        }

        respondToMove(game, toTile, displaced.map(Entry::getValue).toList());
    }

    private static void respondToMove(Game game, Tile toTile, List<Map<UnitKey, List<Integer>>> unitMaps) {
        if (unitMaps.isEmpty()) {
            return;
        }

        String movingColor = null;
        Map<String, Integer> unitAmounts = new HashMap<>();
        for (Map<UnitKey, List<Integer>> unitMap : unitMaps) {
            for (UnitKey unitKey : unitMap.keySet()) {
                if (movingColor == null) {
                    movingColor = unitKey.getColor();
                }
                String asyncID = unitKey.asyncID();
                if (!Mapper.getUnit(unitKey.unitType().plainName()).getIsShip()) {
                    continue;
                }
                int unitState = 0;
                for (int amount : unitMap.get(unitKey)) {
                    if (amount <= 0) {
                        continue;
                    }
                    String combinedKey = asyncID + unitState;
                    amount += unitAmounts.getOrDefault(combinedKey, 0);
                    unitAmounts.put(combinedKey, amount);
                    unitState++;
                }
            }
        }
        if (movingColor == null || unitAmounts.isEmpty()) {
            return;
        }

        updateValue(game, toTile, movingColor, unitAmounts);
    }

    private static void updateValue(Game game, Tile toTile, String targetColor, Map<String, Integer> unitAmounts) {
        String value = String.join(
                "_",
                toTile.getPosition(),
                targetColor,
                unitAmounts.entrySet().stream()
                        .map(e -> e.getValue() + e.getKey())
                        .collect(Collectors.joining("_")));
        game.setStoredValue(AC_ID, value);
    }

    /*
    private static void sendButtons(
            Game game, Player acHolder, String targetColor, Tile toTile, Map<String, Integer> unitAmounts) {
        boolean toAnomaly = toTile.isAnomaly(game, acHolder);
        String buttonId = acHolder.factionButtonChecker()
                + String.join(
                        "_",
                        RESOLVE_PREFIX,
                        toTile.getPosition(),
                        targetColor,
                        unitAmounts.entrySet().stream()
                                .map(e -> e.getValue() + e.getKey())
                                .collect(Collectors.joining("_")));
        int total = unitAmounts.values().stream().mapToInt(Integer::intValue).sum();
        String msg = String.format(
                "%s moved %d units %s %s (%s).",
                game.getPlayerByColorID(targetColor)
                        .map(Player::getRepresentationNoPing)
                        .orElse("Someone"),
                total,
                toAnomaly ? "into the anomaly at" : "out of one or more anomalies and into",
                toTile.getPosition(),
                toTile.getRepresentation());
        MessageHelper.sendMessageToChannelWithButtons(
                acHolder.getCardsInfoThread(),
                msg,
                List.of(Buttons.green(buttonId, "Play Starflare", CardEmojis.ActionCard)));
    }
    */

    @ButtonHandler(RESOLVE_PREFIX)
    private static void resolveTkStarflare(Game game, Player player, ButtonInteractionEvent event, String buttonID) {
        String[] buttonIdSplit = buttonID.split("_", 4);

        if (Arrays.stream(buttonIdSplit)
                        .filter(Objects::nonNull)
                        .filter(Predicate.not(String::isEmpty))
                        .count()
                != 4) {
            MessageHelper.sendMessageToChannel(
                    player.getCorrectChannel(), "Something went wrong with TK Starflare. Contact JayCyrZak.");
        }

        String position = buttonIdSplit[1];
        Tile tile = game.getTileByPosition(position);
        UnitHolder unitHolder = tile.getSpaceUnitHolder();

        String targetColor = buttonIdSplit[2];
        Player targetPlayer = game.getPlayerByColorID(targetColor).orElse(null);

        String unitAmountsString = buttonIdSplit[3];

        int totalAmount = 0;
        List<UnitAmountEntry> unitAmounts = new ArrayList<>();
        for (String entryStr : unitAmountsString.split("_")) {
            String[] entrySplit = entryStr.split(RegexHelper.DIGIT_BOUNDARY);
            int amount = Integer.parseInt(entrySplit[0]);
            UnitType type = Units.findUnitType(entrySplit[1]);
            UnitState state = UnitState.values()[Integer.parseInt(entrySplit[2])];
            unitAmounts.add(new UnitAmountEntry(type, state, amount));
            totalAmount += amount;
        }
        Collections.sort(unitAmounts);

        int totalFails = 0;
        StringBuilder sb =
                new StringBuilder("Starflare: Rolling for ").append(totalAmount).append(" ships:");
        for (UnitAmountEntry entry : unitAmounts) {
            List<Die> dice = DiceHelper.rollDice(4, entry.amount);
            int fails =
                    (int) dice.stream().filter(Predicate.not(Die::isSuccess)).count();
            totalFails += fails;

            unitHolder.removeUnit(new UnitKey(entry.type, targetColor), fails, entry.state);

            // Message building
            sb.append(String.format(
                    "\n%d %s %s", entry.amount, entry.state.humanDescr(), entry.type.humanReadableName()));
            if (entry.amount > 1) {
                sb.append("s");
            }
            sb.append(": ")
                    .append(dice.stream()
                            .map(Die::getGreenDieIfSuccessOrRedDieIfFailure)
                            .collect(Collectors.joining()))
                    .append(" ");
            if (entry.amount == 1) {
                sb.append(fails == 0 ? "It survived!" : "It was lost!");
            } else if (fails == 1) {
                sb.append("One was lost!");
            } else if (entry.amount == 2) {
                sb.append("Both ").append(fails == 0 ? "survived!" : "were lost!");
            } else if (fails == 0) {
                sb.append("They all survived!");
            } else {
                if (fails == entry.amount) {
                    sb.append("All ");
                }
                sb.append(fails).append(" were lost!");
            }
        }
        sb.append("\n -> In total, ")
                .append(totalFails)
                .append(" ships were lost and ")
                .append(totalAmount - totalFails)
                .append(" survived.");

        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), sb.toString());
        if (game.isFowMode() && targetPlayer != null) {
            MessageHelper.sendMessageToChannel(targetPlayer.getCardsInfoThread(), sb.toString());
        }

        ButtonHelper.deleteMessage(event);
    }

    private record UnitAmountEntry(UnitType type, UnitState state, int amount) implements Comparable<UnitAmountEntry> {
        @Override
        public int compareTo(@NotNull UnitAmountEntry other) {
            int result = type.ordinal() - other.type.ordinal();
            if (result != 0) {
                return result;
            }
            return other.state.ordinal() - state.ordinal();
        }
    }
}
