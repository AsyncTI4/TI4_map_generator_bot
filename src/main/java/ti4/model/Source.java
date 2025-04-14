package ti4.model;

import ti4.service.emoji.FactionEmojis;
import ti4.service.emoji.SourceEmojis;
import ti4.service.emoji.TI4Emoji;

public class Source {

    public enum ComponentSource {

        // official
        base, pok, codex1, codex2, codex3,

        //big homebrew
        ds, absol, franken, uncharted_space, monuments,

        // lil homebrew
        lazax, action_deck_2, action_deck_2_old, keleresplus, little_omega, project_pi, neutral, lost_star_charts_of_ixth, flagshipping, promises_promises,

        // async homebrew
        draft, admins, pbd100, pbd500, pbd1000, testsource, pbd2000, fow, dane_leaks,

        // personal projs
        somno, ignis_aurora, asteroid, cryypter, voice_of_the_council, cpti, oath_of_kings, eronous, miltymod, luminous, holytispoon, salliance, nomadfalcon, unfulvio, andcat, sigma, byz_agendas, memephilosopher, riftset, omega_phase,

        // catchall
        other;

        public String toString() {
            return super.toString().toLowerCase();
        }

        /**
         * Converts a string identifier to the corresponding ComponentSource enum value.
         * 
         * @param id the string identifier
         * @return the ComponentSource enum value, or null if not found
         */
        public static ComponentSource fromString(String id) {
            for (ComponentSource source : values()) {
                if (source.toString().equals(id)) {
                    return source;
                }
            }
            return null;
        }

        public boolean isOfficial() {
            return switch (this) {
                case base, pok, codex1, codex2, codex3 -> true;
                default -> false;
            };
        }

        public boolean isPok() {
            return switch (this) {
                case base, pok, codex1, codex2, codex3 -> true;
                default -> false;
            };
        }

        public boolean isDs() {
            return switch (this) {
                case base, pok, codex1, codex2, codex3, ds, uncharted_space -> true;
                default -> false;
            };
        }

        public String emoji() {
            TI4Emoji emoji = switch (this) {
                case absol -> SourceEmojis.Absol;
                case ds -> SourceEmojis.DiscordantStars;
                case uncharted_space -> SourceEmojis.UnchartedSpace;
                case eronous, riftset -> SourceEmojis.Eronous;
                case admins -> FactionEmojis.AdminsFaction;
                case ignis_aurora, pbd2000 -> SourceEmojis.IgnisAurora;
                case keleresplus -> SourceEmojis.KeleresPlus;
                case project_pi -> SourceEmojis.ProjectPi;
                case flagshipping -> SourceEmojis.Flagshipping;
                case promises_promises -> SourceEmojis.PromisesPromises;
                case miltymod -> SourceEmojis.MiltyMod;
                case lazax -> FactionEmojis.Lazax;
                case neutral -> FactionEmojis.Neutral;
                case salliance -> SourceEmojis.StrategicAlliance;
                case monuments -> SourceEmojis.Monuments;
                default -> null;
            };
            return emoji == null ? "" : emoji.toString();
        }

        public String prettyName() {
            return switch (this) {
                case base -> "Twilight Imperium 4th Edition (Base Game)";
                case pok -> "Prophecy of Kings [Expansion]";
                case codex1 -> "Codex 1 - Omega Techs";
                case codex2 -> "Codex 2 - Relics";
                case codex3 -> "Codex 3 - Naalu, Yin, Keleres";
                case ds -> "Discordant Stars [Homebrew]";
                case absol -> "Absol's Mod [Homebrew]";
                case flagshipping -> "Flagshipping";
                case promises_promises -> "Promises Promises";
                case franken -> "Franken Draft [Homebrew Game Mode]";
                case monuments -> "Monuments+";
                default -> toString();
            };
        }
    }

}
