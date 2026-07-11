package ti4.helpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.Consumers;
import ti4.cron.AutoPingCron;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.commands.planet.PlanetExhaustAbility;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Leader;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.message.GameMessageType;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.service.agenda.IsPlayerElectedService;
import ti4.service.button.ReactionCheckService;
import ti4.service.button.ReactionService;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.MiscEmojis;
import ti4.service.fow.GMService;

public final class AgendaWhensAftersHelper {

    private AgendaWhensAftersHelper() {}

    static void offerEveryonePrepassOnShenanigans(Game game) {
        if (game.islandMode()) return;
        for (Player player : game.getRealPlayers()) {
            if (playerDoesNotHaveShenanigans(player)) {
                String part2 = player.getFaction();
                if (!game.getStoredValue("Pass On Shenanigans").isEmpty()) {
                    part2 = game.getStoredValue("Pass On Shenanigans") + "_" + player.getFaction();
                }
                game.setStoredValue("Pass On Shenanigans", part2);
                continue;
            }
            String msg = player.toString() + " you have the option to pre-pass on agenda shenanigans here."
                    + " Agenda shenanigans are the action cards _Bribery_, _Confusing Legal Text_, _Confounding Legal Text_, and _Deadly Plot_."
                    + " Feel free not to pre-pass, this is simply an optional way to resolve agendas faster.";
            List<Button> buttons = new ArrayList<>();

            buttons.add(Buttons.green("resolvePreassignment_Pass On Shenanigans", "Pre-Pass"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        }
    }

    static void eraseAgendaQueues(GenericInteractionCreateEvent event, Game game) {
        game.setStoredValue("queuedAgendasMode", "Yes");
        game.removeStoredValue("aftersResolved");
        game.removeStoredValue("whensResolved");
        game.removeStoredValue("lastPlayerToPlayAnAfter");
        game.removeStoredValue("lastPlayerToPlayAWhen");
        game.removeStoredValue("declinedWhens");
        game.removeStoredValue("declinedAfters");
        game.removeStoredValue("queuedWhens");
        game.removeStoredValue("queuedAfters");
        game.removeStoredValue("CommFormPreset");
        for (Player player : game.getRealPlayers()) {
            game.removeStoredValue("queuedWhensFor" + player.getFaction());
            game.removeStoredValue("queuedAftersFor" + player.getFaction());
            game.removeStoredValue("queuedAftersLockedFor" + player.getFaction());
            if ((!game.getStoredValue("passOnAllWhensNAfters" + player.getFaction())
                                    .isEmpty()
                            || player.isNpc())
                    && !"action".equalsIgnoreCase(game.getPhaseOfGame())) {
                game.setStoredValue("declinedWhens", game.getStoredValue("declinedWhens") + player.getFaction() + "_");
                List<Button> buttons = new ArrayList<>();
                buttons.add(Buttons.red("queueAWhen", "Play A When"));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        "You have declined to queue a \"when\". You can change your mind with this button.",
                        buttons);
                game.setStoredValue(
                        "declinedAfters", game.getStoredValue("declinedAfters") + player.getFaction() + "_");
                game.setStoredValue("queuedAftersLockedFor" + player.getFaction(), "Yes");
                buttons = new ArrayList<>();
                buttons.add(Buttons.red("queueAnAfter", "Play An \"After\""));
                MessageHelper.sendMessageToChannelWithButtons(
                        player.getCardsInfoThread(),
                        "You have declined to queue an \"after\". You can change your mind with this button.",
                        buttons);
                offerPreVote(player);
            }
        }
        if (event instanceof ButtonInteractionEvent bevent) {
            resolveWhenQueue(bevent, game);
        }
    }

    public static void offerPlayerPassOnWhensNAfters(Player player) {
        List<String> whens = getPossibleWhenNames(player);
        List<String> afters = getPossibleAfterNames(player);
        StringBuilder msg = new StringBuilder(
                player.toString()
                        + " if you wish, to speed up the Agenda Phase, you can choose now to secretly pass on all \"when\"s and \"after\"s for both agendas in the upcoming Agenda Phase. "
                        + "You may currently play " + StringHelper.pluralize(whens.size(), "\"when\"")
                        + " and " + StringHelper.pluralize(afters.size(), "\"after\"")
                        + ". You will be able to change your mind during the agendas themselves if something unexpected occurs.");
        if (!whens.isEmpty()) {
            msg.append("\nThe possible \"when\"")
                    .append(whens.size() == 1 ? "" : "s")
                    .append(" you may play ")
                    .append(whens.size() == 1 ? "is" : "are")
                    .append(":");
            for (String when : whens) {
                msg.append('\n').append(when);
            }
        }
        if (!afters.isEmpty()) {
            msg.append("\nThe possible \"after\"")
                    .append(afters.size() == 1 ? "" : "s")
                    .append(" you may play ")
                    .append(afters.size() == 1 ? "is" : "are")
                    .append(":");
            for (String after : afters) {
                msg.append('\n').append(after);
            }
        }
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("passOnEverythingWhensNAfters", "Pass On \"When\"s and \"After\"s"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg.toString(), buttons);
    }

    @ButtonHandler("passOnEverythingWhensNAfters")
    public static void passOnEverythingWhensNAfters(Game game, ButtonInteractionEvent event, Player player) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("undoPassOnAllWhensNAfters", "Undo Pass"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.toString()
                        + ", you have successfully passed on all \"when\"s and \"after\"s for the entire Agenda Phase."
                        + " You can undo this during the agenda if necessary, or with this button.",
                buttons);
        game.setStoredValue("passOnAllWhensNAfters" + player.getFaction(), "Yes");

        if ("agendawaiting".equalsIgnoreCase(game.getPhaseOfGame())) {
            game.setStoredValue("declinedWhens", game.getStoredValue("declinedWhens") + player.getFaction() + "_");
            buttons = new ArrayList<>();
            buttons.add(Buttons.red("queueAWhen", "Play A When"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    "You have declined to queue a \"when\". You can change your mind with this button.",
                    buttons);
            game.setStoredValue("declinedAfters", game.getStoredValue("declinedAfters") + player.getFaction() + "_");
            game.setStoredValue("queuedAftersLockedFor" + player.getFaction(), "Yes");
            buttons = new ArrayList<>();
            buttons.add(Buttons.red("queueAnAfter", "Play An \"After\""));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    "You have declined to queue an \"after\". You can change your mind with this button.",
                    buttons);
            offerPreVote(player);
            String alreadyResolved = game.getStoredValue("whensResolved");
            if (alreadyResolved.isEmpty()) {
                resolveWhenQueue(event, game);
            } else {
                resolveAfterQueue(event, game);
            }
        }
    }

