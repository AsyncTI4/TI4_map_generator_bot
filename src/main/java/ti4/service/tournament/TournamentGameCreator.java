package ti4.service.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import org.apache.commons.lang3.StringUtils;
import ti4.executors.CircuitBreaker;
import ti4.executors.ExecutionLockManager;
import ti4.executors.ExecutionLockType;
import ti4.executors.ExecutorServiceManager;
import ti4.game.Game;
import ti4.game.persistence.GameManager;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.fow.CreateFoWGameService;
import ti4.service.game.CreateGameService;
import ti4.service.tournament.panel.TournamentPanel;
import ti4.service.tournament.setup.TournamentSetupApplier;
import ti4.service.tournament.setup.TournamentSetupGame;
import ti4.spring.service.statistics.matchmaking.queue.MatchmakerService;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;
import ti4.spring.service.tournament.TournamentStatus;

@UtilityClass
public class TournamentGameCreator {

    private static final Set<Long> RUNNING = ConcurrentHashMap.newKeySet();

    public record Preflight(
            int groupCount,
            int pendingGames,
            Map<Integer, List<String>> missingFromHostByGroup,
            List<Integer> groupsWithoutGameMaster,
            List<String> takenNames,
            String capacityProblem) {

        public boolean hasMissingPlayers() {
            return !missingFromHostByGroup.isEmpty();
        }
    }

    private record Step(String gameName, String skipped, String failure) {

        static Step created(String gameName) {
            return new Step(gameName, null, null);
        }

        static Step skip(String reason) {
            return new Step(null, reason, null);
        }

        static Step fail(String reason) {
            return new Step(null, null, reason);
        }
    }

    public static boolean isRunning(long tournamentId) {
        return RUNNING.contains(tournamentId);
    }

    public static Preflight preflight(Tournament tournament, List<TournamentEntry> entries) {
        Map<Integer, List<TournamentEntry>> groups = TournamentService.groups(entries);
        Guild guild = TournamentDiscord.hostGuild(tournament);
        Map<Integer, String> gameMasters = TournamentService.gameMastersByGroup(tournament);
        Map<Integer, List<String>> missing = new TreeMap<>();
        List<Integer> withoutGameMaster = new ArrayList<>();
        List<String> takenNames = new ArrayList<>();
        boolean customNames = TournamentGameNames.usesCustomNames(tournament);
        int pending = 0;
        for (Map.Entry<Integer, List<TournamentEntry>> group : groups.entrySet()) {
            if (group.getValue().getFirst().getGameName() != null) continue;
            pending++;
            if (customNames) {
                String name = TournamentGameNames.customName(tournament, group.getKey());
                if (TournamentGameNames.isTaken(name)) takenNames.add(name);
            }
            List<String> absent = new ArrayList<>();
            for (TournamentEntry entry : group.getValue()) {
                if (TournamentDiscord.member(guild, entry.getUserId()) == null) absent.add(entry.getUserId());
            }
            String gm = gameMasters.get(group.getKey());
            if (tournament.isFogMode()) {
                if (gm == null) withoutGameMaster.add(group.getKey());
                else if (TournamentDiscord.member(guild, gm) == null) absent.add(gm);
            }
            if (!absent.isEmpty()) missing.put(group.getKey(), absent);
        }
        return new Preflight(
                groups.size(),
                pending,
                missing,
                withoutGameMaster,
                takenNames,
                capacityProblem(tournament, guild, pending));
    }

    private static String capacityProblem(Tournament tournament, Guild guild, int pendingGames) {
        if (guild == null) return "The host server can't be found. Pick another one in Settings.";
        if (pendingGames == 0 || tournament.isFogMode()) return null;
        int capacity = CreateGameService.getServerCapacityForNewGames(guild);
        if (capacity >= pendingGames) return null;
        return "**" + guild.getName() + "** only has room for about " + capacity + " more games, but " + pendingGames
                + " are needed. Pick another server in Settings.";
    }

    public static String start(Tournament tournament) {
        if (!CreateGameService.isGameCreationAllowed()) {
            return "Admins have temporarily turned off game creation.";
        }
        if (CircuitBreaker.isOpen()) return "The bot is pausing work after repeated errors. Try again later.";
        if (RUNNING.contains(tournament.getId())) return "Games are already being created.";
        long id = tournament.getId();
        ExecutorServiceManager.runAsync("Tournament game creation " + id, 3600, () -> {
            if (!RUNNING.add(id)) return;
            try {
                run(id);
            } finally {
                RUNNING.remove(id);
            }
        });
        return null;
    }

