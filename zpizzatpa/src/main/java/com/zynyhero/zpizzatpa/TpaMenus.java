package com.zynyhero.zpizzatpa;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TpaMenus {

    private static final int BASE_WIDTH = 150;

    private static final ClickCallback.Options ONE_USE = ClickCallback.Options.builder()
            .uses(1)
            .lifetime(Duration.ofMinutes(2))
            .build();

    private final ZpizzaTPA plugin;

    public TpaMenus(ZpizzaTPA plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ selector de jugador

    public void openSelector(Player viewer, TpaRequest.Type type) {
        openSelector(viewer, type, 0, "");
    }

    public void openSelector(Player viewer, TpaRequest.Type type, int page, String query) {
        // Jugadores que este viewer puede elegir (sin él mismo y sin los que están en vanish)
        List<Player> all = new ArrayList<>();
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getUniqueId().equals(viewer.getUniqueId())) continue;
            if (!plugin.canTarget(viewer, o)) continue;
            all.add(o);
        }
        if (all.isEmpty()) {
            plugin.send(viewer, "no-players");
            return;
        }
        all.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(
                plugin.clean(a.getName()), plugin.clean(b.getName())));

        int perPage = Math.max(1, plugin.getConfig().getInt("players-per-page", 9));
        // Modo "cuadrícula": hay más jugadores que una página -> flechas + búsqueda
        boolean grid = all.size() > perPage;

        String shownQuery = query == null ? "" : query.trim();
        String q = shownQuery.toLowerCase(Locale.ROOT);
        List<Player> shown = q.isEmpty()
                ? all
                : all.stream()
                        .filter(p -> plugin.clean(p.getName()).toLowerCase(Locale.ROOT).contains(q))
                        .toList();

        int pages = Math.max(1, (shown.size() + perPage - 1) / perPage);
        int current = Math.max(0, Math.min(page, pages - 1));

        List<ActionButton> buttons = new ArrayList<>();
        List<DialogInput> inputs = new ArrayList<>();
        List<DialogBody> bodies = new ArrayList<>();

        if (grid) {
            // Fila superior: [←]  [Página x/y]  [→]
            buttons.add(navButton(plugin.msg("page-prev"), viewer, type, current - 1, shownQuery, current > 0));
            buttons.add(ActionButton.builder(plugin.msg("page-indicator",
                            Placeholder.unparsed("page", String.valueOf(current + 1)),
                            Placeholder.unparsed("pages", String.valueOf(pages))))
                    .width(BASE_WIDTH)
                    .build());
            buttons.add(navButton(plugin.msg("page-next"), viewer, type, current + 1, shownQuery, current < pages - 1));

            // Barra de búsqueda (los campos de texto siempre van encima de la cuadrícula)
            inputs.add(DialogInput.text("search", plugin.msg("search-label"))
                    .width(200)
                    .labelVisible(true)
                    .initial(shownQuery)
                    .maxLength(20)
                    .build());

            // Botón que envía lo escrito en la barra
            buttons.add(ActionButton.builder(plugin.msg("search-button"))
                    .width(BASE_WIDTH)
                    .action(DialogAction.customClick((view, audience) -> {
                        if (audience instanceof Player clicker) {
                            String typed = view.getText("search");
                            String text = typed == null ? "" : typed;
                            clicker.getScheduler().run(plugin, t -> openSelector(clicker, type, 0, text), null);
                        }
                    }, ONE_USE))
                    .build());

            if (!shownQuery.isEmpty()) {
                buttons.add(ActionButton.builder(plugin.msg("search-clear"))
                        .width(BASE_WIDTH)
                        .action(DialogAction.customClick((view, audience) -> {
                            if (audience instanceof Player clicker) {
                                clicker.getScheduler().run(plugin, t -> openSelector(clicker, type, 0, ""), null);
                            }
                        }, ONE_USE))
                        .build());
                bodies.add(DialogBody.plainMessage(plugin.msg("search-active",
                        Placeholder.unparsed("query", shownQuery))));
            }
            if (shown.isEmpty()) {
                bodies.add(DialogBody.plainMessage(plugin.msg("search-no-results")));
            }
        }

        int from = current * perPage;
        int to = Math.min(shown.size(), from + perPage);
        for (Player target : shown.subList(from, to)) {
            buttons.add(playerButton(target, type));
        }

        DialogBase.Builder base = DialogBase.builder(plugin.msg("menu-title"));
        if (!bodies.isEmpty()) base.body(bodies);
        if (!inputs.isEmpty()) base.inputs(inputs);
        if (grid) {
            // El diálogo se queda abierto al pulsar (las flechas/búsqueda lo vuelven a abrir)
            base.afterAction(DialogBase.DialogAfterAction.NONE);
        }

        int columns = grid ? 3 : 1;
        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(base.build())
                .type(DialogType.multiAction(buttons, null, columns)));
        viewer.showDialog(dialog);
    }

    private ActionButton navButton(Component label, Player viewer, TpaRequest.Type type,
                                   int targetPage, String query, boolean enabled) {
        ActionButton.Builder b = ActionButton.builder(label).width(BASE_WIDTH);
        if (enabled) {
            b.action(DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player clicker) {
                    clicker.getScheduler().run(plugin, t -> openSelector(clicker, type, targetPage, query), null);
                }
            }, ONE_USE));
        }
        // Sin acción (primera/última página): el botón no hace nada
        return b.build();
    }

    private ActionButton playerButton(Player target, TpaRequest.Type type) {
        String name = plugin.clean(target.getName());

        // Cara a la izquierda, nick a la derecha
        Component label = Component.empty();
        if (HeadSupport.isAvailable()) {
            label = label.append(HeadSupport.face(target)).append(Component.text(" "));
        }
        label = label.append(Component.text(name));

        // Botón más ancho si el nick es largo
        int len = name.length();
        int width = len <= 10 ? BASE_WIDTH : Math.min(400, BASE_WIDTH + (len - 10) * 10);

        UUID targetId = target.getUniqueId();
        return ActionButton.builder(label)
                .width(width)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        clicker.getScheduler().run(plugin, t -> openConfirm(clicker, targetId, type), null);
                    }
                }, ONE_USE))
                .build();
    }

    // ------------------------------------------------------------ confirmación

    private void openConfirm(Player viewer, UUID targetId, TpaRequest.Type type) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            plugin.send(viewer, "offline");
            return;
        }

        ActionButton yes = ActionButton.builder(plugin.msg("button-confirm"))
                .width(120)
                .action(DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player clicker) {
                        clicker.getScheduler().run(plugin, t -> {
                            Player t2 = Bukkit.getPlayer(targetId);
                            if (t2 == null) {
                                plugin.send(clicker, "offline");
                            } else {
                                plugin.sendRequest(clicker, t2, type, false);
                            }
                        }, null);
                    }
                }, ONE_USE))
                .build();

        // Sin acción: simplemente cierra el diálogo
        ActionButton no = ActionButton.builder(plugin.msg("button-cancel"))
                .width(120)
                .build();

        DialogBase.Builder base = DialogBase.builder(plugin.msg("confirm-title"));
        Component body = plugin.msg("confirm-body", ZpizzaTPA.playerTag(plugin.clean(target.getName())));
        base.body(List.of(DialogBody.plainMessage(body)));

        Dialog dialog = Dialog.create(factory -> factory.empty()
                .base(base.build())
                .type(DialogType.confirmation(yes, no)));
        viewer.showDialog(dialog);
    }
}
