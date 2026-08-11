package ti4.service.relic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Oblivion.OblivionUnitHandler;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.thundersedge.DSHelperBreakthroughs;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.LeaderModel;
import ti4.model.Source.ComponentSource;
import ti4.model.TechnologyModel;
import ti4.service.leader.UnlockLeaderService;
import ti4.service.tech.PlayerTechService;

@UtilityClass
public class MutagenService {

    private static final Set<String> MUTAGENS =
            Set.of("mutagenhazardous", "mutagenindustrial", "mutagencultural", "mutagenfrontier");
    private static final String OPTIONS_KEY = "mutagenOptions_";
    private static final String REMAINING_KEY = "mutagenRemaining_";
    private static final String CHOOSE_MUTAGEN_OPTION = "chooseMutagenOption_";

    public static boolean isMutagen(String relicID) {
        return MUTAGENS.contains(relicID);
    }

    public static int getMutagenCount(Player player) {
        return (int) player.getRelics().stream().filter(MUTAGENS::contains).count();
    }

    public static void resolveMutagenPurge(
            ButtonInteractionEvent event, Game game, Player player, boolean volatileMutagenics) {
        game.removeStoredValue(OPTIONS_KEY + player.getFaction());
        game.removeStoredValue(REMAINING_KEY + player.getFaction());
        if (!volatileMutagenics && getMutagenCount(player) < 2) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + ", you need 2 _Mutagen_ cards to use this action.");
            return;
        }
        if (volatileMutagenics && !player.hasRelic("volatile_mutagenics")) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You do not have _Volatile Mutagenics_.");
            return;
        }

        List<String> options = drawMutagenOptions(game, player);
        if (options.isEmpty()) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing()
                            + ", no unused _Mutagen_ components are currently available to gain.");
            return;
        }

        if (!volatileMutagenics) {
            int purged = 0;
            for (String relicID : new ArrayList<>(player.getRelics())) {
                if (isMutagen(relicID)) {
                    player.removeRelic(relicID);
                    purged++;
                    if (purged == 2) {
                        break;
                    }
                }
            }
        }
        if (volatileMutagenics) {
            player.removeRelic("volatile_mutagenics");
            player.removeExhaustedRelic("volatile_mutagenics");
            DSHelperBreakthroughs.doLanefirBtCheck(game, player);
            OblivionUnitHandler.doOblivionMechCheck(game, player);
        }

        game.setStoredValue(OPTIONS_KEY + player.getFaction(), String.join(",", options));
        game.setStoredValue(REMAINING_KEY + player.getFaction(), volatileMutagenics ? "2" : "1");
        MessageHelper.sendMessageToChannelWithEmbedsAndButtons(
                player.getCorrectChannel(),
                player.getRepresentationNoPing() + ", choose " + (volatileMutagenics ? "2 components" : "1 component")
                        + (volatileMutagenics
                                ? " to gain from _Volatile Mutagenics_."
                                : " to gain from your _Mutagen_ cards."),
                getMutagenOptionEmbeds(options),
                getMutagenOptionButtons(player, options));
    }

    @ButtonHandler(CHOOSE_MUTAGEN_OPTION)
    public static void chooseMutagenOption(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        String choice = buttonID.replace(CHOOSE_MUTAGEN_OPTION, "");
        List<String> options = new ArrayList<>(
                List.of(game.getStoredValue(OPTIONS_KEY + player.getFaction()).split(",")));
        int remaining;
        try {
            remaining = Integer.parseInt(game.getStoredValue(REMAINING_KEY + player.getFaction()));
        } catch (NumberFormatException e) {
            remaining = 0;
        }
        if (remaining < 1 || !options.remove(choice)) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + ", that _Mutagen_ component is no longer available.");
            return;
        }
        if (!isAvailable(game, player, choice)) {
            if (options.isEmpty()) {
                game.setStoredValue(OPTIONS_KEY + player.getFaction(), "");
                game.setStoredValue(REMAINING_KEY + player.getFaction(), "");
                ButtonHelper.deleteMessage(event);
            } else {
                game.setStoredValue(OPTIONS_KEY + player.getFaction(), String.join(",", options));
                MessageHelper.editMessageWithButtons(
                        event,
                        player.getRepresentationNoPing() + ", choose " + remaining + " more component"
                                + (remaining == 1 ? "" : "s") + " to gain from _Volatile Mutagenics_.",
                        getMutagenOptionButtons(player, options));
            }
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(),
                    player.getRepresentationNoPing() + ", that _Mutagen_ component is no longer available.");
            return;
        }

        String[] choiceParts = choice.split("\\|", 2);
        if (choiceParts.length != 2) {
            return;
        }
        String type = choiceParts[0];
        String id = choiceParts[1];
        switch (type) {
            case "tech" -> {
                player.addFactionTech(id);
                PlayerTechService.addTech(event, game, player, id);
                MessageHelper.sendMessageToChannelWithEmbed(
                        event.getMessageChannel(),
                        player.getRepresentationNoPing() + " gained a faction technology from _Mutagen_ cards.",
                        Mapper.getTech(id).getRepresentationEmbed());
            }
            case "agent" -> {
                player.addLeader(id);
                MessageHelper.sendMessageToChannelWithEmbed(
                        event.getMessageChannel(),
                        player.getRepresentationNoPing() + " gained an agent from _Mutagen_ cards.",
                        Mapper.getLeader(id).getRepresentationEmbed());
            }
            case "commander" -> {
                player.addLeader(id);
                UnlockLeaderService.unlockLeader(id, game, player);
            }
            default -> {
                return;
            }
        }

        remaining--;
        if (remaining == 0 || options.isEmpty()) {
            game.setStoredValue(OPTIONS_KEY + player.getFaction(), "");
            game.setStoredValue(REMAINING_KEY + player.getFaction(), "");
            ButtonHelper.deleteMessage(event);
            return;
        }
        game.setStoredValue(OPTIONS_KEY + player.getFaction(), String.join(",", options));
        game.setStoredValue(REMAINING_KEY + player.getFaction(), Integer.toString(remaining));
        MessageHelper.editMessageWithButtons(
                event,
                player.getRepresentationNoPing() + ", choose " + remaining + " more component"
                        + (remaining == 1 ? "" : "s") + " to gain from _Volatile Mutagenics_.",
                getMutagenOptionButtons(player, options));
    }

    private static List<String> drawMutagenOptions(Game game, Player player) {
        List<String> options = new ArrayList<>();
        List<FactionModel> factions = Mapper.getFactionsValues().stream()
                .filter(faction -> faction.getSource().isOfficial()
                        || (game.isDiscordantStarsMode() && faction.getSource().isDs())
                        || (game.isBlueReverieMode() && faction.getSource().isBr())
                        || faction.getSource() == ComponentSource.theodisi)
                .toList();
        List<String> factionTechs = factions.stream()
                .filter(faction -> !game.getFactions().contains(faction.getAlias()))
                .flatMap(faction -> faction.getFactionTech().stream())
                .filter(Mapper::isValidTech)
                .filter(techID -> {
                    TechnologyModel tech = Mapper.getTech(techID);
                    return tech.isFactionTech()
                            && !tech.isUnitUpgrade()
                            && game.getRealPlayers().stream().noneMatch(otherPlayer -> otherPlayer.hasTech(techID));
                })
                .distinct()
                .toList();
        String factionTech = getRandom(factionTechs);
        if (factionTech != null) {
            options.add("tech|" + factionTech);
        }

        List<String> agents = getEligibleLeaders(game, factions, "agent");
        String agent = getRandom(agents);
        if (agent != null) {
            options.add("agent|" + agent);
        }
        List<String> commanders = getEligibleLeaders(game, factions, "commander");
        String commander = getRandom(commanders);
        if (commander != null) {
            options.add("commander|" + commander);
        }
        return options;
    }

    private static List<String> getEligibleLeaders(Game game, List<FactionModel> factions, String type) {
        return factions.stream()
                .filter(faction -> !game.getFactions().contains(faction.getAlias()))
                .flatMap(faction -> faction.getLeaders().stream())
                .filter(Mapper::isValidLeader)
                .filter(leaderID -> {
                    LeaderModel leader = Mapper.getLeader(leaderID);
                    return type.equalsIgnoreCase(leader.getType())
                            && !Constants.CALL_OF_THE_HAUNTED_LEADERS.contains(leaderID)
                            && Helper.getPlayerFromLeader(game, leaderID) == null
                            && !"unknown".equalsIgnoreCase(leader.getAbilityText())
                            && !game.getStoredValue("agent".equals(type) ? "fakeAgents" : "mercCommander")
                                    .contains(leaderID)
                            && !("commander".equals(type)
                                    && leader.getAbilityText().toLowerCase().contains("fracture"));
                })
                .distinct()
                .toList();
    }

    private static boolean isAvailable(Game game, Player player, String choice) {
        String[] choiceParts = choice.split("\\|", 2);
        if (choiceParts.length != 2) {
            return false;
        }
        String id = choiceParts[1];
        return switch (choiceParts[0]) {
            case "tech" ->
                Mapper.isValidTech(id)
                        && !player.hasTech(id)
                        && game.getRealPlayers().stream().noneMatch(otherPlayer -> otherPlayer.hasTech(id));
            case "agent", "commander" -> Mapper.isValidLeader(id) && Helper.getPlayerFromLeader(game, id) == null;
            default -> false;
        };
    }

    private static List<Button> getMutagenOptionButtons(Player player, List<String> options) {
        return options.stream()
                .map(choice -> {
                    String[] choiceParts = choice.split("\\|", 2);
                    String id = choiceParts[1];
                    String name = "tech".equals(choiceParts[0])
                            ? Mapper.getTech(id).getName()
                            : Mapper.getLeader(id).getName();
                    return Buttons.green(player.factionButtonChecker() + CHOOSE_MUTAGEN_OPTION + choice, name);
                })
                .toList();
    }

    private static List<MessageEmbed> getMutagenOptionEmbeds(List<String> options) {
        return options.stream()
                .map(choice -> {
                    String[] choiceParts = choice.split("\\|", 2);
                    return "tech".equals(choiceParts[0])
                            ? Mapper.getTech(choiceParts[1]).getRepresentationEmbed()
                            : Mapper.getLeader(choiceParts[1]).getRepresentationEmbed();
                })
                .toList();
    }

    private static String getRandom(List<String> choices) {
        if (choices.isEmpty()) {
            return null;
        }
        List<String> shuffledChoices = new ArrayList<>(choices);
        Collections.shuffle(shuffledChoices);
        return shuffledChoices.getFirst();
    }
}
