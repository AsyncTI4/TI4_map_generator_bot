package ti4.buttons.handlers.agenda;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.AgendaModel;
import ti4.model.StrategyCardSetModel;
import ti4.model.TechnologyModel;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.ColorEmojis;
import ti4.service.emoji.PlanetEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.tech.ListTechService;

@UtilityClass
public class VoteButtonHandler {

    @ButtonHandler("erasePreVote")
    public static void erasePreVote(ButtonInteractionEvent event, Player player, Game game) {
        game.setStoredValue("preVoting" + player.getFaction(), "");
        player.resetSpentThings();
        event.getMessage().delete().queue();
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("preVote", "Pre-Vote"));
        buttons.add(Buttons.blue("resolvePreassignment_Abstain On Agenda", "Pre-abstain"));
        buttons.add(Buttons.red("deleteButtons", "Don't do anything"));
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), "Erased the pre-vote", buttons);
    }

    @ButtonHandler("preVote")
    public static void preVote(ButtonInteractionEvent event, Player player, Game game) {
        game.setStoredValue("preVoting" + player.getFaction(), "0");
        firstStepOfVoting(game, event, player);
    }

    @ButtonHandler("vote")
    public static void firstStepOfVoting(Game game, ButtonInteractionEvent event, Player player) {
        String pfaction2 = null;
        if (player != null) {
            pfaction2 = player.getFaction();
        }
        if (pfaction2 != null) {
            String voteMessage = player.getRepresentation() + " Chose to Vote. Click buttons for which outcome to vote for.";
            String agendaDetails = game.getCurrentAgendaInfo().split("_")[1];
            List<Button> outcomeActionRow;
            if (agendaDetails.contains("For") || agendaDetails.contains("for")) {
                outcomeActionRow = getForAgainstOutcomeButtons(game, null, "outcome", game.getCurrentAgendaInfo().split("_")[2], player);
            } else if (agendaDetails.contains("Player") || agendaDetails.contains("player")) {
                outcomeActionRow = getPlayerOutcomeButtons(game, null, "outcome", null);
            } else if (agendaDetails.contains("Planet") || agendaDetails.contains("planet")) {
                voteMessage = "Chose to Vote. Too many planets in the game to represent all as buttons. Click buttons for which player owns the planet you wish to elect.";
                outcomeActionRow = getPlayerOutcomeButtons(game, null, "planetOutcomes",
                    null);
            } else if (agendaDetails.contains("Secret") || agendaDetails.contains("secret")) {
                outcomeActionRow = getSecretOutcomeButtons(game, null, "outcome");
            } else if (agendaDetails.contains("Strategy") || agendaDetails.contains("strategy")) {
                outcomeActionRow = getStrategyOutcomeButtons(game, null, "outcome");
            } else if (agendaDetails.contains("unit upgrade")) {
                outcomeActionRow = getUnitUpgradeOutcomeButtons(game, null, "outcome");
            } else if (agendaDetails.contains("Unit") || agendaDetails.contains("unit")) {
                outcomeActionRow = getUnitOutcomeButtons(game, null, "outcome");
            } else {
                outcomeActionRow = getLawOutcomeButtons(game, null, "outcome");
            }
            ButtonHelper.deleteMessage(event);
            MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), AgendaHelper.getSummaryOfVotes(game, true) + "\n\n" + voteMessage,
                outcomeActionRow);
        }
    }

    @ButtonHandler("planetOutcomes_")
    public static void planetOutcomes(ButtonInteractionEvent event, String buttonID, Game game) {
        String factionOrColor = buttonID.substring(buttonID.indexOf("_") + 1);
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Chose to vote for one of " + factionOrColor
            + "'s planets. Click buttons for which outcome to vote for.";
        List<Button> outcomeActionRow;
        outcomeActionRow = getPlanetOutcomeButtons(planetOwner, game, "outcome", null);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler("tiedPlanets_")
    public static void tiedPlanets(ButtonInteractionEvent event, String buttonID, Game game) {
        buttonID = buttonID.replace("tiedPlanets_", "");
        buttonID = buttonID.replace("resolveAgendaVote_outcomeTie*_", "");
        String factionOrColor = buttonID;
        Player planetOwner = game.getPlayerFromColorOrFaction(factionOrColor);
        String voteMessage = "Chose to break tie for one of " + factionOrColor
            + "'s planets. Use buttons to select which one.";
        List<Button> outcomeActionRow;
        outcomeActionRow = getPlanetOutcomeButtons(planetOwner, game,
            "resolveAgendaVote_outcomeTie*", null);
        MessageHelper.sendMessageToChannelWithButtons(event.getChannel(), voteMessage, outcomeActionRow);
        ButtonHelper.deleteMessage(event);
    }

    public static List<Button> getLawOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> lawButtons = new ArrayList<>();
        for (Map.Entry<String, Integer> law : game.getLaws().entrySet()) {
            String lawName = Mapper.getAgendaTitleNoCap(law.getKey());
            Button button;
            if (rider == null) {
                button = Buttons.blue(prefix + "_" + law.getKey(), lawName);
            } else {
                button = Buttons.blue(prefix + "rider_law;" + law.getKey() + "_" + rider, lawName);
            }
            lawButtons.add(button);
        }
        return lawButtons;
    }

    public static List<Button> getForAgainstOutcomeButtons(Game game, String rider, String prefix, String agendaID, Player player) {
        List<Button> voteButtons = new ArrayList<>();
        Button buttonFor;
        Button buttonAgainst;
        String finChecker = "";
        if (player != null) {
            finChecker = player.getFinsFactionCheckerPrefix();
        }
        Map<String, Integer> discardAgendas = game.getDiscardAgendas();
        Integer agendaInt = null;
        String forEmojiString = "👍";
        String againstEmojiString = "👎";
        try {
            agendaInt = Integer.valueOf(agendaID);
        } catch (NumberFormatException e) {}
        if (agendaInt != null) {
            String agendaAlias = "";
            for (Map.Entry<String, Integer> agendas : discardAgendas.entrySet()) {
                if (agendas.getValue().equals(agendaInt)) {
                    agendaAlias = agendas.getKey();
                    break;
                }
            }
            AgendaModel agendaDetails = Mapper.getAgenda(agendaAlias);
            if (agendaDetails != null) {
                forEmojiString = agendaDetails.getForEmoji();
                againstEmojiString = agendaDetails.getAgainstEmoji();
            }
            for (TI4Emoji emoji : TI4Emoji.allEmojiEnums()) {
                if (forEmojiString.equals(emoji.name())) {
                    forEmojiString = emoji.toString();
                    break;
                }
            }
            for (TI4Emoji emoji : TI4Emoji.allEmojiEnums()) {
                if (againstEmojiString.equals(emoji.name())) {
                    againstEmojiString = emoji.toString();
                    break;
                }
            }
        }
        if (rider == null) {
            buttonFor = Buttons.green(finChecker + prefix + "_for", "For");
            buttonAgainst = Buttons.red(finChecker + prefix + "_against", "Against");
        } else {
            buttonFor = Buttons.green(finChecker + prefix + "rider_fa;for_" + rider, "For");
            buttonAgainst = Buttons.red(finChecker + prefix + "rider_fa;against_" + rider, "Against");
        }

        buttonFor = buttonFor.withEmoji(Emoji.fromFormatted(forEmojiString));
        buttonAgainst = buttonAgainst.withEmoji(Emoji.fromFormatted(againstEmojiString));

        voteButtons.add(buttonFor);
        voteButtons.add(buttonAgainst);
        return voteButtons;
    }

    public static List<Button> getSecretOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> secretButtons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            for (Map.Entry<String, Integer> so : player.getSecretsScored().entrySet()) {
                Button button;
                String soName = Mapper.getSecretObjectivesJustNames().get(so.getKey());
                if (rider == null) {
                    button = Buttons.blue(prefix + "_" + so.getKey(), soName);
                } else {
                    button = Buttons.blue(prefix + "rider_so;" + so.getKey() + "_" + rider, soName);
                }
                if (!game.isFowMode()) {
                    String colorEmojiString = ColorEmojis.getColorEmoji(player.getColor()).toString();
                    button = button.withEmoji(Emoji.fromFormatted(colorEmojiString));
                }
                secretButtons.add(button);
            }
        }
        return secretButtons;
    }

    public static List<Button> getUnitUpgradeOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (Player player : game.getPlayers().values()) {
            for (TechnologyModel tech : ListTechService.getAllNonFactionUnitUpgradeTech(game, player)) {
                Button button;
                if (rider == null) {
                    button = Buttons.blue(prefix + "_" + tech.getAlias(), tech.getName());
                } else {
                    button = Buttons.blue(prefix + "rider_so;" + tech.getAlias() + "_" + rider, tech.getName());
                }
                buttons.add(button);
            }
        }
        return buttons;
    }

    public static List<Button> getUnitOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (TechnologyModel tech : ListTechService.getAllNonFactionUnitUpgradeTech(game)) {
            Button button;
            if (rider == null) {
                button = Buttons.blue(prefix + "_" + tech.getAlias(), tech.getName());
            } else {
                button = Buttons.blue(prefix + "rider_so;" + tech.getAlias() + "_" + rider, tech.getName());
            }
            buttons.add(button);
        }
        return buttons;
    }

    public static List<Button> getStrategyOutcomeButtons(Game game, String rider, String prefix) {
        List<Button> strategyButtons = new ArrayList<>();
        StrategyCardSetModel stratCards = game.getStrategyCardSet();
        for (int x = 1; x < 9; x++) {
            Button button;
            TI4Emoji scEmoji = CardEmojis.getSCBackFromInteger(x);
            if (rider == null) {
                button = Buttons.blue(prefix + "_" + x, stratCards.getSCName(x), scEmoji);
            } else {
                button = Buttons.blue(prefix + "rider_sc;" + x + "_" + rider, stratCards.getSCName(x), scEmoji);
            }
            strategyButtons.add(button);
        }
        return strategyButtons;
    }

    public static List<Button> getPlanetOutcomeButtons(Player player, Game game, String prefix, String rider) {
        List<Button> planetOutcomeButtons = new ArrayList<>();
        List<String> planets = new ArrayList<>(player.getPlanets());
        for (String planet : planets) {
            Button button;
            TI4Emoji planetEmoji = PlanetEmojis.getPlanetEmoji(planet);
            if (rider == null) {
                button = Buttons.blue(prefix + "_" + planet, Helper.getPlanetRepresentation(planet, game), planetEmoji);
            } else {
                button = Buttons.blue(prefix + "rider_planet;" + planet + "_" + rider,
                    Helper.getPlanetRepresentation(planet, game), planetEmoji);
            }
            planetOutcomeButtons.add(button);
        }
        return planetOutcomeButtons;
    }

    public static List<Button> getPlayerOutcomeButtons(Game game, String rider, String prefix, String planetRes) {
        List<Button> playerOutcomeButtons = new ArrayList<>();

        for (Player player : game.getRealPlayers()) {
            String faction = player.getFaction();
            Button button;
            if (!game.isFowMode() && !faction.contains("franken")) {
                if (rider != null) {
                    if (planetRes != null) {
                        button = Buttons.blue(prefix + planetRes + "_" + faction + "_" + rider, StringUtils.capitalize(faction));
                    } else {
                        button = Buttons.blue(prefix + "rider_player;" + faction + "_" + rider, StringUtils.capitalize(faction));
                    }
                } else {
                    button = Buttons.blue(prefix + "_" + faction, StringUtils.capitalize(faction));
                }
                String colorEmojiString = ColorEmojis.getColorEmoji(player.getColor()).toString();
                button = button.withEmoji(Emoji.fromFormatted(colorEmojiString));
            } else {
                if (rider != null) {
                    if (planetRes != null) {
                        button = Buttons.blue(planetRes + "_" + player.getColor() + "_" + rider, player.getColor());
                    } else {
                        button = Buttons.blue(prefix + "rider_player;" + player.getColor() + "_" + rider,
                            player.getColor());
                    }
                } else {
                    button = Buttons.blue(prefix + "_" + player.getColor(), player.getColor());
                }
            }
            playerOutcomeButtons.add(button);
        }
        return playerOutcomeButtons;
    }
}