    @ButtonHandler("undoPassOnAllWhensNAfters")
    public static void undoPassOnEverythingWhensNAfters(Game game, ButtonInteractionEvent event, Player player) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                player.toString()
                        + ", you have successfully undone passing on all \"when\"s and \"after\"s for the Agenda Phase."
                        + " You may still need to handle \"when\"s and \"after\"s for any currently ongoing agenda.");
        game.setStoredValue("passOnAllWhensNAfters" + player.getFaction(), "");
    }

    public static List<String> getPossibleWhenNames(Player player) {
        List<String> names = new ArrayList<>();
        if (player.hasAbility("quash") && (player.getStrategicCC() > 0 || player.hasRelicReady("emelpar"))) {
            names.add("Quash");
        }
        for (String acId : player.getPlayableActionCards()) {
            ActionCardModel actionCard = Mapper.getActionCard(acId);
            String actionCardWindow = actionCard.getWindow();
            if (actionCardWindow.contains("When an agenda is revealed")) {
                names.add(actionCard.getName());
            }
        }
        for (String pnId : player.getPromissoryNotes().keySet()) {
            if (!player.ownsPromissoryNote(pnId)
                    && ((pnId.endsWith("_ps") && !pnId.contains("absol")) || "favor".equals(pnId))) {
                names.add(StringUtils.capitalize(Mapper.getPromissoryNote(pnId).getColor() + " ")
                        + Mapper.getPromissoryNote(pnId).getName());
            }
        }
        return names;
    }

    private static List<Button> getPossibleWhenButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        if (player.hasAbility("quash") && (player.getStrategicCC() > 0 || player.hasRelicReady("emelpar"))) {
            buttons.add(Buttons.red("queueWhen_ability_quash", "Quash"));
        }
        for (String acId : player.getPlayableActionCards()) {
            ActionCardModel actionCard = Mapper.getActionCard(acId);
            String actionCardWindow = actionCard.getWindow();
            if (actionCardWindow.contains("When an agenda is revealed")) {
                buttons.add(Buttons.green("queueWhen_ac_" + acId, actionCard.getName()));
            }
        }
        for (String pnId : player.getPromissoryNotes().keySet()) {
            if (!player.ownsPromissoryNote(pnId)
                    && ((pnId.endsWith("_ps") && !pnId.contains("absol")) || "favor".equals(pnId))) {
                buttons.add(Buttons.red(
                        "queueWhen_pn_" + pnId,
                        StringUtils.capitalize(Mapper.getPromissoryNote(pnId).getColor() + " ")
                                + Mapper.getPromissoryNote(pnId).getName()));
            }
        }
        return buttons;
    }

    @ButtonHandler("queueWhen_")
    public static void queueWhen(Game game, String buttonID, ButtonInteractionEvent event, Player player) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        String when = buttonID.replace("queueWhen_", "");
        game.setStoredValue("queuedWhens", game.getStoredValue("queuedWhens") + player.getFaction() + "_");
        game.setStoredValue("queuedWhensFor" + player.getFaction(), when);
        String msg = "Successfully queued '" + event.getButton().getLabel()
                + "'. You can use this button to unqueue it and pass on \"when\"s.";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue("declineToQueueAWhen", "Pass On \"When\"s"));
        GMService.addForcePassWhenButtonForFowGM(game, player, buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        resolveWhenQueue(event, game);
    }

    @ButtonHandler("explainQueue")
    public static void explainQueue(ButtonInteractionEvent event) {
        String msg = """
            "When"s are any action card or ability that has a timing window of "when an agenda is revealed" and \
            "after"s are any action card or ability that has a timing window of "after an agenda is revealed". All "when"s need to be played (or declined to be played) \
            before any "after"s, and all "after"s need to be played (or declined to be played) before any votes are cast. The bot attempts to facilitate this process \
            via a queue system, so you can queue an "after" to automatically play later, while the game is still waiting on everyone to decline on "when"s. Or you can queue a pre-vote \
            or pre-abstain while the game is waiting on "when"s or "after"s to clear.

            Per rules, "when"s and "after"s get played in a certain order (speaker order). This order \
            can often be relevant (if someone plays a rider before you, that may influence where you play your rider, or even if you play a rider at all).

            This queue system is asking you "if no-one who was in front of you in speaker order played anything, would you play anything?". \
            If your answer is "yes", then it will play your chosen ability when everyone in front of you officially declines to play anything. \
            If someone else in front of you does decide to play something, then your answer will be discarded and you will be asked to reconsider if you wish to play something. \
            If your answer was "no" then by default the system will assume that your answer will remain no, but you can instruct it to ask you again. After you decide on "when"s \
            the system will ask you about "after"s.

            It should be understood that there is little benefit in stalling your decision here. \
            You have as much information as you need to answer the bot's queuestion, and if others provide more information (by playing any "when"s or "after"s) \
            you will be asked to privately decide again at that later point.""";

        MessageHelper.sendEphemeralMessageToEventChannel(event, msg);
    }

    @ButtonHandler("queueAfter_")
    public static void queueAfter(Game game, String buttonID, ButtonInteractionEvent event, Player player) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        String after = buttonID.replace("queueAfter_", "");
        game.setStoredValue("queuedAfters", game.getStoredValue("queuedAfters") + player.getFaction() + "_");
        game.setStoredValue("queuedAftersFor" + player.getFaction(), after);
        String msg = "Successfully queued '" + event.getButton().getLabel()
                + "'. You can use this button to unqueue it and pass on \"afters\".";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.blue("declineToQueueAnAfter", "Pass On \"After\"s"));
        GMService.addForcePassAfterButtonForFowGM(game, player, buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);

        msg =
                "By default, your queued \"after\" will be canceled if someone before you plays an \"after\". If you wish it to play regardless of others actions, press this button.";
        buttons = new ArrayList<>();
        buttons.add(Buttons.blue("lockAftersIn", "Play Regardless of Others"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
        resolveAfterQueue(event, game);
    }

    @ButtonHandler("lockAftersIn")
    public static void lockAftersIn(Game game, ButtonInteractionEvent event, Player player) {
        game.setStoredValue("queuedAftersLockedFor" + player.getFaction(), "Yes");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "Your \"after\" will now play regardless if another player also plays an \"after\" before you.");
    }

    public static List<String> getPossibleAfterNames(Player player) {
        List<String> names = new ArrayList<>();
        List<String> abilities = new ArrayList<>(Arrays.asList("radiance", "galactic_threat", "conspirators"));
        for (String ability : abilities) {
            if (player.hasAbility(ability)) {
                if ("galactic_threat".equalsIgnoreCase(ability)
                        && (!player.getGame()
                                        .getStoredValue("galacticThreatUsed")
                                        .isEmpty()
                                || !player.getGame()
                                        .getStoredValue("executiveOrder")
                                        .isEmpty())) {
                    continue;
                }
                names.add(Mapper.getAbility(ability).getName());
            }
        }
        for (String acId : player.getPlayableActionCards()) {
            ActionCardModel actionCard = Mapper.getActionCard(acId);
            String actionCardWindow = actionCard.getWindow();
            if (actionCardWindow.contains("After an agenda is revealed")
                    || actionCardWindow.contains("After the first agenda of this agenda phase is revealed")) {
                names.add(actionCard.getName());
            }
        }
        for (String pnId : player.getPromissoryNotes().keySet()) {
            if (!player.ownsPromissoryNote(pnId)) {
                if (pnId.contains("rider") || pnId.contains("dspnedyn") || pnId.contains("dspnkyro")) {
                    names.add(Mapper.getPromissoryNote(pnId).getName());
                }
            }
        }
        String planet = "tarrock";
        if (player.containsPlanet(planet)
                && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            names.add("Tarrock Ability");
        }

        if (player.hasLeaderUnlocked("keleresheroodlynn")) {
            names.add("Keleres Hero");
        }
        if (player.getGame().playerHasLeaderUnlockedOrAlliance(player, "atokeracommander")) {
            names.add("Atokera Commander Ability");
        }

        if (player.hasTechReady("dsedyng")) {
            names.add("Unity Algorithm");
        }
        return names;
    }

    private static List<Button> getPossibleAferButtons(Player player) {
        List<Button> buttons = new ArrayList<>();
        List<String> abilities = new ArrayList<>(Arrays.asList("radiance", "galactic_threat", "conspirators"));
        for (String ability : abilities) {
            if (player.hasAbility(ability)) {
                if ("galactic_threat".equalsIgnoreCase(ability)
                        && !player.getGame()
                                .getStoredValue("galacticThreatUsed")
                                .isEmpty()) {
                    continue;
                }
                buttons.add(Buttons.red(
                        "queueAfter_ability_" + ability,
                        Mapper.getAbility(ability).getName()));
            }
        }
        for (String acId : player.getPlayableActionCards()) {
            ActionCardModel actionCard = Mapper.getActionCard(acId);
            String actionCardWindow = actionCard.getWindow();
            if (actionCardWindow.contains("After an agenda is revealed")
                    || actionCardWindow.contains("After the first agenda of this agenda phase is revealed")) {
                buttons.add(Buttons.green("queueAfter_ac_" + acId, actionCard.getName()));
            }
        }
        for (String pnId : player.getPromissoryNotes().keySet()) {
            if (!player.ownsPromissoryNote(pnId)) {
                if (pnId.contains("rider") || pnId.contains("dspnedyn") || pnId.contains("dspnkyro")) {
                    buttons.add(Buttons.red(
                            "queueAfter_pn_" + pnId,
                            Mapper.getPromissoryNote(pnId).getName()));
                }
            }
        }
        String planet = "tarrock";
        if (player.containsPlanet(planet)
                && !player.getExhaustedPlanetsAbilities().contains(planet)) {
            buttons.add(Buttons.red("queueAfter_planet_" + planet, "Tarrock Ability"));
        }

        if (player.getGame().playerHasLeaderUnlockedOrAlliance(player, "atokeracommander")) {
            buttons.add(Buttons.red("queueAfter_leader_Atokera Commander", "Atokera Commander Ability"));
        }

        if (player.hasLeaderUnlocked("keleresheroodlynn")) {
            buttons.add(Buttons.red("queueAfter_leader_Keleres Xxcha Hero", "Keleres Hero"));
        }

        if (player.hasTechReady("dsedyng")) {
            buttons.add(Buttons.red("queueAfter_tech_dsedyng", "Unity Algorithm"));
        }
        CryypterHelper.addVotCRiderQueueButtons(player, buttons);

        return buttons;
    }

    static void offerEveryoneWhensQueue(Game game) {
        String factionsThatHavePassedOnWhens = game.getStoredValue("declinedWhens");
        for (Player player : game.getRealPlayers()) {
            if (factionsThatHavePassedOnWhens.contains(player.getFaction())) {
                continue;
            }
            List<String> whens = getPossibleWhenNames(player);
            StringBuilder msg = new StringBuilder(player.getRepresentation(true, true)
                    + " now is the time to decide whether or not you will play a \"when\". If you do,"
                    + " the bot will queue your \"when\" to play in the proper order (after those before you in speaker order decline). You may currently"
                    + " play " + StringHelper.pluralize(whens.size(), "\"when\"") + ".");
            if (!whens.isEmpty()) {
                msg.append("\nThe possible \"when\"")
                        .append(whens.size() == 1 ? "" : "s")
                        .append(" you may play ")
                        .append(whens.size() == 1 ? "is" : "are")
                        .append(":");
                for (String when : whens) {
                    msg.append('\n').append(when);
                }
            }
            List<Button> buttons = new ArrayList<>();

            buttons.add(Buttons.gray("queueAWhen", "Play A \"When\""));
            buttons.add(Buttons.blue("declineToQueueAWhen", "Pass On \"When\"s"));
            GMService.addForcePassWhenButtonForFowGM(game, player, buttons);
            buttons.add(Buttons.gray("explainQueue", "How Does This Work?"));
            MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg.toString(), buttons);
        }
    }

    private static void resolveWhenQueue(ButtonInteractionEvent event, Game game) {
        String alreadyResolved = game.getStoredValue("whensResolved");
        if (alreadyResolved.isEmpty()) {
            String lastPlayerToPlayAWhen = game.getStoredValue("lastPlayerToPlayAWhen");
            List<Player> agendaAbilityResolutionOrder = Helper.getSpeakerOrFullPriorityOrder(game);
            if (!lastPlayerToPlayAWhen.isEmpty()) {
                agendaAbilityResolutionOrder = Helper.getSpeakerOrFullPriorityOrderFromPlayer(
                        game.getPlayerFromColorOrFaction(lastPlayerToPlayAWhen), game);
                agendaAbilityResolutionOrder =
                        Helper.getSpeakerOrFullPriorityOrderFromPlayer(agendaAbilityResolutionOrder.get(1), game);
            }
            for (Player player : agendaAbilityResolutionOrder) {
                String factionsThatHavePassedOnWhens = game.getStoredValue("declinedWhens");
                if (!factionsThatHavePassedOnWhens.contains(player.getFaction())) {
                    String factionsThatHaveQueuedAWhen = game.getStoredValue("queuedWhens");
                    if (!factionsThatHaveQueuedAWhen.contains(player.getFaction())) {
                        MessageHelper.sendMessageToChannel(
                                player.getCardsInfoThread(),
                                "This is a nudge that the \"when\" queue is currently waiting on you.");
                        int num = 0;
                        for (Player p2 : game.getRealPlayers()) {
                            if (game.getStoredValue("queuedWhens").contains(p2.getFaction())
                                    || game.getStoredValue("declinedWhens").contains(p2.getFaction())) {
                                continue;
                            }
                            num++;
                        }

                        MessageHelper.sendMessageToChannel(
                                game.getActionsChannel(),
                                "The game is currently waiting on " + pluralPerson(num) + " to decide on \"when\"s.");
                    } else {
                        game.setStoredValue(
                                "queuedWhens",
                                game.getStoredValue("queuedWhens").replace(player.getFaction() + "_", ""));
                        game.setStoredValue("lastPlayerToPlayAWhen", player.getFaction());
                        String when = game.getStoredValue("queuedWhensFor" + player.getFaction());
                        String type = when.split("_")[0];
                        when = when.replace(type + "_", "");
                        switch (type) {
                            case "ability" -> ButtonHelperFactionSpecific.quash(event, player, game);
                            case "pn" -> {
                                PromissoryNoteHelper.resolvePNPlay(when, player, game, event);
                                resolveWhenQueue(event, game);
                                offerEveryoneWhensQueue(game);
                            }
                            case "ac" -> {
                                ActionCardHelper.playAC(event, game, player, when, game.getMainGameChannel());
                                if (!when.contains("veto")) {
                                    resolveWhenQueue(event, game);
                                    offerEveryoneWhensQueue(game);
                                }
                            }
                        }
                    }
                    return;
                }
            }
            MessageHelper.sendMessageToChannel(game.getMainGameChannel(), "# All players have passed on \"when\"s.");
            game.setStoredValue("whensResolved", "Yes");
            resolveAfterQueue(event, game);
        }
    }

    private static void resolveAfterQueue(ButtonInteractionEvent event, Game game) {
        resolveAfterQueue(event, game, false);
    }

    private static void resolveAfterQueue(ButtonInteractionEvent event, Game game, boolean alreadyPinged) {
        String alreadyResolved = game.getStoredValue("aftersResolved");
        String whensResolved = game.getStoredValue("whensResolved");
        if (alreadyResolved.isEmpty() && !whensResolved.isEmpty()) {
            String lastPlayerToPlayAnAfter = game.getStoredValue("lastPlayerToPlayAnAfter");
            List<Player> agendaAbilityResolutionOrder = Helper.getSpeakerOrFullPriorityOrder(game);
            if (!lastPlayerToPlayAnAfter.isEmpty()) {
                agendaAbilityResolutionOrder = Helper.getSpeakerOrFullPriorityOrderFromPlayer(
                        game.getPlayerFromColorOrFaction(lastPlayerToPlayAnAfter), game);
                agendaAbilityResolutionOrder =
                        Helper.getSpeakerOrFullPriorityOrderFromPlayer(agendaAbilityResolutionOrder.get(1), game);
            }
            for (Player player : agendaAbilityResolutionOrder) {
                String factionsThatHavePassedOnAfters = game.getStoredValue("declinedAfters");
                if (!factionsThatHavePassedOnAfters.contains(player.getFaction())) {
                    String factionsThatHaveQueuedAnAfter = game.getStoredValue("queuedAfters");
                    if (!factionsThatHaveQueuedAnAfter.contains(player.getFaction())) {
                        MessageHelper.sendMessageToChannel(
                                player.getCardsInfoThread(),
                                "This is a nudge that the \"after\" queue is currently waiting on you.");
                        int num = 0;
                        for (Player p2 : game.getRealPlayers()) {
                            if (game.getStoredValue("queuedAfters").contains(p2.getFaction())
                                    || game.getStoredValue("declinedAfters").contains(p2.getFaction())) {
                                continue;
                            }
                            num++;
                        }
                        MessageHelper.sendMessageToChannel(
                                game.getActionsChannel(),
                                "The game is currently waiting on " + pluralPerson(num) + " to decide on \"after\"s.");
                    } else {
                        game.setStoredValue(
                                "queuedAfters",
                                game.getStoredValue("queuedAfters").replace(player.getFaction() + "_", ""));
                        game.setStoredValue("lastPlayerToPlayAnAfter", player.getFaction());
                        String after = game.getStoredValue("queuedAftersFor" + player.getFaction());
                        String type = after.split("_")[0];
                        after = after.replace(type + "_", "");
                        List<Button> riderButtons;
                        switch (type) {
                            case "ability" -> {
                                if ("conspirators".equalsIgnoreCase(after)) {
                                    game.setStoredValue("conspiratorsFaction", player.getFaction());
                                    game.setStoredValue("conspiratorsUsed", player.getFaction());
                                    MessageHelper.sendMessageToChannel(
                                            game.getMainGameChannel(),
                                            game.getPing()
                                                    + " The **Conspirators** ability has been used, which means the player will vote after the speaker. This ability may be used once per Agenda Phase.");
                                    if (!game.isFowMode()) {
                                        AgendaHelper.listVoteCount(game, game.getMainGameChannel());
                                    }
                                } else {
                                    String ability = "Galactic Threat";
                                    if ("galactic_threat".equalsIgnoreCase(after)) {
                                        game.setStoredValue("galacticThreatUsed", "Yes");
                                        riderButtons = AgendaRiderHelper.getAgendaButtons(
                                                "Galactic Threat Rider", game, player.factionButtonChecker());
                                    } else {
                                        ability = "Radiance";
                                        riderButtons = AgendaRiderHelper.getAgendaButtons(
                                                "Radiance", game, player.factionButtonChecker());
                                    }
                                    MessageHelper.sendMessageToChannelWithFactionReact(
                                            player.getCorrectChannel(),
                                            player.toString() + ", please choose your target for the " + ability
                                                    + " ability.",
                                            game,
                                            player,
                                            riderButtons);
                                }
                            }
                            case "pn" -> PromissoryNoteHelper.resolvePNPlay(after, player, game, event);
                            case "planet" -> PlanetExhaustAbility.doAction(event, player, "tarrock", game, true);
                            case "tech" -> {
                                player.exhaustTech("dsedyng");
                                riderButtons = AgendaRiderHelper.getAgendaButtons(
                                        "Edyn Unity Algorithm", game, player.factionButtonChecker());
                                MessageHelper.sendMessageToChannelWithFactionReact(
                                        player.getCorrectChannel(),
                                        player.toString() + ", please choose your target.",
                                        game,
                                        player,
                                        riderButtons);
                            }
                            case "leader" -> {
                                if (after.toLowerCase().contains("keleres")) {
                                    Leader playerLeader = player.getLeader("keleresheroodlynn")
                                            .orElse(null);
                                    playerLeader = CryypterHelper.keleresHeroCheck(player, playerLeader);
                                    if (playerLeader != null) {
                                        String message = player.toString() + " played "
                                                + Helper.getLeaderFullRepresentation(playerLeader);
                                        player.removeLeader(playerLeader);

                                        ButtonHelperHeroes.checkForMykoHero(game, playerLeader.getId(), player);
                                        MessageHelper.sendMessageToChannel(
                                                player.getCorrectChannel(),
                                                message + " - Odlynn Myrr, the Keleres (Xxcha) hero, has been purged.");
                                    }
                                    riderButtons = AgendaRiderHelper.getAgendaButtons(
                                            "Keleres Xxcha Hero", game, player.factionButtonChecker());
                                } else if (after.toLowerCase().contains("envoy")) {
                                    riderButtons = AgendaRiderHelper.getAgendaButtons(
                                            after, game, player.factionButtonChecker());
                                } else {
                                    riderButtons = AgendaRiderHelper.getAgendaButtons(
                                            "Atokera Commander Ability", game, player.factionButtonChecker());
                                }
                                MessageHelper.sendMessageToChannelWithFactionReact(
                                        player.getCorrectChannel(),
                                        player.toString() + ", please choose your target.",
                                        game,
                                        player,
                                        riderButtons);
                            }
                            case "ac" -> ActionCardHelper.playAC(event, game, player, after, game.getMainGameChannel());
                            case "agenda" ->
                                AgendaHelper.autoResolve(event, player, "autoresolve_manualcommittee", game);
                        }
                        for (Player p2 : game.getRealPlayers()) {
                            if (!game.getStoredValue("preVoting" + p2.getFaction())
                                    .isEmpty()) {
                                erasePreVoteDueToAfterPlay(p2, game);
                            }
                            if (!game.getStoredValue("queuedAftersLockedFor" + p2.getFaction())
                                    .isEmpty()) {
                                continue;
                            }
                            game.setStoredValue(
                                    "queuedAfters",
                                    game.getStoredValue("queuedAfters").replace(p2.getFaction() + "_", ""));
                            game.setStoredValue(
                                    "declinedAfters",
                                    game.getStoredValue("declinedAfters").replace(p2.getFaction() + "_", ""));
                        }
                        resolveAfterQueue(event, game, true);
                        if (!alreadyPinged) {
                            for (Player p2 : game.getRealPlayers()) {
                                if (game.getStoredValue("queuedAfters").contains(p2.getFaction())
                                        || game.getStoredValue("declinedAfters").contains(p2.getFaction())) {
                                    continue;
                                }
                                List<String> afters = getPossibleAfterNames(p2);
                                StringBuilder msg = new StringBuilder(p2.toString()
                                        + "due to the recent playing of an \"after\", you are now being asked to decide whether or not you will play an \"after\"."
                                        + " You can currently play "
                                        + StringHelper.pluralize(afters.size(), "\"after\"") + ".");
                                if (!afters.isEmpty()) {
                                    msg.append("\nThe possible \"after\"")
                                            .append(afters.size() == 1 ? "" : "s")
                                            .append(" you may play ")
                                            .append(afters.size() == 1 ? "is" : "are")
                                            .append(":");
                                    for (String after2 : afters) {
                                        msg.append('\n').append(after2);
                                    }
                                }
                                List<Button> buttons = new ArrayList<>();

                                buttons.add(Buttons.gray("queueAnAfter", "Play An \"After\""));
                                buttons.add(Buttons.blue("declineToQueueAnAfter", "Pass On \"After\"s"));
                                GMService.addForcePassAfterButtonForFowGM(game, player, buttons);
                                MessageHelper.sendMessageToChannelWithButtons(
                                        p2.getCardsInfoThread(), msg.toString(), buttons);
                            }
                        }
                    }
                    return; // The person up has not yet decided whether to queue or not queue an after
                }
            }
            String msg = "# All players have passed on \"after\"s.";
            if (!game.isOmegaPhaseMode() && !game.isHiddenAgendaMode()) {
                msg += " Voting will now begin.";
                MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
                AgendaHelper.startTheVoting(game);
            } else {
                if (AgendaHelper.getPlayersWhoNeedToPreVoted(game).isEmpty()) {
                    msg += " Voting will now begin.";
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg);
                    AgendaHelper.startTheVoting(game);
                } else {
                    msg += " Still need "
                            + AgendaHelper.getPlayersWhoNeedToPreVoted(game).size() + " players to prevote";
                    if (game.isHiddenAgendaMode()) {
                        msg += " or pre-abstain";
                    }
                    MessageHelper.sendMessageToChannel(game.getMainGameChannel(), msg + ".");
                    for (Player p2 : AgendaHelper.getPlayersWhoNeedToPreVoted(game)) {
                        MessageHelper.sendMessageToChannel(
                                p2.getCardsInfoThread(),
                                p2.toString()
                                        + ", the game is waiting upon you to decide voting before it moves onto voting.");
                    }
                }
            }
            game.setStoredValue("aftersResolved", "Yes");
        }
    }

    @ButtonHandler("pingNonresponders")
    public static void pingNonresponders(Game game, Player player) {
        AutoPingCron.pingMissingAgendaPlayers(game);
        String alreadyResolved = game.getStoredValue("whensResolved");
        int num = 0;
        if (alreadyResolved.isEmpty()) {
            for (Player p2 : game.getRealPlayers()) {
                if (game.getStoredValue("queuedWhens").contains(p2.getFaction())
                        || game.getStoredValue("declinedWhens").contains(p2.getFaction())) {
                    continue;
                }
                num++;
            }
        } else {
            for (Player p2 : game.getRealPlayers()) {
                if (game.getStoredValue("queuedAfters").contains(p2.getFaction())
                        || game.getStoredValue("declinedAfters").contains(p2.getFaction())) {
                    continue;
                }
                num++;
            }
        }
        String msg = player.getRepresentation(true, false) + " has chosen to issue a reminder ping"
                + " to those who have not yet responded to \"when\"s or \"after\"s (a total of " + pluralPerson(num)
                + "). They have been pinged in their private thread. ";
        if (game.isHiddenAgendaMode() || game.isOmegaPhaseMode()) {
            msg += "The "
                    + pluralPerson(
                            AgendaHelper.getPlayersWhoNeedToPreVoted(game).size())
                    + " who still need to decide on voting were also reminded.";
        }
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), msg);
    }

    static String pluralPerson(int num) {
        return StringHelper.pluralize(num, "player");
    }

    @ButtonHandler("queueAWhen")
    public static void queueAWhen(Game game, ButtonInteractionEvent event, Player player) {
        List<Button> buttons = getPossibleWhenButtons(player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "The bot knows of no \"when\"s that you can play."
                            + " If this is a bug or unimplemented ability, please report it in the `#bot-bugs-and-feature-requests` channel.");
            return;
        }
        buttons.add(Buttons.blue("declineToQueueAWhen", "Pass On \"When\"s"));
        GMService.addForcePassWhenButtonForFowGM(game, player, buttons);
        game.setStoredValue(
                "declinedWhens", game.getStoredValue("declinedWhens").replace(player.getFaction() + "_", ""));
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), "You can use these buttons to queue a \"when\".", buttons);
    }

    @ButtonHandler("declineToQueueAWhen")
    public static void declineToQueueAWhen(Game game, ButtonInteractionEvent event, Player player) {
        game.setStoredValue("queuedWhens", game.getStoredValue("queuedWhens").replace(player.getFaction() + "_", ""));
        game.setStoredValue("declinedWhens", game.getStoredValue("declinedWhens") + player.getFaction() + "_");
        resolveWhenQueue(event, game);
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("queueAWhen", "Play A \"When\""));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                "You have declined to queue a \"when\". You can change your mind with this button.",
                buttons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        List<String> afters = getPossibleAfterNames(player);
        StringBuilder msg = new StringBuilder(player.getRepresentation(true, true)
                + " now is the time to decide whether or not you will play an \"after\". If you do,"
                + " the bot will queue your after to play in the proper order (after those before you in speaker order decline). You may currently"
                + " play " + StringHelper.pluralize(afters.size(), "\"after\"") + ".");
        if (!afters.isEmpty()) {
            msg.append("\nThe possible \"after\"")
                    .append(afters.size() == 1 ? "" : "s")
                    .append(" you may play ")
                    .append(afters.size() == 1 ? "is" : "are")
                    .append(":");
            for (String after : afters) {
                msg.append('\n').append(after);
            }
        }
        buttons = new ArrayList<>();

        buttons.add(Buttons.gray("queueAnAfter", "Play An \"After\""));
        buttons.add(Buttons.blue("declineToQueueAnAfter", "Pass On \"After\"s"));
        GMService.addForcePassAfterButtonForFowGM(game, player, buttons);
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg.toString(), buttons);
    }

    @ButtonHandler("queueAnAfter")
    public static void queueAnAfter(Game game, ButtonInteractionEvent event, Player player) {
        List<Button> buttons = getPossibleAferButtons(player);
        if (buttons.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    player.getCardsInfoThread(),
                    "The bot knows of no \"after\" that you can play."
                            + " If this is a bug or unimplemented ability, please report it in the `#bot-bugs-and-feature-requests` channel.");
            return;
        }
        buttons.add(Buttons.blue("declineToQueueAnAfter", "Pass On \"After\"s"));
        GMService.addForcePassAfterButtonForFowGM(game, player, buttons);
        game.setStoredValue("queuedAftersLockedFor" + player.getFaction(), "");
        game.setStoredValue(
                "declinedAfters", game.getStoredValue("declinedAfters").replace(player.getFaction() + "_", ""));
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(), "You can use these buttons to queue an \"after\".", buttons);
    }

    @ButtonHandler("unlockQueuedAfters")
    public static void unlockQueuedAfters(Game game, ButtonInteractionEvent event, Player player) {
        game.setStoredValue("queuedAftersLockedFor" + player.getFaction(), "");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "You will now be asked to decide again if someone else plays an \"after\".");
    }

    @ButtonHandler("declineToQueueAnAfter")
    public static void declineToQueueAnAfter(Game game, ButtonInteractionEvent event, Player player) {
        game.setStoredValue("queuedAfters", game.getStoredValue("queuedAfters").replace(player.getFaction() + "_", ""));
        game.setStoredValue("declinedAfters", game.getStoredValue("declinedAfters") + player.getFaction() + "_");
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.red("queueAnAfter", "Play An \"After\""));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                "You have declined to queue an \"after\". You can change your mind with this button.",
                buttons);
        game.setStoredValue("queuedAftersLockedFor" + player.getFaction(), "Yes");
        if (!getPossibleAfterNames(player).isEmpty()) {
            buttons = new ArrayList<>();
            buttons.add(Buttons.red("unlockQueuedAfters", "Be Asked Again"));
            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCardsInfoThread(),
                    "You will not be asked again by default if someone else plays an \"after\". You can change that "
                            + " and be asked to decide on \"after\"s again when someone else plays an \"after\" by pressing this button.",
                    buttons);
        }
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        offerPreVote(player);
        resolveAfterQueue(event, game);
        String msg = player.toString() + " you have the option to pre-pass on agenda shenanigans here."
                + " Agenda shenanigans are the action cards _Bribery_, _Confusing Legal Text_, _Confounding Legal Text_, and _Deadly Plot_."
                + " Feel free not to pre-pass, this is simply an optional way to resolve agendas faster.";
        buttons = new ArrayList<>();

        buttons.add(Buttons.green("resolvePreassignment_Pass On Shenanigans", "Pre-Pass"));
        buttons.add(Buttons.red("deleteButtons", "Decline"));
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    private static boolean playerDoesNotHaveShenanigans(Player player) {
        Set<String> shenanigans = Set.of("deadly_plot", "bribery", "confounding", "confusing");
        if (player.getActionCards().keySet().stream().anyMatch(shenanigans::contains)) {
            return false;
        }
        if (player.hasPlanet("garbozia")) {
            return ActionCardHelper.getGarboziaActionCards(player.getGame()).keySet().stream()
                    .noneMatch(shenanigans::contains);
        }
        return true;
    }

    private static void offerPreVote(Player player) {

        Game game = player.getGame();
        int[] voteInfo = AgendaHelper.getVoteTotal(player, game);
        if (voteInfo[0] < 1) {
            return;
        }
        String msg = player.toString()
                + " if you intend to preset an abstention or vote on this agenda, you have the option to preset it here."
                + " Feel free not to, this is simply an optional way to resolve agendas faster. Any pre-votes will be automatically erased if someone plays an \"after\".";
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("preVote", "Pre-Vote"));
        if (player.hasAbility("future_sight")
                && game.getStoredValue("executiveOrder").isEmpty()) {
            msg += " Reminder that you have **Future Sight** and may wish to not abstain.";
        }

        msg += CryypterHelper.argentEnvoyReminder(player, game);

        Player argent = Helper.getPlayerFromAbility(game, "zeal");
        if (game.isOmegaPhaseMode()) {
            if (argent != null) {
                if (argent == player) {
                    msg = player.toString()
                            + ", since this is game is in Omega Phase mode, all non-speaker players who can vote will need to pre-vote using this button before voting can begin. "
                            + "If you cannot vote due to playing a rider or having no votes, just ignore this button. "
                            + "Otherwise, since you have the **Zeal** ability, you need to vote first and do so now, even if you are speaker. Other players are free to wait to vote until they see your vote.";
                } else {
                    msg = player.toString()
                            + " since this is game is in Omega Phase mode, all non-speaker players who can vote will need to pre-vote using this button before voting can begin. "
                            + "If you cannot vote due to playing a rider or having no votes, or if you are speaker, just ignore this button. "
                            + "Since "
                            + ("argent".equalsIgnoreCase(argent.getFaction())
                                    ? "Argent is in this game"
                                    : "a player has the **Zeal** ability")
                            + ", you can wait to pre-vote until you see what they vote (assuming they can vote).";
                }
            } else {
                msg = player.toString() + ", since this is game is in Omega Phase mode,"
                        + " all non-speaker players who can vote will need to pre-vote using this button before voting can begin."
                        + " If you cannot vote due to playing a rider or having no votes, or if you are speaker, just ignore this button.";
            }
        } else {
            if (game.isHiddenAgendaMode()) {
                if (argent != null) {
                    if (argent == player) {
                        msg = player.toString()
                                + ", since this is game is in Hidden Agenda mode, all players will need to pre-vote (or pre-abstain) using these button before voting can begin."
                                + " If you cannot vote due to playing a rider or having no votes just ignore this button."
                                + " Otherwise, since you have the **Zeal** ability, you need to vote (or abstain) first, and to do so now."
                                + " Other players are free to wait to vote until they see your vote.";
                    } else {
                        msg = player.toString()
                                + " since this is game is in Hidden Agenda mode, all players will need to pre-vote (or pre-abstain) using these button before voting can begin."
                                + " If you cannot vote due to playing a rider or having no votes just ignore this button."
                                + " Since "
                                + ("argent".equalsIgnoreCase(argent.getFaction())
                                        ? "Argent is in this game"
                                        : "a player has the **Zeal** ability")
                                + ", you can wait to pre-vote until you see what they vote (assuming they can vote).";
                    }
                } else {
                    msg = player.toString()
                            + " since this is game is in Hidden Agenda mode, all players will need to pre-vote or pre-abstain using these button before voting can begin."
                            + " If you cannot vote due to playing a rider or having no votes just ignore this button.";
                }
            }
            buttons.add(Buttons.blue("resolvePreassignment_Abstain On Agenda", "Pre-Abstain"));
            buttons.add(Buttons.red("deleteButtons", "Decline"));
        }

        if (!game.getStoredValue("agendaChecksNBalancesAgainst").isEmpty()) {
            msg +=
                    " **Reminder: _Checks and Balances_ has resolved \"Against\" — you will only be able to ready 3 planets at the end of this agenda phase.**";
        }
        MessageHelper.sendMessageToChannelWithButtons(player.getCardsInfoThread(), msg, buttons);
    }

    private static List<Button> getWhenButtons(Game game) {
        Button playWhen = Buttons.red("play_when", "Play \"When\"");
        Button noWhen = Buttons.blue("no_when", "No \"When\"s (for now)", MiscEmojis.NoWhens);
        Button noWhenPersistent =
                Buttons.blue("no_when_persistent", "No \"When\"s (for this agenda)", MiscEmojis.NoWhens);
        List<Button> whenButtons = new ArrayList<>(List.of(playWhen, noWhen, noWhenPersistent));
        Player quasher = Helper.getPlayerFromAbility(game, "quash");
        if (quasher != null && quasher.getStrategicCC() > 0) {
            String factionChecker = "FFCC_" + quasher.getFaction() + "_";
            Button quashButton = Buttons.red(factionChecker + "quash", "Quash Agenda", FactionEmojis.Xxcha);
            if (game.isFowMode()) {
                MessageHelper.sendMessageToChannelWithButton(
                        quasher.getPrivateChannel(),
                        "Please use this button to **Quash**, if you so wish.",
                        quashButton);
            } else {
                whenButtons.add(quashButton);
            }
        }
        return whenButtons;
    }

    public static List<Button> getAfterButtons(Game game) {
        List<Button> afterButtons = new ArrayList<>();
        Button playAfter = Buttons.red("play_after_Non-AC Rider", "Play A Non-Action Card Rider");
        if (game.isFowMode()) {
            afterButtons.add(playAfter);
        }

        if (ButtonHelper.shouldKeleresRiderExist(game) && !game.isFowMode()) {
            afterButtons.add(Buttons.gray("play_after_Keleres Rider", "Play Keleres Rider", FactionEmojis.Keleres));
        }

        if (!game.isFowMode()
                && Helper.getDateDifference(game.getCreationDate(), Helper.getDateRepresentation(1705824000011L)) < 0) {
            for (Player player : game.getRealPlayers()) {
                String factionChecker = player.factionButtonChecker();
                String planet = "tarrock";
                if (player.containsPlanet(planet)
                        && !player.getExhaustedPlanetsAbilities().contains(planet)) {
                    afterButtons.add(Buttons.green(
                            factionChecker + "planetAbilityExhaust_" + planet,
                            "Use Tarrock Ability",
                            player.getFactionEmoji()));
                }
            }
        }

        if (game.getPNOwner("dspnedyn") != null && !game.isFowMode()) {
            afterButtons.add(Buttons.gray("play_after_Edyn Rider", "Play Edyn Rider", FactionEmojis.edyn));
        }

        if (game.getPNOwner("dspnkyro") != null && !game.isFowMode()) {
            afterButtons.add(Buttons.gray("play_after_Kyro Rider", "Play Kyro Rider", FactionEmojis.kyro));
        }

        if (Helper.getPlayerFromAbility(game, "galactic_threat") != null) {
            Player nekroProbably = Helper.getPlayerFromAbility(game, "galactic_threat");
            String factionChecker = "FFCC_" + nekroProbably.getFaction() + "_";
            afterButtons.add(Buttons.gray(
                    factionChecker + "play_after_Galactic Threat Rider",
                    "Predict Outcome for Galactic Threat",
                    FactionEmojis.Nekro));
        }

        // conspirators
        if (Helper.getPlayerFromAbility(game, "conspirators") != null && !game.isFowMode()) {
            Player nekroProbably = Helper.getPlayerFromAbility(game, "conspirators");
            String factionChecker = "FFCC_" + nekroProbably.getFaction() + "_";
            afterButtons.add(Buttons.gray(
                    factionChecker + "play_after_Conspirators", "Use Conspirators", FactionEmojis.zealots));
        }

        if (Helper.getPlayerFromUnlockedLeader(game, "keleresheroodlynn") != null) {
            Player keleresX = Helper.getPlayerFromUnlockedLeader(game, "keleresheroodlynn");
            String factionChecker = "FFCC_" + keleresX.getFaction() + "_";
            afterButtons.add(Buttons.gray(
                    factionChecker + "play_after_Keleres Xxcha Hero",
                    "Play Keleres (Xxcha) Hero",
                    FactionEmojis.Keleres));
        }

        if (Helper.getPlayerFromAbility(game, "radiance") != null) {
            Player edyn = Helper.getPlayerFromAbility(game, "radiance");
            String factionChecker = "FFCC_" + edyn.getFaction() + "_";
            afterButtons.add(Buttons.gray(
                    factionChecker + "play_after_Edyn Radiance Ability",
                    "Use Edyn Radiance Ability",
                    FactionEmojis.edyn));
        }

        for (Player p1 : game.getRealPlayers()) {
            String factionChecker = "FFCC_" + p1.getFaction() + "_";
            if (p1.hasTechReady("dsedyng")) {
                afterButtons.add(Buttons.gray(
                        factionChecker + "play_after_Edyn Unity Algorithm", "Use Unity Algorithm", FactionEmojis.edyn));
            }
            if (game.getCurrentAgendaInfo().contains("Player")
                    && IsPlayerElectedService.isPlayerElected(game, p1, "committee")) {
                afterButtons.add(Buttons.gray(
                        factionChecker + "autoresolve_manualcommittee", "Use Committee Formation", CardEmojis.Agenda));
            }
        }
        CryypterHelper.addVotCAfterButtons(game, afterButtons);
        afterButtons.add(Buttons.blue("no_after", "No \"After\"s (for now)", MiscEmojis.NoAfters));
        afterButtons.add(Buttons.blue("no_after_persistent", "No \"After\"s (for this agenda)", MiscEmojis.NoAfters));
        return afterButtons;
    }

    @ButtonHandler("play_after_")
    public static void playAfter(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String riderName = buttonID.replace("play_after_", "");
        List<Button> riderButtons = AgendaRiderHelper.getAgendaButtons(riderName, game, player.factionButtonChecker());
        MessageChannel mainGameChannel = game.getMainGameChannel();
        String pnKey = "fin";

        if ("Keleres Rider".equalsIgnoreCase(riderName)
                || "Edyn Rider".equalsIgnoreCase(riderName)
                || "Kyro Rider".equalsIgnoreCase(riderName)) {
            if ("Keleres Rider".equalsIgnoreCase(riderName)) {
                for (String pn : player.getPromissoryNotes().keySet()) {
                    if (pn.contains("rider")) {
                        pnKey = pn;
                    }
                }
                if ("fin".equalsIgnoreCase(pnKey)) {
                    MessageHelper.sendMessageToChannel(
                            mainGameChannel, player.toString() + ", you don't have the _Keleres Rider_.");
                    return;
                }
                if (player.getFaction().contains("keleres")) {
                    MessageHelper.sendMessageToChannel(
                            mainGameChannel, player.toString() + ", you cannot play your own promissory note.");
                    return;
                }
            } else if ("Edyn Rider".equalsIgnoreCase(riderName)) {
                for (String pn : player.getPromissoryNotes().keySet()) {
                    if (pn.contains("dspnedyn")) {
                        pnKey = pn;
                    }
                }
                if ("fin".equalsIgnoreCase(pnKey)) {
                    MessageHelper.sendMessageToChannel(mainGameChannel, "You don't have the _Edyn Rider_.");
                    return;
                }
            } else if ("Kyro Rider".equalsIgnoreCase(riderName)) {
                for (String pn : player.getPromissoryNotes().keySet()) {
                    if (pn.contains("dspnkyro")) {
                        pnKey = pn;
                    }
                }
                if ("fin".equalsIgnoreCase(pnKey)) {
                    MessageHelper.sendMessageToChannel(mainGameChannel, "You don't have the _Kyro Rider_.");
                    return;
                }
            }

            ReactionService.addReaction(event, game, player, true, true, "playing " + riderName, ".");
            PromissoryNoteHelper.resolvePNPlay(pnKey, player, game, event);
        } else {
            ReactionService.addReaction(event, game, player, true, true, "playing " + riderName, ".");

            if (riderName.contains("Unity Algorithm")) {
                player.exhaustTech("dsedyng");
            }
            if ("conspirators".equalsIgnoreCase(riderName)) {
                game.setStoredValue("conspiratorsFaction", player.getFaction());
                MessageHelper.sendMessageToChannel(
                        game.getMainGameChannel(),
                        game.getPing()
                                + " The **Conspirators** ability has been used, which means the player will vote after the speaker. This ability may be used once per Agenda Phase.");
                if (!game.isFowMode()) {
                    AgendaHelper.listVoteCount(game, game.getMainGameChannel());
                }
            } else {
                MessageHelper.sendMessageToChannelWithFactionReact(
                        mainGameChannel, "Please choose your rider target.", game, player, riderButtons);
                if ("Keleres Xxcha Hero".equalsIgnoreCase(riderName)) {
                    Leader playerLeader = player.getLeader("keleresheroodlynn").orElse(null);
                    playerLeader = CryypterHelper.keleresHeroCheck(player, playerLeader);
                    if (playerLeader != null) {
                        String message =
                                player.toString() + " played " + Helper.getLeaderFullRepresentation(playerLeader);
                        player.removeLeader(playerLeader);
                        MessageHelper.sendMessageToChannel(
                                event.getMessageChannel(),
                                message + " - Odlynn Myrr, the Keleres (Xxcha) hero, has been purged.");
                    }
                }
            }
        }
        // "dspnedyn"
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("no_when_persistent")
    public static void noWhenPersistent(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No whens (locked in)" : null;
        game.addPlayersWhoHitPersistentNoWhen(player.getFaction());
        ReactionService.addReaction(event, game, player, message);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "You hit \"No Whens\" for this entire agenda. If you change your mind, you can just play a \"When\" or remove this setting by hitting \"No Whens (For Now)\".");
    }

    @ButtonHandler("no_after_persistent")
    public static void noAfterPersistent(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No afters (locked in)" : null;
        game.addPlayersWhoHitPersistentNoAfter(player.getFaction());
        ReactionService.addReaction(event, game, player, message);
        MessageHelper.sendMessageToChannel(
                player.getCardsInfoThread(),
                "You hit \"No Afters\" for this entire agenda. If you change your mind, you can just play an \"After\" or remove this setting by hitting \"No Afters (For Now)\".");
    }

    @ButtonHandler("no_after")
    public static void noAfter(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No afters" : null;
        game.removePlayersWhoHitPersistentNoAfter(player.getFaction());
        ReactionService.addReaction(event, game, player, message);
    }

    @ButtonHandler("no_when")
    public static void noWhen(ButtonInteractionEvent event, Player player, Game game) {
        String message = game.isFowMode() ? "No whens" : null;
        game.removePlayersWhoHitPersistentNoWhen(player.getFaction());
        ReactionService.addReaction(event, game, player, message);
    }

    public static void playWhen(
            ButtonInteractionEvent event, Game game, Player player, MessageChannel mainGameChannel) {
        ReactionCheckService.clearAllReactions(event);
        ReactionService.addReaction(event, game, player, true, true, "is playing a \"when\".");
        List<Button> whenButtons = getWhenButtons(game);
        MessageHelper.sendMessageToChannelWithPersistentReacts(
                mainGameChannel, "Please indicate \"No Whens\" again.", game, whenButtons, GameMessageType.AGENDA_WHEN);
        List<Button> afterButtons = getAfterButtons(game);
        MessageHelper.sendMessageToChannelWithPersistentReacts(
                mainGameChannel,
                "Please indicate \"No Afters\" again.",
                game,
                afterButtons,
                GameMessageType.AGENDA_AFTER);
        ButtonHelper.deleteMessage(event);
    }

    public static void erasePreVoteDueToAfterPlay(Player player, Game game) {
        game.setStoredValue("preVoting" + player.getFaction(), "");
        player.resetSpentThings();
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("preVote", "Pre-Vote"));
        buttons.add(Buttons.blue("resolvePreassignment_Abstain On Agenda", "Pre-abstain"));
        buttons.add(Buttons.red("deleteButtons", "Don't do anything"));
        MessageHelper.sendMessageToChannelWithButtons(
                player.getCardsInfoThread(),
                player.toString()
                        + " due to the playing of an \"after\", your pre-vote was erased. You can use these buttons to pre-vote again.",
                buttons);
    }
}
