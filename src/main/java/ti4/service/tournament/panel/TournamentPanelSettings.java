package ti4.service.tournament.panel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.section.Section;
import net.dv8tion.jda.api.components.selections.SelectOption;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.textdisplay.TextDisplay;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import ti4.discord.JdaService;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.discord.interactions.routing.ModalHandler;
import ti4.discord.interactions.routing.SelectionHandler;
import ti4.message.componentsV2.ComponentPanel;
import ti4.service.fow.CreateFoWGameService;
import ti4.service.game.CreateGameService;
import ti4.service.tournament.TournamentAccess;
import ti4.service.tournament.TournamentDiscord;
import ti4.service.tournament.TournamentGameNames;
import ti4.service.tournament.TournamentRoles;
import ti4.service.tournament.TournamentSignupPosts;
import ti4.service.tournament.panel.TournamentPanel.Context;
import ti4.service.tournament.panel.TournamentPanel.Tab;
import ti4.service.tournament.panel.TournamentPanel.View;
import ti4.service.tournament.setup.TournamentSetupGame;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;
import ti4.spring.service.tournament.TournamentStatus;

final class TournamentPanelSettings {

    private static final String BASICS_MODAL = "trnBasics~MDL_";
    private static final String BASICS_SAVE = "trnBasicsSave_";
    private static final String FOG = "trnFog_";
    private static final String PER_CATEGORY = "trnPerCat_";
    private static final String HOURS = "trnHours_";
    private static final String SERVER = "trnServer_";
    private static final String NAMES_MODAL = "trnNames~MDL_";
    private static final String SPREAD = "trnSpread_";
    private static final String REMATCH = "trnRematch_";
    private static final String NAMES_SAVE = "trnNamesSave_";
    private static final int MIN_GROUP_SIZE = 3;
    private static final int MAX_GROUP_SIZE = 8;

    private TournamentPanelSettings() {}

    static View view(Context context) {
        Tournament tournament = context.tournament();
        long id = context.id();
        boolean editable = tournament.getStatus().allowsRosterChanges();
        List<ContainerChildComponent> body = new ArrayList<>();
        body.add(Section.of(
                Buttons.blue(BASICS_MODAL + id, "Edit").withDisabled(!editable),
                TextDisplay.of("**" + tournament.getSeriesName() + "** · " + tournament.getRoundLabel()
                        + "\n-# Target " + tournament.getTargetSize() + " players · " + tournament.getGroupSize()
                        + " players per game")));
        body.add(ComponentPanel.toggleSection(
                "Fog of War",
                "Games are created as Fog of War games with a GM each. Fixed once groups are approved.",
                tournament.isFogMode(),
                ComponentPanel.toggleButton(FOG + id, tournament.isFogMode())
                        .withDisabled(!editable || tournament.getStatus() == TournamentStatus.GROUPED)));
        if (!tournament.isFogMode()) {
            int other = tournament.getGamesPerCategory() == 5 ? 10 : 5;
            body.add(Section.of(
                    Buttons.gray(PER_CATEGORY + id, "Use " + other).withDisabled(!editable),
                    TextDisplay.of("**" + tournament.getGamesPerCategory()
                            + " games per category**\n-# Categories are named like *"
                            + tournament.getSeriesName() + " " + tournament.getRoundLabel() + " 1-"
                            + tournament.getGamesPerCategory() + "*.")));
        }
        body.add(Section.of(
                Buttons.gray(NAMES_MODAL + id, "Change").withDisabled(!editable || tournament.isFogMode()),
                TextDisplay.of("**Game names:** " + TournamentGameNames.example(tournament) + "\n-# "
                        + (tournament.isFogMode()
                                ? "Fog of War games always use fow numbers."
                                : "Custom names show the tournament in every channel name. Bot cleanup tools"
                                        + " only look at pbd names."))));
        body.add(ComponentPanel.toggleSection(
                "Match active hours",
                "Random groups that prefer players whose active hours overlap. Off means purely random.",
                tournament.isMatchActiveHours(),
                ComponentPanel.toggleButton(HOURS + id, tournament.isMatchActiveHours())));
        if (tournament.getPreviousRoundId() != null) {
            body.add(ComponentPanel.toggleSection(
                    "Spread previous winners",
                    "Winners of the previous round are drawn into different groups.",
                    tournament.isSpreadPreviousWinners(),
                    ComponentPanel.toggleButton(SPREAD + id, tournament.isSpreadPreviousWinners())));
            body.add(ComponentPanel.toggleSection(
                    "Avoid rematches",
                    "Players who shared a game in the previous round are drawn into different groups when possible.",
                    tournament.isAvoidRematches(),
                    ComponentPanel.toggleButton(REMATCH + id, tournament.isAvoidRematches())));
        }
        Guild host = TournamentDiscord.hostGuild(tournament);
        body.add(TextDisplay.of("**Host server:** "
                + (host == null ? "not set" : host.getName() + " · " + capacity(tournament, host))));
        List<ActionRow> controls = editable ? List.of(ActionRow.of(serverSelect(tournament, host))) : List.of();
        return View.of(body, controls).withWarning(host == null);
    }

