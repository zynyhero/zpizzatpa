package com.zynyhero.zpizzatpa;

import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Cabeza del jugador como componente de texto. Esa función solo existe en Minecraft/Adventure 1.21.9+,
 * así que se usa por reflexión: en 1.21.8 simplemente no se muestra la cara.
 */
final class HeadSupport {

    private static final boolean AVAILABLE;
    private static Method playerHead;
    private static Method builderId;
    private static Method builderProperty;
    private static Method builderBuild;
    private static Method property;
    private static Method componentObject;

    static {
        boolean ok = false;
        try {
            Class<?> objectContents = Class.forName("net.kyori.adventure.text.object.ObjectContents");
            Class<?> headContents = Class.forName("net.kyori.adventure.text.object.PlayerHeadObjectContents");
            Class<?> builder = Class.forName("net.kyori.adventure.text.object.PlayerHeadObjectContents$Builder");
            Class<?> profileProperty = Class.forName("net.kyori.adventure.text.object.PlayerHeadObjectContents$ProfileProperty");

            playerHead = objectContents.getMethod("playerHead");
            builderId = builder.getMethod("id", UUID.class);
            builderProperty = builder.getMethod("profileProperty", profileProperty);
            builderBuild = builder.getMethod("build");
            property = headContents.getMethod("property", String.class, String.class, String.class);
            componentObject = Component.class.getMethod("object", objectContents);
            ok = true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // Servidor sin soporte de cabezas en texto
        }
        AVAILABLE = ok;
    }

    private HeadSupport() {
    }

    static boolean isAvailable() {
        return AVAILABLE;
    }

    /** Devuelve la cara del jugador, o un componente vacío si no se puede. */
    static Component face(Player target) {
        if (!AVAILABLE) return Component.empty();
        try {
            Object b = playerHead.invoke(null);
            builderId.invoke(b, target.getUniqueId());
            // Incluimos la textura para que la cara también salga con skins de Bedrock/Geyser
            for (ProfileProperty pp : target.getPlayerProfile().getProperties()) {
                if (pp.getName().equals("textures")) {
                    Object prop = property.invoke(null, "textures", pp.getValue(), pp.getSignature());
                    builderProperty.invoke(b, prop);
                }
            }
            return (Component) componentObject.invoke(null, builderBuild.invoke(b));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return Component.empty();
        }
    }
}
