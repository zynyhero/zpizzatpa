package com.zynyhero.zpizzatpa;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZpizzaTPA extends JavaPlugin implements Listener {

    private final MiniMessage mm = MiniMessage.miniMessage();

    /** receptor -> (emisor -> solicitud) */
    private final Map<UUID, Map<UUID, TpaRequest>> incoming = new ConcurrentHashMap<>();

    private FileConfiguration messages;
    private TpaMenus menus;

    // Floodgate (opcional, por reflexión)
    private Method fgGetInstance;
    private Method fgIsPlayer;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadMessages();
        initFloodgate();
        menus = new TpaMenus(this);
        Bukkit.getPluginManager().registerEvents(this, this);

        // Comandos con Brigadier (argumento <jugador> con sugerencias). Funciona en Paper, Purpur y Folia.
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> new TpaCommands(this).register(event.registrar()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        incoming.remove(id);
        incoming.values().forEach(m -> m.remove(id));
    }

    // ------------------------------------------------------------------ mensajes

    /** Carga messages.yml; las claves que falten se rellenan con las del jar. */
    private void loadMessages() {
        File file = new File(getDataFolder(), "messages.yml");
        if (!file.exists()) saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStream in = getResource("messages.yml")) {
            if (in != null) {
                messages.setDefaults(YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8)));
            }
        } catch (IOException ignored) {
        }
    }

    public Component msg(String key, TagResolver... resolvers) {
        String raw = messages.getString(key, "<red>Falta el mensaje: " + key);
        return mm.deserialize(raw, resolvers);
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(msg(key, resolvers));
    }

    public static TagResolver playerTag(String name) {
        return Placeholder.unparsed("player", name);
    }

    /** Nick sin el prefijo configurado (p. ej. el "." de Floodgate). */
    public String clean(String name) {
        String prefix = getConfig().getString("strip-prefix", "");
        if (prefix != null && !prefix.isEmpty() && name.startsWith(prefix) && name.length() > prefix.length()) {
            return name.substring(prefix.length());
        }
        return name;
    }

    private Player findPlayer(String name) {
        Player p = Bukkit.getPlayerExact(name);
        if (p != null) return p;
        String prefix = getConfig().getString("strip-prefix", "");
        if (prefix != null && !prefix.isEmpty()) return Bukkit.getPlayerExact(prefix + name);
        return null;
    }

    // ------------------------------------------------------------------ Bedrock

    private void initFloodgate() {
        try {
            Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            fgGetInstance = api.getMethod("getInstance");
            fgIsPlayer = api.getMethod("isFloodgatePlayer", UUID.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            fgGetInstance = null;
            fgIsPlayer = null;
        }
    }

    /** ¿Es un jugador de Bedrock (Geyser/Floodgate)? */
    public boolean isBedrock(Player p) {
        try {
            if (fgGetInstance != null && fgIsPlayer != null) {
                Object api = fgGetInstance.invoke(null);
                if (api != null && (boolean) fgIsPlayer.invoke(api, p.getUniqueId())) return true;
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }
        // Los UUID de Floodgate empiezan por 00000000-0000-0000-...
        if (p.getUniqueId().getMostSignificantBits() == 0L) return true;
        String prefix = getConfig().getString("strip-prefix", "");
        return prefix != null && !prefix.isEmpty() && p.getName().startsWith(prefix);
    }

    // ------------------------------------------------------------------ vanish

    /** Vanish de EssentialsX / SuperVanish / PremiumVanish y similares (metadata "vanished"). */
    public boolean isVanished(Player p) {
        for (MetadataValue v : p.getMetadata("vanished")) {
            if (v.asBoolean()) return true;
        }
        return false;
    }

    /**
     * ¿Puede "viewer" ver/elegir a "target"? Oculta a los jugadores en vanish
     * (también los ocultos con hidePlayer), salvo que el viewer tenga zpizzatpa.seevanished.
     */
    public boolean canTarget(CommandSender viewer, Player target) {
        if (viewer instanceof Player v) {
            if (v.hasPermission("zpizzatpa.seevanished")) return true;
            if (!v.canSee(target)) return false;
        }
        return !isVanished(target);
    }

    // ------------------------------------------------------------------ sugerencias

    /** Nombres sugeridos para /tpa y /tpahere. */
    List<String> targetNames(CommandSender sender) {
        List<String> out = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player me && me.getUniqueId().equals(o.getUniqueId())) continue;
            if (!canTarget(sender, o)) continue;
            out.add(clean(o.getName()));
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Nombres sugeridos para /tpaccept y /tpdeny: quienes te han pedido tpa. */
    List<String> pendingNames(CommandSender sender) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player me)) return out;
        Map<UUID, TpaRequest> map = incoming.get(me.getUniqueId());
        if (map == null) return out;
        long now = System.currentTimeMillis();
        for (TpaRequest r : map.values()) {
            if (r.expiresAt() < now) continue;
            Player s = Bukkit.getPlayer(r.sender());
            if (s != null) out.add(clean(s.getName()));
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    // ------------------------------------------------------------------ acciones (las usa TpaCommands)

    void reload(CommandSender sender) {
        reloadConfig();
        loadMessages();
        send(sender, "reloaded");
    }

    void openMenu(Player p, TpaRequest.Type type) {
        menus.openSelector(p, type);
    }

    void requestByName(Player p, String name, TpaRequest.Type type) {
        Player target = findPlayer(name);
        if (target == null || !canTarget(p, target)) {
            send(p, "offline");
            return;
        }
        sendRequest(p, target, type, false);
    }

    /** Crea la solicitud y avisa al receptor en el chat. */
    public boolean sendRequest(Player from, Player to, TpaRequest.Type type, boolean quietSender) {
        if (from.getUniqueId().equals(to.getUniqueId())) {
            if (!quietSender) send(from, "self");
            return false;
        }
        if (!to.isOnline() || !canTarget(from, to)) {
            if (!quietSender) send(from, "offline");
            return false;
        }

        int timeout = getConfig().getInt("request-timeout-seconds", 60);
        TpaRequest req = new TpaRequest(from.getUniqueId(), to.getUniqueId(), type,
                System.currentTimeMillis() + timeout * 1000L);
        incoming.computeIfAbsent(to.getUniqueId(), k -> new ConcurrentHashMap<>()).put(from.getUniqueId(), req);

        if (!quietSender) send(from, "sent", playerTag(clean(to.getName())));

        String key = switch (type) {
            case TPA -> "received-tpa";
            case TPAHERE -> "received-tpahere";
            case TPAALL -> "received-tpaall";
        };

        Component accept;
        Component deny;
        if (isBedrock(to)) {
            // Bedrock no puede pulsar el chat: se muestran los comandos
            accept = msg("bedrock-accept", playerTag(clean(from.getName())));
            deny = msg("bedrock-deny", playerTag(clean(from.getName())));
        } else {
            accept = msg("button-accept-chat")
                    .clickEvent(ClickEvent.runCommand("/tpaccept " + from.getName()))
                    .hoverEvent(HoverEvent.showText(msg("hover-accept")));
            deny = msg("button-deny-chat")
                    .clickEvent(ClickEvent.runCommand("/tpdeny " + from.getName()))
                    .hoverEvent(HoverEvent.showText(msg("hover-deny")));
        }

        to.sendMessage(Component.empty()
                .append(msg(key, playerTag(clean(from.getName()))))
                .appendNewline()
                .append(accept)
                .append(msg("button-separator"))
                .append(deny)
                .append(msg("expires-note", Placeholder.unparsed("seconds", String.valueOf(timeout)))));
        return true;
    }

    private TpaRequest take(Player target, String senderName) {
        Map<UUID, TpaRequest> map = incoming.get(target.getUniqueId());
        if (map == null) return null;

        long now = System.currentTimeMillis();
        map.values().removeIf(r -> r.expiresAt() < now);

        TpaRequest chosen;
        if (senderName != null) {
            Player s = findPlayer(senderName);
            chosen = s == null ? null : map.get(s.getUniqueId());
        } else {
            chosen = map.values().stream()
                    .max(Comparator.comparingLong(TpaRequest::expiresAt))
                    .orElse(null);
        }
        if (chosen != null) map.remove(chosen.sender());
        return chosen;
    }

    void accept(Player target, String senderName) {
        TpaRequest r = take(target, senderName);
        if (r == null) {
            send(target, "no-pending");
            return;
        }
        Player sender = Bukkit.getPlayer(r.sender());
        if (sender == null) {
            send(target, "sender-offline");
            return;
        }

        if (r.type() == TpaRequest.Type.TPA) {
            teleport(sender, target);
        } else {
            teleport(target, sender);
        }
        send(target, "accepted-target", playerTag(clean(sender.getName())));
        send(sender, "accepted-sender", playerTag(clean(target.getName())));
    }

    void deny(Player target, String senderName) {
        TpaRequest r = take(target, senderName);
        if (r == null) {
            send(target, "no-pending");
            return;
        }
        Player sender = Bukkit.getPlayer(r.sender());
        send(target, "denied-target", playerTag(sender != null ? clean(sender.getName()) : "?"));
        if (sender != null) send(sender, "denied-sender", playerTag(clean(target.getName())));
    }

    void cancel(Player sender) {
        int count = 0;
        for (Map.Entry<UUID, Map<UUID, TpaRequest>> e : incoming.entrySet()) {
            TpaRequest removed = e.getValue().remove(sender.getUniqueId());
            if (removed == null) continue;
            count++;
            Player target = Bukkit.getPlayer(e.getKey());
            if (target != null) send(target, "cancelled-target", playerTag(clean(sender.getName())));
        }
        if (count == 0) {
            send(sender, "no-outgoing");
        } else {
            send(sender, "cancelled-sender", Placeholder.unparsed("count", String.valueOf(count)));
        }
    }

    void tpall(Player admin) {
        int count = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getUniqueId().equals(admin.getUniqueId())) continue;
            if (!canTarget(admin, o)) continue;
            teleport(o, admin);
            count++;
        }
        send(admin, "tpall-done", Placeholder.unparsed("count", String.valueOf(count)));
    }

    void tpaall(Player admin) {
        int count = 0;
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getUniqueId().equals(admin.getUniqueId())) continue;
            if (sendRequest(admin, o, TpaRequest.Type.TPAALL, true)) count++;
        }
        send(admin, "tpaall-done", Placeholder.unparsed("count", String.valueOf(count)));
    }

    /** Teletransporta a "who" hasta "dest". Compatible con Folia (la ubicación se lee en el hilo de dest). */
    private void teleport(Player who, Player dest) {
        dest.getScheduler().run(this, task -> {
            Location loc = dest.getLocation();
            who.teleportAsync(loc);
        }, null);
    }
}
