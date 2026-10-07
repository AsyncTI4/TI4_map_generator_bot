package ti4.service.testbed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.model.FactionModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TestBedPreset;
import ti4.model.TestBedPreset.CardPick;
import ti4.model.TestBedPreset.Seat;
import ti4.service.combat.StartCombatService;
import ti4.service.draft.PlayerSetupService;
import ti4.service.draft.PlayerSetupState;
import ti4.service.game.StartPhaseService;
import ti4.service.info.CardsInfoService;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.map.AddTileListService;
import ti4.service.map.MapStringMapper;
import ti4.service.planet.AddPlanetService;
import ti4.service.unit.AddUnitService;

@UtilityClass
public class TestBedApplyService {

    private static final Set<ComponentSource> RANDOM_FACTION_SOURCES =
            Set.of(ComponentSource.base, ComponentSource.pok);

    private record SeatPlan(Player player, Seat seat, String faction, String home, boolean speaker) {}

    public static List<String> apply(Game game, TestBedPreset preset, GenericInteractionCreateEvent event) {
        List<String> warnings = new ArrayList<>();
        if (!TestBedSnapshotService.take(game)) {
            warnings.add("No snapshot of the game before the preset was saved; `/testbed reset` will rebuild it"
                    + " instead of restoring it exactly.");
        }
        TestBedService.markAsTestBed(game, true);
        TestBedShortcuts.store(game, preset.getShortcuts());
        recordAppliedPreset(game, preset);
        placeMap(game, preset, warnings);
        List<SeatPlan> plans = planSeats(game, preset, event.getUser());
        if (game.isFowMode()) prepareFog(game, plans, event.getMember(), warnings);
        for (SeatPlan plan : plans) {
            setUpSeat(game, plan, event, warnings);
        }
        AddTileListService.finishSetup(game, event);
        Helper.setOrder(game);
        TestBedComponentService.applyGameState(game, preset, warnings);
        for (SeatPlan plan : plans) {
            if (!plan.player().isRealPlayer()) continue;
            applyHand(game, plan.player(), plan.seat(), event, warnings);
            if (!game.isFowMode() && TestBedService.isVirtualSeat(plan.player())) {
                TestBedChannelService.shareCardsInfoThread(
                        plan.player(), event.getUser().getId());
            }
            CardsInfoService.sendCardsInfo(game, plan.player());
        }
        startPhase(game, preset, plans, event, warnings);
        startCombats(game, preset, event, warnings);
        return warnings;
    }

    private static void prepareFog(Game game, List<SeatPlan> plans, @Nullable Member developer, List<String> warnings) {
        if (developer == null) {
            warnings.add("Could not resolve you as a server member; no fog private channels were created.");
            return;
        }
        if (!TestBedChannelService.grantGameMasterRole(game, developer)) {
            warnings.add("No `" + game.getName() + " GM` role exists, so you were not made GM.");
        }
        for (SeatPlan plan : plans) {
            try {
                TestBedChannelService.createFogPrivateChannel(game, plan.player(), developer);
            } catch (Exception e) {
                BotLogger.error("Test bed could not create a private channel for " + plan.faction(), e);
                warnings.add("Could not create a private channel for `" + plan.faction() + "`: " + e.getMessage());
            }
        }
    }

