package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Veylor;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.emoji.FactionEmojis;
import ti4.service.leader.UnlockLeaderService;

@UtilityClass
public class VeylorLeadersHandler {
    private static final String UNLOCK = "unlockVeylorCommander";
    private static final String EXHAUST_PLANET = "exhaustVeylorPlanet_";
    private static final String DONE = "doneVeylorCommander";
    private static final String GAIN_HERO_CC = "gainVeylorHeroCC_";

    // Commander
    public static Button offerVeylorCommanderUnlock(Player player) {
        if (player == null || !player.hasLeader("veylorcommander") || player.hasLeaderUnlocked("veylorcommander")) {
            return null;
        }

        return Buttons.green(player.factionButtonChecker() + UNLOCK, "Unlock Commander", FactionEmojis.veylor);
    }

    @ButtonHandler(UNLOCK)
    public static void startVeylorCommanderUnlock(ButtonInteractionEvent event, Player player, Game game) {
        if (event == null
                || player == null
                || game == null
                || !player.hasLeader("veylorcommander")
                || player.hasLeaderUnlocked("veylorcommander")) {
            return;
        }

        List<Button> buttons = getExhaustPlanetButtons(player, game);
        buttons.add(Buttons.red(player.factionButtonChecker() + DONE, "Done Exhausting Planets"));
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                player.getRepresentationUnfogged()
                        + ", exhaust any planets you choose, then select **Done Exhausting Planets**. Reminder that you must exhaust all but 3 to unlock it.",
                buttons);
        ButtonHelper.deleteMessage(event);
    }

    @ButtonHandler(EXHAUST_PLANET)
    public static void exhaustPlanet(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        if (event == null || game == null || player == null || !player.hasLeader("veylorcommander")) {
            return;
        }

        String planet = buttonID.substring(EXHAUST_PLANET.length());
        if (!player.getReadiedPlanets().contains(planet)) {
            ButtonHelper.deleteTheOneButton(event);
            return;
        }

        player.exhaustPlanet(planet);
        ButtonHelper.deleteTheOneButton(event);
    }

    @ButtonHandler(DONE)
    public static void finishVeylorCommanderUnlock(ButtonInteractionEvent event, Game game, Player player) {
        if (event == null
                || game == null
                || player == null
                || !player.hasLeader("veylorcommander")
                || player.hasLeaderUnlocked("veylorcommander")) {
            return;
        }

        UnlockLeaderService.unlockLeader("veylorcommander", game, player);
        ButtonHelper.deleteMessage(event);
    }

    private static List<Button> getExhaustPlanetButtons(Player player, Game game) {
        List<String> planets = new ArrayList<>(player.getReadiedPlanets());
        planets.sort(String::compareTo);

        List<Button> buttons = new ArrayList<>();
        for (String planet : planets) {
            buttons.add(Buttons.green(
                    player.factionButtonChecker() + EXHAUST_PLANET + planet,
                    Helper.getPlanetRepresentation(planet, game)));
        }
        return buttons;
    }

    // Hero
    public static boolean hasHeroAdditionalAgenda(Game game, int aCount) {
        return aCount == 3
                && game.getRealPlayers().stream().anyMatch(player -> "yes"
                        .equals(game.getStoredValue("veylorHeroActive_" + player.getFaction())));
    }

    public static void resolveVeylorHeroLosingVote(Game game, String winner) {
        for (Player player : AgendaHelper.getLosingVoters(winner, game)) {
            if (!"yes".equals(game.getStoredValue("veylorHeroActive_" + player.getFaction()))) {
                continue;
            }

            List<Button> buttons = List.of(
                    Buttons.green(player.factionButtonChecker() + GAIN_HERO_CC + "tactic", "Gain 1 Tactic Token"),
                    Buttons.green(player.factionButtonChecker() + GAIN_HERO_CC + "fleet", "Gain 1 Fleet Token"),
                    Buttons.green(player.factionButtonChecker() + GAIN_HERO_CC + "strategy", "Gain 1 Strategy Token"));

            MessageHelper.sendMessageToChannelWithButtons(
                    player.getCorrectChannel(),
                    player.getRepresentationUnfogged()
                            + ", an agenda outcome you voted for was not resolved. Gain 1 command token from _Adoration of the Masses_.",
                    buttons);
        }
    }

    @ButtonHandler(GAIN_HERO_CC)
    public static void gainVeylorHeroCommandToken(ButtonInteractionEvent event, Player player, String buttonID) {
        String pool =
                switch (buttonID.substring(GAIN_HERO_CC.length())) {
                    case "tactic" -> {
                        player.setTacticalCC(player.getTacticalCC() + 1);
                        yield "tactic";
                    }
                    case "fleet" -> {
                        player.setFleetCC(player.getFleetCC() + 1);
                        yield "fleet";
                    }
                    case "strategy" -> {
                        player.setStrategicCC(player.getStrategicCC() + 1);
                        yield "strategy";
                    }
                    default -> {
                        ButtonHelper.deleteMessage(event);
                        yield null;
                    }
                };

        if (pool == null) {
            return;
        }

        MessageHelper.sendMessageToChannel(
                event.getMessageChannel(),
                player.getRepresentationUnfogged() + " gained 1 " + pool + " token from _Adoration of the Masses_.");

        ButtonHelper.deleteMessage(event);
    }
}
