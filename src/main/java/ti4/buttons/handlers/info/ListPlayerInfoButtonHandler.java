package ti4.buttons.handlers.info;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.commands2.player.Stats;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.info.ListPlayerInfoService;
import ti4.service.info.UnitInfoService;

@UtilityClass
class ListPlayerInfoButtonHandler {

    @ButtonHandler("gameInfoButtons")
    public static void offerInfoButtons(ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Buttons.green("offerInfoButtonStep2_allFaction", "All Info On A Faction"));
        buttons.add(Buttons.green("offerInfoButtonStep2_objective", "Objective Info"));
        buttons.add(Buttons.green("offerInfoButtonStep2_abilities", "Ability Info"));
        buttons.add(Buttons.green("offerInfoButtonStep2_stats", "Player Stats Info"));
        buttons.add(Buttons.green("offerInfoButtonStep2_agent", "Agent Info"));
        buttons.add(Buttons.green("offerInfoButtonStep2_commander", "Commander Info"));
        buttons.add(Buttons.green("offerInfoButtonStep2_hero", "Hero Info"));
        buttons.add(Buttons.green("offerInfoButtonStep2_relic", "Relic Info"));
        buttons.add(Buttons.green("offerInfoButtonStep2_planet", "Planet Info"));
        buttons.add(Buttons.green("offerInfoButtonStep2_units", "Special Units"));
        buttons.add(Buttons.green("offerInfoButtonStep2_pn", "Faction PN"));
        buttons.add(Buttons.green("offerInfoButtonStep2_tech", "Researched Tech"));
        buttons.add(Buttons.green("offerInfoButtonStep2_ftech", "Faction Tech"));
        buttons.add(Buttons.REFRESH_INFO);
        String msg = "Select the category you'd like more info on. You will then be able to select either a specific faction's info you want, or every factions";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("offerInfoButtonStep2_")
    public static void resolveOfferInfoButtonStep2(ButtonInteractionEvent event, String buttonID, Game game) {
        String category = buttonID.split("_")[1];
        List<Button> buttons = new ArrayList<>();
        String msg = "";
        if (category.equalsIgnoreCase("objective")) {
            buttons.add(Buttons.green("showObjInfo_both", "All Objectives in Game"));
            buttons.add(Buttons.blue("showObjInfo_1", "All Stage 1s Possible"));
            buttons.add(Buttons.blue("showObjInfo_2", "All Stage 2s Possible"));
        } else {
            for (Player p2 : game.getRealPlayers()) {
                Button button = Buttons.gray("offerInfoButtonStep3_" + category + "_" + p2.getFaction(), " ");
                String factionEmojiString = p2.getFactionEmoji();
                button = button.withEmoji(Emoji.fromFormatted(factionEmojiString));
                buttons.add(button);
            }
            buttons.add(Buttons.green("offerInfoButtonStep3_" + category + "_all", "All Factions"));
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), msg, buttons);
    }

    @ButtonHandler("offerInfoButtonStep3_")
    public static void resolveOfferInfoButtonStep3(ButtonInteractionEvent event, String buttonID, Game game, Player player) {
        String category = buttonID.split("_")[1];
        String faction = buttonID.split("_")[2];
        List<MessageEmbed> messageEmbeds = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (Player p2 : game.getRealPlayers()) {
            if (!"all".equals(faction) && !faction.equalsIgnoreCase(p2.getFaction())) {
                continue;
            }
            switch (category) {
                case "allFaction" -> {
                    sb.append(new Stats().getPlayersCurrentStatsText(p2, game));
                    for (String ability : p2.getAbilities()) {
                        messageEmbeds.add(Mapper.getAbility(ability).getRepresentationEmbed());
                    }
                    for (Leader lead : p2.getLeaders()) {
                        messageEmbeds.add(lead.getLeaderModel().get().getRepresentationEmbed(true, true, true, true));
                    }
                    for (String tech : p2.getFactionTechs()) {
                        messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                    }
                    for (String unit : p2.getUnitsOwned()) {
                        if (unit.contains("_")) {
                            messageEmbeds.add(Mapper.getUnit(unit).getRepresentationEmbed());
                        }
                    }
                    for (String relic : p2.getRelics()) {
                        messageEmbeds.add(Mapper.getRelic(relic).getRepresentationEmbed());
                    }
                    for (String planet : p2.getPlanets()) {
                        sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)).append("\n");
                    }
                    for (String tech : p2.getTechs()) {
                        messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                    }
                    for (String pn : p2.getPromissoryNotesOwned()) {
                        if (!pn.contains(p2.getColor() + "_")) {
                            messageEmbeds.add(Mapper.getPromissoryNote(pn).getRepresentationEmbed());
                        }
                    }
                }
                case "abilities" -> {
                    for (String ability : p2.getAbilities()) {
                        messageEmbeds.add(Mapper.getAbility(ability).getRepresentationEmbed());
                    }
                }
                case "stats" -> sb.append(new Stats().getPlayersCurrentStatsText(p2, game));
                case "relic" -> {
                    for (String relic : p2.getRelics()) {
                        messageEmbeds.add(Mapper.getRelic(relic).getRepresentationEmbed());
                    }
                }
                case "ftech" -> {
                    for (String tech : p2.getFactionTechs()) {
                        messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                    }
                }
                case "tech" -> {
                    for (String tech : p2.getTechs()) {
                        messageEmbeds.add(Mapper.getTech(tech).getRepresentationEmbed());
                    }
                }
                case "planet" -> {
                    for (String planet : p2.getPlanets()) {
                        sb.append(Helper.getPlanetRepresentationPlusEmojiPlusResourceInfluence(planet, game)).append("\n");
                    }
                }
                case "pn" -> {
                    for (String pn : p2.getPromissoryNotesOwned()) {
                        if (!pn.contains(p2.getColor() + "_")) {
                            messageEmbeds.add(Mapper.getPromissoryNote(pn).getRepresentationEmbed());
                        }
                    }
                }
                case "agent", "commander", "hero" -> {
                    for (Leader lead : p2.getLeaders()) {
                        if (lead.getId().contains(category)) {
                            messageEmbeds.add(lead.getLeaderModel().get().getRepresentationEmbed(true, true, true, true));
                        }
                    }
                }
                case "units" -> messageEmbeds.addAll(UnitInfoService.getUnitMessageEmbeds(p2, false));
            }
        }

        MessageHelper.sendMessageToChannelWithEmbeds(player.getCardsInfoThread(), sb.toString(), messageEmbeds);
        event.getMessage().delete().queue();
    }

    @ButtonHandler("showObjInfo_")
    public static void showObjInfo(ButtonInteractionEvent event, String buttonID, Game game) {
        String extent = buttonID.split("_")[1];
        if (extent.equalsIgnoreCase("both")) {
            ListPlayerInfoService.displayerScoringProgression(game, true, event.getMessageChannel(), "both");
        } else {
            ListPlayerInfoService.displayerScoringProgression(game, false, event.getMessageChannel(), extent);
            event.getMessage().delete().queue();
        }
    }
}
