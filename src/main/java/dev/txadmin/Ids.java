package dev.txadmin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;

/**
 * ID pemain bertambah sesuai urutan join (1, 2, 3, ...) dan tidak dipakai ulang.
 * ID TIDAK tampil di tab / nametag pemain biasa. Admin yang mengaktifkan "Player IDs"
 * melihat angka ID di atas kepala pemain lain (bukan dirinya sendiri) lewat TextDisplay
 * yang hanya terlihat oleh admin tersebut.
 */
public final class Ids implements Listener {
    private final TxAdmin p;
    private final Map<UUID, Integer> ids = new HashMap<>();
    private final Map<UUID, TextDisplay> tags = new HashMap<>();
    private final Set<UUID> on = new HashSet<>();
    private int counter = 0;

    public Ids(TxAdmin p) {
        this.p = p;
        // bersihkan team "txN" sisa versi lama
        for (Team t : new ArrayList<>(Bukkit.getScoreboardManager().getMainScoreboard().getTeams())) {
            if (t.getName().matches("tx\\d+")) t.unregister();
        }
        Bukkit.getOnlinePlayers().forEach(this::assign);
        Bukkit.getScheduler().runTaskTimer(p, this::refresh, 20L, 20L);
    }

    public int idOf(Player pl) { return ids.getOrDefault(pl.getUniqueId(), 0); }

    private void assign(Player pl) { ids.put(pl.getUniqueId(), ++counter); }

    public boolean isOn(Player a) { return on.contains(a.getUniqueId()); }

    public boolean toggle(Player a) {
        boolean nowOn = on.add(a.getUniqueId());
        if (!nowOn) on.remove(a.getUniqueId());
        refresh();
        return nowOn;
    }

    public void shutdown() {
        tags.values().forEach(d -> { if (d.isValid()) d.remove(); });
        tags.clear();
    }

    private TextDisplay spawn(Player t) {
        float off = (float) p.getConfig().getDouble("ids.height-offset", 0.35);
        TextDisplay d = t.getWorld().spawn(t.getLocation(), TextDisplay.class, e -> {
            e.setVisibleByDefault(false);
            e.setPersistent(false);
            e.setBillboard(Display.Billboard.CENTER);
            e.text(Component.text(String.valueOf(idOf(t)), NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            e.setTransformation(new Transformation(new Vector3f(0f, off, 0f), new AxisAngle4f(),
                    new Vector3f(1f, 1f, 1f), new AxisAngle4f()));
        });
        t.addPassenger(d);
        return d;
    }

    private void refresh() {
        on.removeIf(u -> Bukkit.getPlayer(u) == null);
        if (on.isEmpty()) { shutdown(); return; }
        for (Player t : Bukkit.getOnlinePlayers()) {
            TextDisplay d = tags.get(t.getUniqueId());
            if (d == null || !d.isValid() || !d.getWorld().equals(t.getWorld()) || !t.getPassengers().contains(d)) {
                if (d != null && d.isValid()) d.remove();
                d = spawn(t);
                tags.put(t.getUniqueId(), d);
            }
            for (Player v : Bukkit.getOnlinePlayers()) {
                if (on.contains(v.getUniqueId()) && !v.equals(t)) v.showEntity(p, d);
                else v.hideEntity(p, d);
            }
        }
    }

    /** Cari pemain berdasarkan ID, nama persis, atau awalan nama. */
    public Player find(String s) {
        if (s.matches("\\d+")) {
            int id = Integer.parseInt(s);
            for (Player pl : Bukkit.getOnlinePlayers()) if (idOf(pl) == id) return pl;
        }
        Player exact = Bukkit.getPlayerExact(s);
        if (exact != null) return exact;
        for (Player pl : Bukkit.getOnlinePlayers()) if (pl.getName().toLowerCase().startsWith(s.toLowerCase())) return pl;
        return null;
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { assign(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        TextDisplay d = tags.remove(id);
        if (d != null && d.isValid()) d.remove();
        ids.remove(id);
        on.remove(id);
    }
}
