package me.srrapero720.watervision.common.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WaterVisionServerConfig {
    public static final String DEFAULT_FALLBACK_MESSAGE_JSON = "[{\"text\":\"\\n\"},{\"text\":\"[\",\"color\":\"gray\"},{\"text\":\"ᴄɪɴᴇᴍᴀᴛɪǫᴜᴇ\",\"bold\":true,\"color\":\"#D6A84B\"},{\"text\":\"] \",\"color\":\"gray\"},{\"text\":\"Un problème de lecture ?\",\"color\":\"gray\"},{\"text\":\"\\n\"},{\"text\":\"Pour regarder la vidéo dans votre navigateur, \",\"color\":\"gray\"},{\"text\":\"cliquez ici\",\"color\":\"#5DADE2\",\"bold\":true},{\"text\":\".\",\"color\":\"gray\"},{\"text\":\"\\n\"}]";
    public static final String DEFAULT_FALLBACK_HOVER_TEXT = "Ouvrir la cinématique dans le navigateur";

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue FALLBACK_MESSAGE_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> FALLBACK_MESSAGE_JSON;
    public static final ForgeConfigSpec.ConfigValue<String> FALLBACK_HOVER_TEXT;

    static {
        final ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("fallback_message");

        FALLBACK_MESSAGE_ENABLED = builder
                .comment(
                        "Affiche un message cliquable dans le chat lorsque le lecteur de cinématique se ferme.",
                        "Le clic ouvre automatiquement l'URL originale passée à /playvideo dans le navigateur du joueur."
                )
                .define("enabled", true);

        FALLBACK_MESSAGE_JSON = builder
                .comment(
                        "Composant texte Minecraft au format JSON affiché après la fermeture de la cinématique.",
                        "Vous pouvez modifier librement le texte, les couleurs, le gras et les sauts de ligne.",
                        "Ne mettez pas de clickEvent ni d'URL ici : WaterVision rend automatiquement tout le message cliquable."
                )
                .define("message_json", DEFAULT_FALLBACK_MESSAGE_JSON, value -> value instanceof String && !((String) value).isBlank());

        FALLBACK_HOVER_TEXT = builder
                .comment("Texte affiché au survol du message cliquable.")
                .define("hover_text", DEFAULT_FALLBACK_HOVER_TEXT, value -> value instanceof String);

        builder.pop();
        SPEC = builder.build();
    }

    private WaterVisionServerConfig() {}
}