    private static void run(long id) {
        TournamentService service = TournamentService.getBean();
        Tournament tournament = service.find(id).orElse(null);
        if (tournament == null) return;
        Guild guild = TournamentDiscord.hostGuild(tournament);
        GuildMessageChannel reportChannel = TournamentDiscord.channel(tournament.getPanelChannelId());
        if (guild == null || reportChannel == null) {
            TournamentPanel.refresh(id, "The host server or the panel channel can't be found.");
            return;
        }
        service.update(id, pool -> pool.setStatus(TournamentStatus.CREATING));
        TournamentSignupPosts.refreshNow(id);

        List<String> created = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        String failure = null;
        try {
            Map<Integer, List<TournamentEntry>> groups = TournamentService.groups(service.entries(id));
            for (Map.Entry<Integer, List<TournamentEntry>> group : groups.entrySet()) {
                if (group.getValue().getFirst().getGameName() != null) continue;
                long stepStart = System.currentTimeMillis();
                Step step =
                        createOne(tournament, guild, reportChannel, group.getKey(), group.getValue(), groups.size());
                if (step.failure() != null) {
                    failure = "Group " + group.getKey() + ": " + step.failure();
                    break;
                }
                if (step.skipped() != null) {
                    skipped.add("Group " + group.getKey() + ": " + step.skipped());
                    continue;
                }
                service.markGameCreated(id, group.getKey(), step.gameName());
                created.add(step.gameName());
                TournamentPanel.refresh(
                        id, "Creating games… " + created.size() + " done, latest **" + step.gameName() + "**.");
                if (!TournamentPacing.waitAfter(System.currentTimeMillis() - stepStart)) {
                    failure = "Paused because the bot is under load.";
                    break;
                }
            }
        } catch (Exception e) {
            BotLogger.error("Tournament game creation failed for tournament " + id, e);
            failure = "Unexpected error: " + e.getMessage();
        } finally {
            finish(id, created, skipped, failure, reportChannel);
        }
    }

    private static void finish(
            long id, List<String> created, List<String> skipped, String failure, GuildMessageChannel reportChannel) {
        TournamentService service = TournamentService.getBean();
        List<TournamentEntry> entries = service.entries(id);
        boolean allCreated = TournamentService.groups(entries).values().stream()
                .allMatch(members -> members.getFirst().getGameName() != null);
        Tournament tournament = service.update(
                        id, pool -> pool.setStatus(allCreated ? TournamentStatus.CREATED : TournamentStatus.GROUPED))
                .orElse(null);
        List<String> lines = new ArrayList<>();
        lines.add("Created " + created.size() + " game(s)"
                + (created.isEmpty() ? "." : ": " + String.join(", ", created)));
        if (!skipped.isEmpty()) lines.add("Skipped:\n" + String.join("\n", skipped));
        if (failure != null) lines.add("Stopped: " + failure + "\nPress Create again to continue.");
        if (allCreated) lines.add("All games are created.");
        TournamentPanel.refresh(id, String.join("\n", lines));
        TournamentSignupPosts.refreshNow(id);
        if (tournament != null && !created.isEmpty()) TournamentRoster.post(tournament, entries, reportChannel);
    }

    private static Step createOne(
            Tournament tournament,
            Guild guild,
            GuildMessageChannel reportChannel,
            int group,
            List<TournamentEntry> members,
            int groupCount) {
        String funName =
                tournament.getSeriesName() + " " + tournament.getRoundLabel() + " " + String.format("%02d", group);
        Step step = tournament.isFogMode()
                ? createFog(tournament, guild, reportChannel, group, members, funName)
                : createNormal(tournament, guild, reportChannel, group, members, groupCount, funName);
        if (step.gameName() != null) {
            finishGame(members);
        }
        return step;
    }

