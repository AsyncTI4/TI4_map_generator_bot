package ti4.service.game;

import java.util.ArrayList;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.buttons.Buttons;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.omegaPhase.OmegaPhaseModStatusHelper;
import ti4.helpers.omegaPhase.VoiceOfTheCouncilHelper;
import ti4.image.Mapper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.emoji.TI4Emoji;

@UtilityClass
public class HomebrewService {

    public enum Homebrew {
        HB444("4/4/4", "4 secrets, 4 stage 1s, 4 stage 2s, 12 VP", null), HB456("4/5/6", "4 Secrets, 5 stage 1s, 6 stage2 (revealed 2 at a time), 14 VP", null), HBABSOLRELICSAGENDAS("Absol Relics/Agendas", "Use Absol Relics and Agendas", SourceEmojis.Absol), HBABSOLTECHSMECHS("Absol Techs/Mechs", "Use Absol Techs and Mechs", SourceEmojis.Absol), HBDSFACTIONS("DS Factions", "Discordant Stars Factions", SourceEmojis.DiscordantStars), HBDSEXPLORES("US Explores/Relics/ACs",
            "Uncharted Space Explores, Relics and Action Cards",
            SourceEmojis.UnchartedSpace), HBACDECK2("AC2 Deck", "Action Cards Deck 2", SourceEmojis.ActionDeck2), HBREDTAPE("Red Tape", "Red Tape mode", null), HBIGNISAURORA("Ignis Aurora", "Ignis Aurora decks for SC/agendas/techs/events/relics", null), HBREMOVESFTT("No Supports", "Remove Support for the Thrones", null), HBHBSC("Homebrew SCs", "Indicate game uses homebrew Strategy Cards", CardEmojis.SCBackBlank), HBOMEGAPHASE("Omega Phase", "Enable Omega Phase homebrew mode", null);

        final String name;
        final String description;
        final TI4Emoji emoji;

        Homebrew(String name, String description, TI4Emoji emoji) {
            this.name = name;
            this.description = description;
            this.emoji = emoji;
        }
    }

    @ButtonHandler("offerGameHomebrewButtons")
    public static void offerGameHomebrewButtons(MessageChannel channel) {
        List<Button> homebrewButtons = new ArrayList<>();
        homebrewButtons.add(Buttons.green("getHomebrewButtons", "Yes Homebrew"));
        homebrewButtons.add(Buttons.red("deleteButtons", "No Homebrew"));
        MessageHelper.sendMessageToChannelWithButtonsAndNoUndo(channel, "If you plan to have a supported homebrew mode in this game, please indicate " +
            "so with these buttons. 4/4/4 is a type of homebrew btw", homebrewButtons);
    }