    private static void startCombats(
            Game game, TestBedPreset preset, GenericInteractionCreateEvent event, List<String> warnings) {
        for (String position : preset.getCombat()) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null) {
                warnings.add("No tile at `" + position + "` to start a combat in.");
                continue;
            }
            StartCombatService.combatCheck(game, event, tile);
        }
    }

    static final String APPLIED_PRESET_KEY = "testBedPreset";
    private static final String CUSTOM_PRESET = "custom";

    private static void recordAppliedPreset(Game game, TestBedPreset preset) {
        String name = preset.getName() == null || !TestBedService.isSaveSafe(preset.getName())
                ? CUSTOM_PRESET
                : preset.getName();
        TestBedService.store(game, APPLIED_PRESET_KEY, name);
    }

    public static String appliedPreset(Game game) {
        return game.getStoredValue(APPLIED_PRESET_KEY);
    }

    private static void placeMap(Game game, TestBedPreset preset, List<String> warnings) {
        String mapString =
                preset.getMapString() == null ? TestBedPresetService.DEFAULT_MAP_STRING : preset.getMapString();
        Map<String, String> tilesByPosition = MapStringMapper.getMappedTilesToPosition(mapString, game);
        preset.getTiles().forEach((position, tileId) -> tilesByPosition.put(position.toLowerCase(), tileId));
        if (tilesByPosition.isEmpty()) {
            warnings.add("Could not map the map string to positions; the map was left empty.");
            return;
        }
        try {
            List<String> badTiles = AddTileListService.addTileMapToGame(game, tilesByPosition);
            if (!badTiles.isEmpty()) warnings.add("Replaced unknown tiles with gray tiles: " + badTiles);
        } catch (Exception e) {
            warnings.add("Could not place the map: " + e.getMessage());
        }
    }

    private static List<SeatPlan> planSeats(Game game, TestBedPreset preset, User developer) {
        List<Seat> seats = preset.allSeats().stream()
                .map(seat -> TestBedPresetService.withDefaults(seat, preset.getDefaults()))
                .toList();
        List<String> randomFactions = randomFactionPool(game, seats);
        List<String> freeHomes = freeDefaultHomes(seats);
        boolean anySpeaker = seats.stream().anyMatch(Seat::isSpeaker);

        List<SeatPlan> plans = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get(i);
            boolean isDeveloperSeat = i == 0 && preset.getYou() != null;
            Player player = isDeveloperSeat ? developerPlayer(game, developer) : addVirtualSeat(game);
            String faction = seat.hasRandomFaction() ? randomFactions.removeFirst() : seat.getFaction();
            String home = seat.getHome() != null ? seat.getHome() : freeHomes.removeFirst();
            boolean speaker = seat.isSpeaker() || (!anySpeaker && i == 0);
            plans.add(new SeatPlan(player, seat, faction, home, speaker));
        }
        return plans;
    }

    private static List<String> randomFactionPool(Game game, List<Seat> seats) {
        Set<String> taken = new HashSet<>();
        seats.stream().filter(seat -> !seat.hasRandomFaction()).forEach(seat -> taken.add(seat.getFaction()));
        game.getRealPlayers().forEach(player -> taken.add(player.getFaction()));
        List<String> pool = new ArrayList<>(Mapper.getFactionsValues().stream()
                .filter(faction -> RANDOM_FACTION_SOURCES.contains(faction.getSource()))
                .filter(faction -> faction.getHomeSystem() != null
                        && !faction.getHomeSystem().isBlank())
                .map(FactionModel::getAlias)
                .filter(alias -> !alias.contains("keleres") && !taken.contains(alias))
                .toList());
        Collections.shuffle(pool);
        return pool;
    }

    private static List<String> freeDefaultHomes(List<Seat> seats) {
        List<String> homes = new ArrayList<>(TestBedPresetService.DEFAULT_HOME_POSITIONS);
        seats.forEach(seat -> homes.remove(seat.getHome()));
        return homes;
    }

    private static Player developerPlayer(Game game, User developer) {
        Player existing = game.getPlayer(developer.getId());
        return existing != null ? existing : game.addPlayer(developer.getId(), developer.getName());
    }

    private static Player addVirtualSeat(Game game) {
        for (int number = 1; ; number++) {
            String userId = TestBedService.VIRTUAL_SEAT_ID_PREFIX + String.format("%02d", number);
            if (game.getPlayer(userId) == null) return game.addPlayer(userId, "TestSeat" + number);
        }
    }

    private static void setUpSeat(
            Game game, SeatPlan plan, GenericInteractionCreateEvent event, List<String> warnings) {
        PlayerSetupState state =
                new PlayerSetupState(plan.seat().getColor(), plan.faction(), plan.home(), plan.speaker());
        try {
            PlayerSetupService.setupPlayer(state, plan.player(), game, event);
        } catch (Exception e) {
            BotLogger.error("Test bed could not set up " + plan.faction(), e);
            warnings.add("Setting up `" + plan.faction() + "` threw: " + e.getMessage());
        }
        if (!plan.player().isRealPlayer()) {
            warnings.add("`" + plan.faction() + "` was not set up; its hand and units were skipped.");
        }
    }

    static void applyHand(
            Game game, Player player, Seat seat, GenericInteractionCreateEvent event, List<String> warnings) {
        drawActionCards(game, player, seat.getAcs(), warnings);
        drawSecretObjectives(game, player, seat.getSos(), warnings);
        drawRelics(game, player, seat.getRelics(), warnings);
        if (seat.getTechs() != null) seat.getTechs().forEach(player::addTech);
        if (seat.getTg() != null) player.setTg(seat.getTg());
        if (seat.getCommodities() != null) player.setCommodities(seat.getCommodities());
        if (seat.getCcs() != null) setCommandTokens(player, seat.getCcs());
        if (seat.getLeaders() != null) applyLeaders(game, player, seat.getLeaders(), warnings);
        placeUnits(game, player, seat.getUnits(), event, warnings);
        if (seat.getPlanets() != null) {
            for (String planet : seat.getPlanets()) {
                AddPlanetService.addPlanet(
                        player, AliasHandler.resolvePlanet(planet.toLowerCase()), game, event, false);
            }
        }
        TestBedComponentService.applySeatComponents(game, player, seat, warnings);
    }

    private static boolean giveActionCard(Game game, Player player, String id) {
        if (player.getActionCards().containsKey(id)) return true;
        Integer discardNumber = game.getDiscardActionCards().get(id);
        if (discardNumber != null) return game.pickActionCard(player.getUserID(), discardNumber);
        for (Player holder : game.getRealPlayers()) {
            Integer handNumber = holder.getActionCards().get(id);
            if (handNumber == null) continue;
            holder.removeActionCard(handNumber);
            game.getActionCards().add(id);
        }
        if (!game.getActionCards().contains(id)) return false;
        game.drawSpecificActionCard(id, player.getUserID());
        return true;
    }

    private static void drawActionCards(Game game, Player player, @Nullable CardPick pick, List<String> warnings) {
        if (pick == null) return;
        for (String id : pick.ids()) {
            if (!giveActionCard(game, player, id)) {
                warnings.add(player.getFaction() + ": action card `" + id + "` is not in this game's deck.");
            }
        }
        game.drawActionCard(player.getUserID(), pick.random());
    }

    private static void drawSecretObjectives(Game game, Player player, @Nullable CardPick pick, List<String> warnings) {
        if (pick == null) return;
        for (String id : pick.ids()) {
            if (game.drawSpecificSecretObjective(id, player.getUserID()) == null) {
                warnings.add(player.getFaction() + ": secret objective `" + id + "` is not in this game's deck.");
            }
        }
        for (int i = 0; i < pick.random(); i++) {
            game.drawSecretObjective(player.getUserID());
        }
    }

    private static void drawRelics(Game game, Player player, @Nullable CardPick pick, List<String> warnings) {
        if (pick == null) return;
        for (String id : pick.ids()) {
            if (game.getAllRelics().remove(id)) {
                player.addRelic(id);
            } else {
                warnings.add(player.getFaction() + ": relic `" + id + "` is not in this game's deck.");
            }
        }
        for (int i = 0; i < pick.random(); i++) {
            String relic = game.drawRelic();
            if (!relic.isEmpty()) player.addRelic(relic);
        }
    }

    private static void setCommandTokens(Player player, String ccs) {
        String[] pools = ccs.split("/");
        player.setTacticalCC(Integer.parseInt(pools[0]));
        player.setFleetCC(Integer.parseInt(pools[1]));
        player.setStrategicCC(Integer.parseInt(pools[2]));
    }

    private static void applyLeaders(Game game, Player player, TestBedPreset.Leaders leaders, List<String> warnings) {
        for (String leaderIdOrType : leaders.getUnlock()) {
            Leader leader = findLeader(player, leaderIdOrType, warnings);
            if (leader != null) UnlockLeaderService.unlockLeader(leader.getId(), game, player);
        }
        for (String leaderIdOrType : leaders.getExhaust()) {
            Leader leader = findLeader(player, leaderIdOrType, warnings);
            if (leader != null) leader.setExhausted(true);
        }
    }

    @Nullable
    private static Leader findLeader(Player player, String leaderIdOrType, List<String> warnings) {
        Leader leader = player.unsafeGetLeader(leaderIdOrType);
        if (leader == null) warnings.add(player.getFaction() + " has no leader `" + leaderIdOrType + "`.");
        return leader;
    }

    private static void placeUnits(
            Game game,
            Player player,
            Map<String, String> unitsByLocation,
            GenericInteractionCreateEvent event,
            List<String> warnings) {
        for (Map.Entry<String, String> entry : unitsByLocation.entrySet()) {
            Tile tile = TestBedPresetService.HOME_UNIT_LOCATION.equalsIgnoreCase(entry.getKey())
                    ? player.getHomeSystemTile()
                    : game.getTileByPosition(entry.getKey());
            if (tile == null) {
                warnings.add(player.getFaction() + ": no tile at `" + entry.getKey() + "` for units.");
                continue;
            }
            AddUnitService.addUnits(event, tile, game, player.getColor(), entry.getValue());
        }
    }

    private static void startPhase(
            Game game,
            TestBedPreset preset,
            List<SeatPlan> plans,
            GenericInteractionCreateEvent event,
            List<String> warnings) {
        switch (preset.getStart()) {
            case "strategy" -> StartPhaseService.startPhase(event, game, "strategy");
            case "action" -> {
                assignStrategyCards(plans);
                StartPhaseService.startPhase(event, game, "action");
            }
            default -> {
                if (plans.stream().anyMatch(plan -> plan.seat().getSc() != null)) {
                    warnings.add("`sc` picks are only applied when `start` is `action`.");
                }
            }
        }
    }

    private static void assignStrategyCards(List<SeatPlan> plans) {
        Set<Integer> taken = new HashSet<>();
        for (SeatPlan plan : plans) {
            if (plan.seat().getSc() != null && plan.player().isRealPlayer()) {
                plan.player().addSC(plan.seat().getSc());
                taken.add(plan.seat().getSc());
            }
        }
        int nextCard = 1;
        for (SeatPlan plan : plans) {
            if (plan.seat().getSc() != null || !plan.player().isRealPlayer()) continue;
            while (taken.contains(nextCard)) nextCard++;
            plan.player().addSC(nextCard);
            taken.add(nextCard);
        }
    }
}
