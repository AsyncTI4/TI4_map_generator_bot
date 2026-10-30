package ti4.ai.selfplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.ai.AiSettings;
import ti4.ai.runtime.AiRuntime;
import ti4.ai.seat.AiSeatService;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.service.draft.PlayerSetupService;
import ti4.service.draft.PlayerSetupState;
import ti4.service.game.CreateGameService;
import ti4.service.map.AddTileListService;
import ti4.service.map.MapStringMapper;
import ti4.service.testbed.TestBedPresetService;

@UtilityClass
public class SelfPlaySetup {

    public static final int MIN_SEATS = 3;
    public static final int MAX_SEATS = 6;
    private static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9]{2,19}$");
    private static final List<String> RESERVED_PREFIXES = List.of("pbd", "fow");
    private static final String CUSTOM_NAME = "AI self-play";
    private static final String UNUSED_POSITION = "-1";
    private static final int FEW_SEATS = 4;
    public static final String THUNDER_DREAMING_MAP_STRING =
            "{112} 35 30 98 34 113 101 109 47 37 44 110 49 27 48 38 46 28 43"
                    + " 0 42 100 0 40 105 0 39 111 0 114 99 0 29 115 0 26 107";

    public record Channels(TextChannel actions, TextChannel tableTalk, ThreadChannel mapUpdates) {}

    public record Table(
            List<String> factions, int humanSeat, @Nullable String humanColor, int speakerSeat) {

        private static final int NO_HUMAN = -1;

        public static Table allAi(List<String> factions) {
            return new Table(factions, NO_HUMAN, null, 0);
        }

        public static Table withHuman(
                String humanFaction, @Nullable String humanColor, List<String> aiFactions, RandomGenerator random) {
            int seats = aiFactions.size() + 1;
            int humanSeat = random.nextInt(seats);
            List<String> factions = new ArrayList<>(aiFactions);
            factions.add(humanSeat, humanFaction);
            return new Table(List.copyOf(factions), humanSeat, humanColor, random.nextInt(seats));
        }

        public boolean hasHuman() {
            return humanSeat != NO_HUMAN;
        }
    }

    public static final class SetupFailed extends RuntimeException {
        SetupFailed(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static List<String> homePositions(int seats) {
        List<String> homes = TestBedPresetService.DEFAULT_HOME_POSITIONS;
        return switch (seats) {
            case 6 -> homes;
            case 5 -> homes.subList(0, 5);
            case 4 -> List.of(homes.get(0), homes.get(1), homes.get(3), homes.get(4));
            case 3 -> List.of(homes.get(0), homes.get(2), homes.get(4));
            default -> throw new IllegalArgumentException("Self-play needs 3 to 6 seats, not " + seats);
        };
    }

    public static Map<String, String> tiles(int seats, Game game) {
        Map<String, String> tiles =
                new HashMap<>(MapStringMapper.getMappedTilesToPosition(THUNDER_DREAMING_MAP_STRING, game));
        List<String> used = homePositions(seats);
        for (String home : TestBedPresetService.DEFAULT_HOME_POSITIONS) {
            if (!used.contains(home)) tiles.put(home, UNUSED_POSITION);
        }
        return tiles;
    }

    public static int strategyCardsPerPlayer(int seats) {
        return seats <= FEW_SEATS ? 2 : 1;
    }

    public static List<String> pickFactions(int seats, RandomGenerator random) {
        List<String> factions = new ArrayList<>(AiSettings.DEFAULT_FACTIONS);
        Collections.shuffle(factions, random);
        return List.copyOf(factions.subList(0, seats));
    }

    public static List<String> pickOpponentFactions(int opponents, String humanFaction, RandomGenerator random) {
        List<String> factions = new ArrayList<>(AiSettings.DEFAULT_FACTIONS);
        factions.remove(humanFaction);
        Collections.shuffle(factions, random);
        return List.copyOf(factions.subList(0, opponents));
    }

    public static boolean isValidName(String name) {
        return name != null
                && NAME.matcher(name).matches()
                && RESERVED_PREFIXES.stream().noneMatch(name::startsWith);
    }

    public static Game setUp(
            String name,
            Member owner,
            Channels channels,
            List<String> factions,
            boolean fast,
            GenericInteractionCreateEvent event) {
        return setUp(name, owner, channels, Table.allAi(factions), fast, event);
    }

    public static Game setUp(
            String name,
            Member owner,
            Channels channels,
            Table table,
            boolean fast,
            GenericInteractionCreateEvent event) {
        List<String> factions = table.factions();
        Game game = CreateGameService.createNewGame(name, owner);
        try {
            game.setMainChannelID(channels.actions().getId());
            game.setTableTalkChannelID(channels.tableTalk().getId());
            game.setBotMapUpdatesThreadID(channels.mapUpdates().getId());
            game.setCustomName(CUSTOM_NAME);
            game.setAutoPing(false);
            game.setPlayerCountForMap(factions.size());
            game.setStrategyCardsPerPlayer(strategyCardsPerPlayer(factions.size()));
            if (fast) game.setStoredValue(AiSettings.PACE_KEY, AiSettings.FAST_PACE);
            AddTileListService.addTileMapToGame(game, tiles(factions.size(), game));
            List<String> homes = homePositions(factions.size());
            for (int seat = 0; seat < factions.size(); seat++) {
                boolean speaker = seat == table.speakerSeat();
                if (seat == table.humanSeat()) {
                    seatHuman(game, owner, factions.get(seat), table.humanColor(), homes.get(seat), speaker, event);
                    continue;
                }
                AiSeatService.AddResult added =
                        AiSeatService.addSeat(game, factions.get(seat), null, homes.get(seat), speaker, event);
                if (added.seat() == null) throw new SetupFailed(added.message(), null);
            }
            AddTileListService.finishSetup(game, null);
            AiRuntime.forget(name);
            GameManager.save(game, "AI self-play setup");
            AiRuntime.register(name);
            return game;
        } catch (SetupFailed e) {
            discard(name);
            throw e;
        } catch (Exception e) {
            discard(name);
            throw new SetupFailed("Setting up the game failed: " + e.getMessage(), e);
        }
    }

    private static void seatHuman(
            Game game,
            Member owner,
            String faction,
            @Nullable String color,
            String homePosition,
            boolean speaker,
            GenericInteractionCreateEvent event) {
        Player human = game.getPlayer(owner.getId());
        PlayerSetupService.setupPlayer(new PlayerSetupState(color, faction, homePosition, speaker), human, game, event);
        if (!human.isRealPlayer()
                || human.getHomeSystemTile() == null
                || human.getPlanets().isEmpty()) {
            throw new SetupFailed("Could not seat you as " + faction + " at " + homePosition + ".", null);
        }
    }

    private static void discard(String name) {
        AiRuntime.forget(name);
        GameManager.delete(name);
    }
}