    private static Step createNormal(
            Tournament tournament,
            Guild guild,
            GuildMessageChannel reportChannel,
            int group,
            List<TournamentEntry> entries,
            int groupCount,
            String funName) {
        List<Member> members = new ArrayList<>();
        for (TournamentEntry entry : entries) {
            Member member = TournamentDiscord.anyMember(guild, entry.getUserId());
            if (member == null) {
                return Step.skip(
                        TournamentDiscord.userName(entry.getUserId()) + " isn't on any server the bot can see.");
            }
            members.add(member);
        }
        boolean customName = TournamentGameNames.usesCustomNames(tournament);
        String gameName =
                customName ? TournamentGameNames.customName(tournament, group) : CreateGameService.getNextPbdGameName();
        if (customName && TournamentGameNames.isTaken(gameName)) {
            return Step.skip("the name **" + gameName + "** is already used by a game or role.");
        }
        Category category = category(tournament, guild, group, groupCount);
        Game source =
                TournamentSetupApplier.hasSource(tournament) ? TournamentSetupApplier.loadSource(tournament) : null;
        boolean copySetup = TournamentSetupApplier.canCopy(source);
        Game game;
        try {
            game = CreateGameService.createGameChannels(
                    members, reportChannel, funName, gameName, members.getFirst(), category, !copySetup);
        } catch (Exception e) {
            BotLogger.error("Could not create tournament game " + gameName, e);
            game = null;
        }
        if (game == null) {
            resetPbdNumber(gameName);
            return Step.fail("Could not create **" + gameName + "**. Check the server and category capacity.");
        }
        Game created = game;
        List<String> setupProblems = new ArrayList<>();
        ExecutionLockManager.wrapWithLockAndRelease(gameName, ExecutionLockType.WRITE, () -> {
                    if (copySetup) setupProblems.addAll(TournamentSetupApplier.applyToNewGame(source, created));
                    TournamentSetupOptions.applyAll(created, TournamentService.gameSetup(tournament));
                    GameManager.save(created, "Tournament game created");
                })
                .run();
        if (!setupProblems.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    reportChannel, "Setup notes for **" + gameName + "**:\n" + String.join("\n", setupProblems));
        }
        return Step.created(gameName);
    }

    private static Step createFog(
            Tournament tournament,
            Guild guild,
            GuildMessageChannel reportChannel,
            int group,
            List<TournamentEntry> entries,
            String funName) {
        List<Member> members = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (TournamentEntry entry : entries) {
            Member member = TournamentDiscord.member(guild, entry.getUserId());
            if (member == null) missing.add(TournamentDiscord.userName(entry.getUserId()));
            else members.add(member);
        }
        String gameMasterId = TournamentService.gameMastersByGroup(tournament).get(group);
        if (gameMasterId == null) return Step.skip("no GM assigned.");
        Member gameMaster = TournamentDiscord.member(guild, gameMasterId);
        if (gameMaster == null) missing.add(TournamentDiscord.userName(gameMasterId) + " (GM)");
        if (!missing.isEmpty()) {
            return Step.skip("not on **" + guild.getName() + "** yet: " + String.join(", ", missing));
        }
        if (!CreateFoWGameService.serverCanHostNewGame(guild, members.size() + 1)) {
            return Step.fail("**" + guild.getName() + "** is full.");
        }
        String gameName = CreateFoWGameService.getNextFOWGameName();
        CreateFoWGameService.executeCreateFoWGame(guild, gameName, funName, gameMaster, members, reportChannel);
        applySetup(tournament, gameName);
        return Step.created(gameName);
    }

    private static void finishGame(List<TournamentEntry> members) {
        for (TournamentEntry member : members) {
            MatchmakerService.get().leaveQueue(member.getUserId());
        }
    }

    public static boolean hasAnySetup(Tournament tournament) {
        return TournamentSetupApplier.hasSource(tournament)
                || !TournamentSetupOptions.all().isEmpty();
    }

    public static String applySetup(Tournament tournament, String gameName) {
        if (!hasAnySetup(tournament)) return null;
        if (!GameManager.isValid(gameName)) return gameName + " no longer exists.";
        Game source =
                TournamentSetupApplier.hasSource(tournament) ? TournamentSetupApplier.loadSource(tournament) : null;
        boolean copySetup = TournamentSetupApplier.canCopy(source);
        Map<String, String> values = TournamentService.gameSetup(tournament);
        List<String> problems = new ArrayList<>();
        ExecutionLockManager.wrapWithLockAndRelease(gameName, ExecutionLockType.WRITE, () -> {
                    Game game = GameManager.getManagedGame(gameName).getGame();
                    if (copySetup && TournamentSetupGame.dealtOrDrafted(game)) {
                        problems.add(gameName + " skipped: its draft or dealing has started.");
                        return;
                    }
                    if (copySetup) problems.addAll(TournamentSetupApplier.applyToNewGame(source, game));
                    TournamentSetupOptions.applyAll(game, values);
                    GameManager.save(game, "Tournament game setup");
                })
                .run();
        return problems.isEmpty() ? null : gameName + ": " + String.join(" ", problems);
    }

    private static Category category(Tournament tournament, Guild guild, int group, int groupCount) {
        int perCategory = Math.max(1, tournament.getGamesPerCategory());
        int from = ((group - 1) / perCategory) * perCategory + 1;
        int to = Math.min(from + perCategory - 1, groupCount);
        String name = StringUtils.left(
                tournament.getSeriesName() + " " + tournament.getRoundLabel() + " " + from + "-" + to, 100);
        return guild.getCategoriesByName(name, true).stream()
                .findFirst()
                .orElseGet(() -> CreateGameService.createGameCategory(guild, name));
    }

    private static void resetPbdNumber(String gameName) {
        String number = gameName.startsWith("pbd") ? gameName.substring(3) : "";
        if (StringUtils.isNumeric(number)) GameManager.resetLatestPbdNumberFrom(Integer.parseInt(number));
    }
}
