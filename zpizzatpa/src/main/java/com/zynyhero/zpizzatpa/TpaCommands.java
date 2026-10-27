package com.zynyhero.zpizzatpa;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Registro de comandos con Brigadier: así el cliente muestra el argumento &lt;jugador&gt; y sus sugerencias. */
final class TpaCommands {

    private final ZpizzaTPA plugin;

    TpaCommands(ZpizzaTPA plugin) {
        this.plugin = plugin;
    }

    void register(Commands commands) {
        // /tpa [jugador]   (y su alias /tpask)
        commands.register(
                playerCommand("tpa", "zpizzatpa.tpa", false,
                        p -> plugin.openMenu(p, TpaRequest.Type.TPA),
                        (p, name) -> plugin.requestByName(p, name, TpaRequest.Type.TPA)),
                "Envía una solicitud de teletransporte (sin nombre abre el menú)",
                List.of("tpask"));

        // /tpahere [jugador]
        commands.register(
                playerCommand("tpahere", "zpizzatpa.tpahere", false,
                        p -> plugin.openMenu(p, TpaRequest.Type.TPAHERE),
                        (p, name) -> plugin.requestByName(p, name, TpaRequest.Type.TPAHERE)),
                "Pide a un jugador que se teletransporte a ti");

        // /tpaccept [jugador]  y  /tpdeny [jugador]  (sugiere quienes te han pedido tpa)
        commands.register(
                playerCommand("tpaccept", "zpizzatpa.tpaccept", true,
                        p -> plugin.accept(p, null),
                        plugin::accept),
                "Acepta una solicitud de teletransporte");
        commands.register(
                playerCommand("tpdeny", "zpizzatpa.tpdeny", true,
                        p -> plugin.deny(p, null),
                        plugin::deny),
                "Rechaza una solicitud de teletransporte");

        // /tpcancel
        commands.register(
                Commands.literal("tpcancel")
                        .requires(src -> src.getSender().hasPermission("zpizzatpa.tpcancel"))
                        .executes(ctx -> {
                            Player p = player(ctx.getSource());
                            if (p != null) plugin.cancel(p);
                            return Command.SINGLE_SUCCESS;
                        })
                        .build(),
                "Cancela tus solicitudes enviadas");

        // Admin
        commands.register(simple("tpall", "zpizzatpa.tpall", plugin::tpall),
                "Teletransporta a todos los jugadores a ti");
        commands.register(simple("tpaall", "zpizzatpa.tpaall", plugin::tpaall),
                "Envía una solicitud a todos para que vayan a ti");

        // /zpizzatpa reload
        commands.register(
                Commands.literal("zpizzatpa")
                        .executes(ctx -> {
                            plugin.send(ctx.getSource().getSender(), "usage-admin");
                            return Command.SINGLE_SUCCESS;
                        })
                        .then(Commands.literal("reload")
                                .requires(src -> src.getSender().hasPermission("zpizzatpa.reload"))
                                .executes(ctx -> {
                                    plugin.reload(ctx.getSource().getSender());
                                    return Command.SINGLE_SUCCESS;
                                }))
                        .build(),
                "Comandos de administración de zpizzatpa");
    }

    // ------------------------------------------------------------------ helpers

    /** Comando con argumento opcional &lt;jugador&gt;. */
    private LiteralCommandNode<CommandSourceStack> playerCommand(String literal, String permission, boolean pending,
                                                                 Consumer<Player> noArg,
                                                                 BiConsumer<Player, String> withArg) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .executes(ctx -> {
                    Player p = player(ctx.getSource());
                    if (p != null) noArg.accept(p);
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("jugador", StringArgumentType.word())
                        .suggests((ctx, builder) -> suggest(ctx.getSource().getSender(), builder, pending))
                        .executes(ctx -> {
                            Player p = player(ctx.getSource());
                            if (p != null) withArg.accept(p, StringArgumentType.getString(ctx, "jugador"));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private LiteralCommandNode<CommandSourceStack> simple(String literal, String permission, Consumer<Player> action) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .executes(ctx -> {
                    Player p = player(ctx.getSource());
                    if (p != null) action.accept(p);
                    return Command.SINGLE_SUCCESS;
                })
                .build();
    }

    private Player player(CommandSourceStack src) {
        CommandSender sender = src.getSender();
        if (sender instanceof Player p) return p;
        plugin.send(sender, "only-players");
        return null;
    }

    private CompletableFuture<Suggestions> suggest(CommandSender sender, SuggestionsBuilder builder, boolean pending) {
        String start = builder.getRemainingLowerCase();
        List<String> names = pending ? plugin.pendingNames(sender) : plugin.targetNames(sender);
        for (String n : names) {
            if (n.toLowerCase(Locale.ROOT).startsWith(start)) builder.suggest(n);
        }
        return builder.buildFuture();
    }
}
