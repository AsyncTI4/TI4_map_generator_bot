package ti4.service.breakthrough;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.helpers.RegexHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.LeaderModel;
import ti4.service.emoji.ExploreEmojis;
import ti4.service.emoji.TI4Emoji;
import ti4.service.leader.RefreshLeaderService;
import ti4.service.regex.RegexService;

@UtilityClass
public class ThundersParadoxService {

    private String paradoxRep() {
        return Mapper.getBreakthrough("nomadbt").getNameRepresentation();
    }

    @ButtonHandler("startThundersParadox")
    private void startThundersParadox(ButtonInteractionEvent event, Game game, Player player) {
        if (player.hasUnlockedBreakthrough("nomadbt")) {
            List<Button> buttons = new ArrayList<>();
            String buttonPrefix = player.finChecker() + "useThundersParadox_step2_";
            for (Leader leader : player.getLeaders()) {
                if (leader.isExhausted() || !leader.getType().equals("agent")) continue;

                String id = buttonPrefix + leader.getId();
                String label = leader.getLeaderModel().map(lm -> "Exhaust " + lm.getName()).orElse("Exhaust " + leader.getId());
                TI4Emoji emoji = leader.getLeaderModel().map(lm -> lm.getLeaderEmoji()).orElse(null);
                buttons.add(Buttons.blue(id, label, emoji));
            }
            if (player.hasRelicReady("titanprototype"))
                buttons.add(Buttons.blue(buttonPrefix + "titanprototype", "Exhaust JR-XS455-O", ExploreEmojis.Relic));
            if (player.hasRelicReady("absol_jr"))
                buttons.add(Buttons.blue(buttonPrefix + "absol_jr", "Exhaust JR-XS455-O", ExploreEmojis.Relic));

            MessageHelper.sendMessageToChannelWithEmbed(player.getCorrectChannel(), null, player.getBreakthroughModel().getRepresentationEmbed());
            String message = player.getRepresentation() + " is using their breakthrough, " + paradoxRep() + ". Use the buttons to choose one of your agents to exhaust:";
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
            ButtonHelper.deleteTheOneButton(event);

        } else {
            Player nomad = Helper.getPlayerFromUnlockedBreakthrough(game, "nomadbt");
            if (nomad != null) {
                String msg = "Attention " + nomad.getRepresentation() + ": " + player.getRepresentation(false, false) + " is requesting that you use your breakthrough" + paradoxRep() + ".";
                MessageHelper.sendMessageToChannel(nomad.getCorrectChannel(), msg);
            }
        }
    }

    @ButtonHandler("useThundersParadox_step2_")
    private void useThundersParadoxStep2(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "useThundersParadox_step2_" + RegexHelper.agentRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            String leaderID = matcher.group("agent");

            String message = player.getRepresentation(false, false) + " exhausted their agent ";
            if (player.hasUnexhaustedLeader(leaderID)) {
                Optional<Leader> leader = player.getLeaderByID(leaderID);
                Optional<LeaderModel> model = leader.flatMap(l -> l.getLeaderModel());
                leader.ifPresent(l -> l.setExhausted(true));
                message += model.map(lm -> lm.getNameRepresentation()).orElse("");
            } else if (player.hasRelicReady(leaderID)) {
                player.addExhaustedRelic(leaderID);
                message += ExploreEmojis.Relic + " " + Mapper.getRelic(leaderID).getName();
            }
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);

            String msg2 = player.getRepresentation() + " choose a player to ready one of their agents:";
            List<Button> buttons = new ArrayList<>();
            String buttonPrefix = player.finChecker() + "useThundersParadox_step3_";
            for (Player p : game.getRealPlayers()) {
                boolean found = false;
                for (Leader l : p.getLeaders()) {
                    if (l.getType().equals("agent")) {
                        found = true;
                    }
                }
                if (found || p.getExhaustedRelics().contains("titanprototype") || p.getExhaustedRelics().contains("absol_jr"))
                    buttons.add(Buttons.green(buttonPrefix + p.getFaction(), null, p.fogSafeEmoji()));
            }
            MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), msg2, buttons);
            ButtonHelper.deleteMessage(event);
        });
    }

    @ButtonHandler("useThundersParadox_step3_")
    private void useThundersParadoxStep3(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "useThundersParadox_step3_" + RegexHelper.factionRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Player p2 = game.getPlayerFromColorOrFaction(matcher.group("faction"));
            if (p2 != null) {
                String message = player.getRepresentation() + " choose an agent to refresh for " + p2.getRepresentation(false, false);

                List<Button> buttons = new ArrayList<>();
                String buttonPrefix = player.finChecker() + "useThundersParadox_step4_" + p2.getFaction() + "_";
                for (Leader leader : p2.getLeaders()) {
                    if (!leader.isExhausted() || !leader.getType().equals("agent")) continue;

                    String id = buttonPrefix + leader.getId();
                    String label = leader.getLeaderModel().map(lm -> "Ready " + lm.getName()).orElse("Ready " + leader.getId());
                    TI4Emoji emoji = leader.getLeaderModel().map(lm -> lm.getLeaderEmoji()).orElse(null);
                    buttons.add(Buttons.blue(id, label, emoji));
                }
                if (p2.getExhaustedRelics().contains("titanprototype"))
                    buttons.add(Buttons.blue(buttonPrefix + "titanprototype", "Ready JR-XS455-O", ExploreEmojis.Relic));
                if (p2.getExhaustedRelics().contains("absol_jr"))
                    buttons.add(Buttons.blue(buttonPrefix + "absol_jr", "Ready JR-XS455-O", ExploreEmojis.Relic));

                MessageHelper.sendMessageToChannelWithButtons(player.getCorrectChannel(), message, buttons);
                ButtonHelper.deleteMessage(event);
            }
        });
    }

    @ButtonHandler("useThundersParadox_step4_")
    private void useThundersParadoxStep4(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String regex = "useThundersParadox_step4_" + RegexHelper.factionRegex(game) + "_" + RegexHelper.agentRegex(game);
        RegexService.runMatcher(regex, buttonID, matcher -> {
            Player p2 = game.getPlayerFromColorOrFaction(matcher.group("faction"));
            String leaderID = matcher.group("agent");

            if (p2 != null) {
                String message = p2.getRepresentation() + " your agent, ";
                if (p2.hasLeader(leaderID) && p2.getLeader(leaderID).map(Leader::isExhausted).orElse(true)) {
                    Optional<Leader> leader = p2.getLeaderByID(leaderID);
                    Optional<LeaderModel> model = leader.flatMap(l -> l.getLeaderModel());
                    leader.ifPresent(l -> RefreshLeaderService.refreshLeader(p2, l, game));
                    message += model.map(lm -> lm.getNameRepresentation()).orElse("");

                } else if (p2.hasRelic(leaderID) && p2.getExhaustedRelics().contains(leaderID)) {
                    p2.removeExhaustedRelic(leaderID);
                    message += ExploreEmojis.Relic + " " + Mapper.getRelic(leaderID).getName();
                }
                message += ", has been readied by " + paradoxRep();
                MessageHelper.sendMessageToChannel(player.getCorrectChannel(), message);
                ButtonHelper.deleteMessage(event);
            }
        });
    }
}