    @ButtonHandler("getHomebrewButtons")
    public static void offerHomeBrewButtons(Game game, ButtonInteractionEvent event) {
        List<Button> buttons = new ArrayList<>();

        StringBuilder sb = new StringBuilder("### Choose the homebrew you'd like in the game\n");
        for (Homebrew hb : Homebrew.values()) {
            sb.append("**").append(hb.name).append("**: ").append(hb.description).append("\n");
            buttons.add(Buttons.green("setupHomebrew_" + hb, hb.name));
        }
        buttons.add(Buttons.red("setupHomebrewNone", "Remove All Homebrews"));
        buttons.add(Buttons.DONE_DELETE_BUTTONS);

        ButtonHelper.deleteMessage(event);
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), sb.toString(), buttons);
    }

    @ButtonHandler("setupHomebrewNone")
    public static void removeHomebrew(Game game, ButtonInteractionEvent event) {
        game.setHomebrewSCMode(false);
        game.setRedTapeMode(false);
        game.setDiscordantStarsMode(false);
        game.setAbsolMode(false);
        game.setOmegaPhaseMode(false);
        game.setStoredValue("homebrewMode", "");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Set all homebrew options off. You need manually check and fix decks, VPs, objectives etc. that might've been set.");
    }

    @ButtonHandler("setupHomebrew_")
    public static void setUpHomebrew(Game game, ButtonInteractionEvent event, String buttonID) {
        ButtonHelper.deleteTheOneButton(event);
        game.setHomebrew(true);

        Homebrew type = Homebrew.valueOf(buttonID.split("_")[1]);
        switch (type) {
            case HB444 -> {
                game.setMaxSOCountPerPlayer(4);
                game.setUpPeakableObjectives(4, 1);
                game.setUpPeakableObjectives(4, 2);
                game.setVp(12);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set up 4/4/4.");
            }
            case HB456 -> {
                game.setMaxSOCountPerPlayer(4);
                game.setUpPeakableObjectives(5, 1);
                game.setUpPeakableObjectives(6, 2);
                game.setVp(14);
                game.setStoredValue("homebrewMode", "456");
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set up 4/5/6/14VP.");
            }
            case HBREMOVESFTT -> {
                for (Player p2 : game.getRealPlayers()) {
                    p2.removeOwnedPromissoryNoteByID(p2.getColor() + "_sftt");
                    p2.removePromissoryNote(p2.getColor() + "_sftt");
                }
                game.setStoredValue("removeSupports", "true");
            }
            case HBABSOLRELICSAGENDAS -> {
                game.setAbsolMode(true);
                game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_absol"));
                if (game.isDiscordantStarsMode() && game.getRelicDeckID().contains("ds")) {
                    game.validateAndSetRelicDeck(Mapper.getDeck("relics_absol_ds"));
                } else {
                    game.validateAndSetRelicDeck(Mapper.getDeck("relics_absol"));
                }
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Set the relics and agendas to Absol stuff");
            }
            case HBIGNISAURORA -> {
                game.validateAndSetAgendaDeck(event, Mapper.getDeck("agendas_baldrick"));
                game.validateAndSetRelicDeck(Mapper.getDeck("relics_baldrick"));
                game.setTechnologyDeckID("techs_baldrick");
                game.setStrategyCardSet("ignis_aurora");
                game.setEventDeckID("events_baldrick");
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Set the stuff (Relic, Agenda, SCs, Tech, Event) to Ignis Aurora stuff");
            }
            case HBABSOLTECHSMECHS -> {
                game.setAbsolMode(true);
                if (game.isDiscordantStarsMode()) {
                    game.setTechnologyDeckID("techs_ds_absol");
                } else {
                    game.setTechnologyDeckID("techs_absol");
                }
                game.swapInVariantUnits("absol");
                game.swapInVariantTechs();
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set the techs & mechs to Absol stuff.");
            }
            case HBDSEXPLORES -> {
                game.setDiscordantStarsMode(true);
                game.validateAndSetExploreDeck(event, Mapper.getDeck("explores_DS"));
                game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_cards_ds"));
                if (game.isAbsolMode()) {
                    if (game.getTechnologyDeckID().contains("absol")) {
                        game.setTechnologyDeckID("techs_ds_absol");
                    }
                    if (game.getRelicDeckID().contains("absol")) {
                        game.validateAndSetRelicDeck(Mapper.getDeck("relics_absol_ds"));
                    }

                } else {
                    game.validateAndSetRelicDeck(Mapper.getDeck("relics_ds"));
                    game.setTechnologyDeckID("techs_ds");

                }
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Set the explores/action cards/relics to Discordant Stars stuff.");
            }
            case HBACDECK2 -> {
                game.validateAndSetActionCardDeck(event, Mapper.getDeck("action_deck_2"));
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Set the action card deck to Action Card Deck 2.");
            }
            case HBDSFACTIONS -> {
                game.setDiscordantStarsMode(true);
                if (game.getTechnologyDeckID().contains("absol")) {
                    game.setTechnologyDeckID("techs_ds_absol");
                } else {
                    game.setTechnologyDeckID("techs_ds");
                }
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Set game to Discordant Stars mode. Only includes factions and planets unless you also click/clicked the Discordant Stars Explores button.");
            }
            case HBHBSC -> {
                game.setHomebrewSCMode(true);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(),
                    "Set game to homebrew strategy card mode.");
            }
            case HBREDTAPE -> {
                game.setRedTapeMode(true);
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Set game to red tape mode.");
            }
            case HBOMEGAPHASE -> {
                if (game.getRevealedPublicObjectives().size() > 1) {
                    MessageHelper.sendMessageToChannel(event.getMessageChannel(), "You can't enable Omega Phase after revealing public objectives.");
                    return;
                }
                game.setOmegaPhaseMode(true);
                game.validateAndSetPublicObjectivesStage1Deck(event, Mapper.getDeck("public_stage_1_objectives_omegaphase"));
                game.setUpPeakableObjectives(9, 1);
                game.shuffleInBottomObjective(Constants.IMPERIUM_REX_ID, 5, 1);
                game.setUpPeakableObjectives(0, 2);
                game.validateAndSetPublicObjectivesStage2Deck(event, Mapper.getDeck("public_stage_2_objectives_omegaphase"));
                //Temporary measure: Remove incompatible components
                game.removeACFromGame("hack");
                game.removeAgendaFromGame("incentive");
                game.getSecretObjectives().remove("dtd");
                //end

                VoiceOfTheCouncilHelper.ResetVoiceOfTheCouncil(game);
                OmegaPhaseModStatusHelper.PrintGreeting(game);
            }
        }
    }
}