    private static String capacity(Tournament tournament, Guild guild) {
        if (tournament.isFogMode()) {
            return CreateFoWGameService.serverCanHostNewGame(guild, tournament.getGroupSize() + 1)
                    ? "has room for fog games"
                    : "full";
        }
        return "room for ~" + Math.max(0, CreateGameService.getServerCapacityForNewGames(guild)) + " games";
    }

    private static StringSelectMenu serverSelect(Tournament tournament, Guild host) {
        List<SelectOption> options = new ArrayList<>();
        JdaService.guilds.stream()
                .sorted(Comparator.comparing(Guild::getName))
                .limit(25)
                .forEach(guild -> options.add(SelectOption.of(StringUtils.left(guild.getName(), 100), guild.getId())
                        .withDescription(StringUtils.left(capacity(tournament, guild), 100))
                        .withDefault(guild.equals(host))));
        return StringSelectMenu.create(SERVER + tournament.getId())
                .setPlaceholder("Pick the server the games are created on")
                .addOptions(options)
                .setRequiredRange(1, 1)
                .build();
    }

    @ButtonHandler(value = BASICS_MODAL, save = false)
    static void openBasics(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, BASICS_MODAL);
        Tournament tournament = TournamentService.getBean().find(id).orElse(null);
        if (tournament == null) return;
        TournamentPanel.openModal(
                event,
                BASICS_SAVE + id,
                "Tournament basics",
                TournamentPanel.field("Tournament name", "series", tournament.getSeriesName(), true),
                TournamentPanel.field("Round", "round", tournament.getRoundLabel(), true),
                TournamentPanel.field("Target players", "target", String.valueOf(tournament.getTargetSize()), true),
                TournamentPanel.field(
                        "Players per game (" + MIN_GROUP_SIZE + "-" + MAX_GROUP_SIZE + ")",
                        "groupSize",
                        String.valueOf(tournament.getGroupSize()),
                        true));
    }

    @ModalHandler(value = BASICS_SAVE, save = false)
    static void saveBasics(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), BASICS_SAVE);
        String series = StringUtils.left(TournamentPanel.value(event, "series"), 60);
        String round = StringUtils.left(TournamentPanel.value(event, "round"), 40);
        int target = NumberUtils.toInt(TournamentPanel.value(event, "target"), -1);
        int groupSize = NumberUtils.toInt(TournamentPanel.value(event, "groupSize"), -1);
        if (series.isBlank()
                || round.isBlank()
                || target < 1
                || groupSize < MIN_GROUP_SIZE
                || groupSize > MAX_GROUP_SIZE) {
            TournamentPanel.show(
                    event,
                    id,
                    Tab.SETTINGS,
                    "Name and round can't be empty, target must be a positive number and players per game must be "
                            + MIN_GROUP_SIZE + "-" + MAX_GROUP_SIZE + ".");
            return;
        }
        Tournament saved = TournamentService.getBean()
                .update(id, pool -> {
                    pool.setSeriesName(series);
                    pool.setRoundLabel(round);
                    pool.setTargetSize(target);
                    pool.setGroupSize(groupSize);
                })
                .orElseThrow();
        TournamentSignupPosts.refreshNow(id);
        String seats = TournamentSetupGame.resizeSeats(saved);
        List<TournamentEntry> entries = TournamentService.getBean().entries(id);
        int seated = TournamentService.activeCount(entries);
        boolean seatsOpen = saved.getStatus().allowsRosterChanges() && target > seated && entries.size() > seated;
        String notice = "Saved." + (seats == null ? "" : " " + seats)
                + (seatsOpen ? " Players are waiting for the new seats: press **Fill to target** on Players." : "");
        TournamentPanel.show(event, id, Tab.SETTINGS, notice);
    }

    @ButtonHandler(value = FOG, save = false)
    static void toggleFog(ButtonInteractionEvent event, String buttonID) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(buttonID, FOG);
        TournamentService.getBean().update(id, pool -> {
            if (pool.getStatus() == TournamentStatus.OPEN || pool.getStatus() == TournamentStatus.CLOSED) {
                pool.setFogMode(!pool.isFogMode());
            }
        });
        TournamentSignupPosts.refreshNow(id);
        TournamentPanel.show(event, id, Tab.SETTINGS, null);
    }

    @ButtonHandler(value = PER_CATEGORY, save = false)
    static void togglePerCategory(ButtonInteractionEvent event, String buttonID) {
        TournamentPanel.change(
                event,
                TournamentPanel.idOf(buttonID, PER_CATEGORY),
                Tab.SETTINGS,
                pool -> pool.setGamesPerCategory(pool.getGamesPerCategory() == 5 ? 10 : 5),
                null);
    }

    @ButtonHandler(value = HOURS, save = false)
    static void toggleHours(ButtonInteractionEvent event, String buttonID) {
        TournamentPanel.change(
                event,
                TournamentPanel.idOf(buttonID, HOURS),
                Tab.SETTINGS,
                pool -> pool.setMatchActiveHours(!pool.isMatchActiveHours()),
                null);
    }

    @ButtonHandler(value = SPREAD, save = false)
    static void toggleSpread(ButtonInteractionEvent event, String buttonID) {
        TournamentPanel.change(
                event,
                TournamentPanel.idOf(buttonID, SPREAD),
                Tab.SETTINGS,
                pool -> pool.setSpreadPreviousWinners(!pool.isSpreadPreviousWinners()),
                null);
    }

    @ButtonHandler(value = REMATCH, save = false)
    static void toggleRematches(ButtonInteractionEvent event, String buttonID) {
        TournamentPanel.change(
                event,
                TournamentPanel.idOf(buttonID, REMATCH),
                Tab.SETTINGS,
                pool -> pool.setAvoidRematches(!pool.isAvoidRematches()),
                null);
    }

    @ButtonHandler(value = NAMES_MODAL, save = false)
    static void openNames(ButtonInteractionEvent event, String buttonID) {
        long id = TournamentPanel.idOf(buttonID, NAMES_MODAL);
        Tournament tournament = TournamentService.getBean().find(id).orElse(null);
        String current = tournament == null ? null : tournament.getGameNamePrefix();
        TournamentPanel.openModal(
                event,
                NAMES_SAVE + id,
                "Game names",
                TextDisplay.of("Games are named with this prefix plus the group number, for example t8p01."
                        + " Leave it empty for normal pbd numbers."),
                TournamentPanel.field("Name prefix", "prefix", current, false));
    }

    @ModalHandler(value = NAMES_SAVE, save = false)
    static void saveNames(ModalInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getModalId(), NAMES_SAVE);
        String prefix = TournamentGameNames.normalize(TournamentPanel.value(event, "prefix"));
        if (!prefix.isEmpty()) {
            String problem = TournamentGameNames.problem(prefix).orElse(null);
            if (problem != null) {
                TournamentPanel.show(event, id, Tab.SETTINGS, problem);
                return;
            }
        }
        TournamentService.getBean().update(id, pool -> pool.setGameNamePrefix(prefix.isEmpty() ? null : prefix));
        TournamentPanel.show(
                event,
                id,
                Tab.SETTINGS,
                prefix.isEmpty()
                        ? "Games will get pbd numbers."
                        : "Games will be named " + prefix + "01, " + prefix + "02, …");
    }

    @SelectionHandler(value = SERVER, save = false)
    static void pickServer(StringSelectInteractionEvent event) {
        if (!TournamentAccess.require(event)) return;
        long id = TournamentPanel.idOf(event.getComponentId(), SERVER);
        TournamentService service = TournamentService.getBean();
        Tournament before = service.find(id).orElse(null);
        String guildId = event.getValues().getFirst();
        if (before == null || guildId.equals(before.getHostGuildId())) {
            TournamentPanel.show(event, id, Tab.SETTINGS, null);
            return;
        }
        if (!before.getStatus().allowsRosterChanges()) {
            TournamentPanel.show(event, id, Tab.SETTINGS, "The host server can't change once games are being created.");
            return;
        }
        TournamentRoles.deleteIfUnused(before);
        Tournament moved = TournamentRoles.ensureRoleSaved(service.update(id, pool -> {
                    pool.setHostGuildId(guildId);
                    pool.setParticipantRoleId(null);
                })
                .orElseThrow());
        int given = TournamentRoles.sync(moved, service.entries(id));
        Guild guild = TournamentDiscord.hostGuild(moved);
        TournamentPanel.show(
                event,
                id,
                Tab.SETTINGS,
                "Games will be created on **" + (guild == null ? guildId : guild.getName())
                        + "**. Gave the participant role to " + given + " player(s) already on that server.");
    }
}
