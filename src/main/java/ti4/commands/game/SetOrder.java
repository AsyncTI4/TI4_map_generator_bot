package ti4.commands.game;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class SetOrder extends GameStateSubcommand {

    public SetOrder() {
        super(Constants.SET_ORDER, "Set player order in game", true, false);
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER1, "Player1").setRequired(true));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER2, "Player2"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER3, "Player3"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER4, "Player4"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER5, "Player5"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER6, "Player6"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER7, "Player7"));
        addOptions(new OptionData(OptionType.USER, Constants.PLAYER8, "Player8"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<User> users = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            User member = event.getOption("player" + i, null, OptionMapping::getAsUser);
            if (member == null) {
                break;
            }
            users.add(member);
        }
        setPlayerOrder(event, getGame(), users);
    }

    public static void setPlayerOrder(GenericInteractionCreateEvent event, Game game, List<User> users) {
        Map<String, Player> newPlayerOrder = new LinkedHashMap<>();
        Map<String, Player> players = new LinkedHashMap<>(game.getPlayers());
        Map<String, Player> playersBackup = new LinkedHashMap<>(game.getPlayers());
        try {
            for (User user : users) {
                setPlayerOrder(newPlayerOrder, players, user);
            }
            if (!players.isEmpty()) {
                newPlayerOrder.putAll(players);
            }
            game.setPlayers(newPlayerOrder);
        } catch (Exception e) {
            game.setPlayers(playersBackup);
        }
        StringBuilder sb = new StringBuilder("Player order set:");
        for (Player player : game.getPlayers().values()) {
            sb.append("\n> ").append(player.getRepresentationNoPing());
            if (player.isSpeaker()) {
                sb.append(Emojis.SpeakerToken);
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    public static void setPlayerOrder(Map<String, Player> newPlayerOrder, Map<String, Player> players, User user) {
        if (user != null) {
            String id = user.getId();
            Player player = players.get(id);
            if (player != null) {
                newPlayerOrder.put(id, player);
                players.remove(id);
            }
        }
    }

    public void setPlayerOrder(Map<String, Player> newPlayerOrder, Map<String, Player> players, Player player) {
        if (player != null) {
            newPlayerOrder.put(player.getUserID(), player);
            players.remove(player.getUserID());
        }

    }
}
