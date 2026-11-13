package ti4.discord.interactions.buttons.handlers.agenda;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.AgendaRiderHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.Mapper;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.fow.GMService;
import ti4.service.info.RelicInfoService;
import ti4.service.megagame.MegagameGmAccess;
import ti4.service.megagame.MegagameGroupService;

@UtilityClass
public class VoiceOfArvidHandler {

    public static final String RELIC_ID = "megagame_voiceofarvid";
    private static final String RELIC_NAME = "_The Voice of Arvid (megagame)_";
    private static final String STATE_KEY = "voiceOfArvid";
    private static final int AGENDAS_TO_LOOK_AT = 5;

    private static final String PLAY = "voiceOfArvidPlay";
    private static final String CHOOSE = "voiceOfArvidChoose_";
    private static final String PLANET_OWNER = "voiceOfArvidOwner_";
    private static final String OUTCOME = "voiceOfArvidOutcome";
    private static final String RESOLVE = "voiceOfArvidResolve_";

    private static final Set<String> MANUAL_AGENDA_IDS = Set.of("miscount", "absol_miscount");

    public static Button playButton() {
        return Buttons.green(PLAY, "Play The Voice of Arvid (megagame)", ExploreEmojis.Relic);
    }

    public static boolean blocksAgendaFlip(Game game, ButtonInteractionEvent event) {
        if (ArvidPlay.current(game) == null) return false;
        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                "Waiting for " + RELIC_NAME + " to be resolved before the agenda is flipped."
                        + " A GM may override this with `/agenda reveal`.");
        return true;
    }

    @ButtonHandler(PLAY)
    public static void play(ButtonInteractionEvent event, Game game, Player player) {
        String refusal = playRefusal(game, player);
        if (refusal != null) {
            MessageHelper.sendMessageToEventChannel(event, refusal);
            return;
        }
        List<String> agendaIds = agendasToLookAt(game);
        new ArvidPlay(game.getRound(), player.getFaction(), agendaIds, "").write(game);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                "**" + FoWHelper.actorOrAnon(game, player, "A player") + " plays " + RELIC_NAME
                        + " and will have the top " + agendaIds.size()
                        + " agenda cards shown privately. Please be patient.**");
        String extraDrawn = agendaIds.size() > AGENDAS_TO_LOOK_AT
                ? " None of the top " + AGENDAS_TO_LOOK_AT + " had a valid target, so more were drawn until one did."
                : "";
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", these are the top " + agendaIds.size() + " agendas."
                        + extraDrawn
                        + " Choose 1 with a valid target to resolve with your vote; all the others are discarded.",
                agendaIds.stream()
                        .map(id -> Mapper.getAgenda(id).getRepresentationEmbed())
                        .toList(),
                agendaIds.stream().map(id -> chooseButton(game, id)).toList());
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(CHOOSE)
    public static void choose(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (refuseDuringAgendaVote(event, game)) return;
        ArvidPlay play = ArvidPlay.current(game);
        String chosen = buttonID.substring(CHOOSE.length());
        if (play == null || play.hasChosen()) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "This " + RELIC_NAME + " choice is no longer open. If the agenda deck changed after you looked at"
                            + " it, the play was cancelled and the relic was kept.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (!play.isOwnedBy(player)) {
            MessageHelper.sendMessageToEventChannel(event, "Only the player who played " + RELIC_NAME + " can choose.");
            return;
        }
        if (!play.agendaIds().contains(chosen) || !hasValidTarget(game, chosen)) {
            MessageHelper.sendMessageToEventChannel(event, "That agenda cannot be chosen with " + RELIC_NAME + ".");
            return;
        }
        play.agendaIds().forEach(game::discardSpecificAgenda);
        announceDiscards(game, player, play.agendaIds(), chosen);
        ButtonHelper.deleteMessage(event);

        game.setCurrentAgendaInfo(AgendaHelper.agendaInfoFor(game, chosen));
        game.resetCurrentAgendaVotes();
        List<Button> outcomeButtons = outcomeButtons(game, player);
        if (needsManualResolution(chosen) || outcomeButtons.isEmpty()) {
            handOverToGm(game, player, chosen);
            return;
        }
        play.withChosen(chosen).write(game);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.getRepresentationUnfogged() + ", choose the outcome your 1 vote goes to for _"
                        + agendaName(chosen) + "_. It resolves immediately afterwards.",
                outcomeButtons);
    }

    @ButtonHandler(PLANET_OWNER)
    public static void choosePlanetOwner(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (refuseDuringAgendaVote(event, game)) return;
        Player owner = game.getPlayerFromColorOrFaction(buttonID.substring(PLANET_OWNER.length()));
        if (!isOpenFor(game, player) || owner == null) {
            MessageHelper.sendMessageToEventChannel(event, "This " + RELIC_NAME + " choice is no longer open.");
            return;
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Choose the planet of " + owner.getFactionEmojiOrColor() + " your vote goes to.",
                VoteButtonHandler.getPlanetOutcomeButtons(owner, game, OUTCOME, null));
    }

    @ButtonHandler(OUTCOME + "_")
    public static void chooseOutcome(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (refuseDuringAgendaVote(event, game)) return;
        ArvidPlay play = ArvidPlay.current(game);
        if (play == null || !play.isOwnedBy(player) || !play.hasChosen()) {
            MessageHelper.sendMessageToEventChannel(event, "This " + RELIC_NAME + " choice is no longer open.");
            return;
        }
        String winner = buttonID.substring(OUTCOME.length() + 1);
        String outcomeName = AgendaHelper.getAgendaOutcomeName(game, winner, true);
        AgendaModel agenda = Mapper.getAgenda(play.chosen());
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                game.getMainGameChannel(),
                "## " + FoWHelper.actorOrAnon(game, player, "A player") + " resolves _" + agenda.getName()
                        + "_ with " + RELIC_NAME + "\n1 vote for **" + outcomeName + "**.\n"
                        + "It is resolved immediately: no whens or afters, no _Confounding Disclosure_ and no"
                        + " _Committee Formation_.",
                List.of(agenda.getRepresentationEmbed()),
                List.of(Buttons.green(player.factionButtonChecker() + RESOLVE + winner, "Resolve for " + outcomeName)));
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(RESOLVE)
    public static void resolve(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (refuseDuringAgendaVote(event, game)) return;
        ArvidPlay play = ArvidPlay.current(game);
        if (play == null || !play.isOwnedBy(player) || !play.hasChosen()) {
            MessageHelper.sendMessageToEventChannel(event, "This " + RELIC_NAME + " has already been resolved.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        String winner = buttonID.substring(RESOLVE.length());
        game.setCurrentAgendaInfo(AgendaHelper.agendaInfoFor(game, play.chosen()));
        game.resetCurrentAgendaVotes();
        game.setCurrentAgendaVote(winner, (game.isFowMode() ? player.getColor() : player.getFaction()) + "_1");
        game.removeStoredValue(STATE_KEY);
        purge(player);
        AgendaResolveButtonHandler.resolveAgenda(game, "agendaResolution_" + winner, event);
        game.resetCurrentAgendaVotes();
    }

    private static String playRefusal(Game game, Player player) {
        if (!player.hasRelic(RELIC_ID)) return "You no longer hold " + RELIC_NAME + ".";
        if (!isOutsideAgendaVote(game)) {
            return RELIC_NAME + " can only be prepared during the action phase.";
        }
        if (ArvidPlay.current(game) != null) return RELIC_NAME + " is already being resolved.";
        if (game.getAgendas().stream().noneMatch(id -> hasValidTarget(game, id))) {
            return "No agenda left in the agenda deck has a valid target.";
        }
        return null;
    }

    private static boolean isOutsideAgendaVote(Game game) {
        return "action".equalsIgnoreCase(game.getPhaseOfGame())
                && game.getStoredValue("executiveOrder").isEmpty();
    }

    private static boolean refuseDuringAgendaVote(ButtonInteractionEvent event, Game game) {
        if (isOutsideAgendaVote(game)) return false;
        MessageHelper.sendMessageToEventChannel(
                event,
                RELIC_NAME + " can only be resolved during the action phase, while no agenda is being voted on."
                        + " Try again once the vote is over.");
        return true;
    }

    private static boolean isOpenFor(Game game, Player player) {
        ArvidPlay play = ArvidPlay.current(game);
        return play != null && play.isOwnedBy(player) && play.hasChosen();
    }

    static List<String> agendasToLookAt(Game game) {
        List<String> deck = game.getAgendas();
        int count = Math.min(AGENDAS_TO_LOOK_AT, deck.size());
        while (count < deck.size() && deck.subList(0, count).stream().noneMatch(id -> hasValidTarget(game, id))) {
            count++;
        }
        return new ArrayList<>(deck.subList(0, count));
    }

    private static Button chooseButton(Game game, String agendaId) {
        if (hasValidTarget(game, agendaId)) {
            return Buttons.green(CHOOSE + agendaId, "Choose " + agendaName(agendaId));
        }
        return Buttons.gray(CHOOSE + agendaId, agendaName(agendaId) + " (no valid target)")
                .asDisabled();
    }

    private static List<Button> outcomeButtons(Game game, Player player) {
        String target = game.getCurrentAgendaInfo().split("_")[1];
        String lower = target.toLowerCase();
        if (target.contains("For") || lower.contains("player") || !lower.contains("planet")) {
            return AgendaRiderHelper.getAgendaButtons(null, game, OUTCOME);
        }
        if (game.isFowMode()) {
            return VoteButtonHandler.fogPlanetOutcomeButtons(game, player, OUTCOME);
        }
        return AgendaRiderHelper.getPlayerOutcomeButtons(game, null, StringUtils.removeEnd(PLANET_OWNER, "_"), null);
    }

    static boolean hasValidTarget(Game game, String agendaId) {
        AgendaModel agenda = Mapper.getAgenda(agendaId);
        if (agenda == null) return false;
        String target = StringUtils.defaultString(agenda.getTarget());
        return !AgendaHelper.isElectLawWithoutLaws(game, agendaId, target)
                && !AgendaHelper.isElectSecretWithoutScoredSecrets(game, target)
                && !"emergency session".equalsIgnoreCase(agenda.getName());
    }

    static boolean needsManualResolution(String agendaId) {
        return MANUAL_AGENDA_IDS.contains(agendaId.toLowerCase())
                || "covert legislation"
                        .equalsIgnoreCase(Mapper.getAgenda(agendaId).getName());
    }

    private static void announceDiscards(Game game, Player player, List<String> agendaIds, String chosen) {
        List<String> discarded = agendaIds.stream()
                .filter(id -> !id.equals(chosen))
                .map(id -> "_" + agendaName(id) + "_")
                .toList();
        String names = discarded.isEmpty() ? "nothing" : String.join(", ", discarded);
        MessageHelper.sendMessageToChannel(
                game.getMainGameChannel(),
                FoWHelper.actorOrAnon(game, player, "A player") + " chose 1 agenda with " + RELIC_NAME
                        + " and discarded: " + names + ".");
    }

    private static void handOverToGm(Game game, Player player, String agendaId) {
        game.removeStoredValue(STATE_KEY);
        purge(player);
        AgendaModel agenda = Mapper.getAgenda(agendaId);
        MessageHelper.sendMessageToChannelWithEmbed(
                game.getMainGameChannel(),
                gmPing(game) + " " + FoWHelper.actorOrAnon(game, player, "A player") + " chose _" + agenda.getName()
                        + "_ with " + RELIC_NAME + ". The bot cannot resolve this agenda with a single vote and no"
                        + " windows, so please resolve it by hand. The agenda deck is unlocked again.",
                agenda.getRepresentationEmbed());
    }

    private static String gmPing(Game game) {
        if (game.isFowMode()) return GMService.gmPing(game);
        if (MegagameGroupService.hasTag(game)) return MegagameGmAccess.gmMention(game.getGuild());
        return game.getPing();
    }

    private static void purge(Player player) {
        player.removeRelic(RELIC_ID);
        player.removeExhaustedRelic(RELIC_ID);
        MessageHelper.sendMessageToChannel(
                player.getCorrectChannel(), player.getRepresentation() + " purged " + RELIC_NAME + ".");
        RelicInfoService.sendRelicInfo(player);
    }

    private static String agendaName(String agendaId) {
        AgendaModel agenda = Mapper.getAgenda(agendaId);
        return agenda == null ? agendaId : agenda.getName();
    }

    private record ArvidPlay(int round, String faction, List<String> agendaIds, String chosen) {

        static ArvidPlay current(Game game) {
            ArvidPlay play = read(game);
            if (play == null) return null;
            boolean stale = play.round() != game.getRound()
                    || (!play.hasChosen() && !game.getAgendas().containsAll(play.agendaIds()));
            if (!stale) return play;
            game.removeStoredValue(STATE_KEY);
            return null;
        }

        private static ArvidPlay read(Game game) {
            String[] parts = game.getStoredValue(STATE_KEY).split("\\|", -1);
            if (parts.length != 4 || !StringUtils.isNumeric(parts[0])) return null;
            return new ArvidPlay(Integer.parseInt(parts[0]), parts[1], Arrays.asList(parts[2].split(";")), parts[3]);
        }

        void write(Game game) {
            game.setStoredValue(STATE_KEY, round + "|" + faction + "|" + String.join(";", agendaIds) + "|" + chosen);
        }

        ArvidPlay withChosen(String agendaId) {
            return new ArvidPlay(round, faction, agendaIds, agendaId);
        }

        boolean isOwnedBy(Player player) {
            return player != null && faction.equals(player.getFaction());
        }

        boolean hasChosen() {
            return !chosen.isEmpty();
        }
    }
}
