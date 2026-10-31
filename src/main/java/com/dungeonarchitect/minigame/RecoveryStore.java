package com.dungeonarchitect.minigame;

import java.nio.file.*;
import java.util.UUID;
import org.bukkit.entity.Player;

/** Records survive server shutdown. Never replace an outstanding original snapshot. */
final class RecoveryStore {
    private final Path directory;
    RecoveryStore(Path directory) { this.directory = directory; }
    private Path path(UUID id) { return directory.resolve(id + ".yml"); }
    boolean pending(UUID id) { return Files.exists(path(id)); }
    void save(Player player) {
        if (pending(player.getUniqueId())) throw new IllegalStateException("Player has pending recovery: " + player.getName());
        // Closing a container returns the cursor item before inventory capture.
        player.closeInventory();
        PresetRepository.write(PlayerSnapshot.capture(player).data(), path(player.getUniqueId()));
    }
    boolean restore(Player player) {
        if (!pending(player.getUniqueId())) return true;
        if (!PlayerSnapshot.load(path(player.getUniqueId())).apply(player)) return false;
        // Persist restored inventory before consuming the recovery record.
        player.saveData();
        try { Files.delete(path(player.getUniqueId())); }
        catch (java.io.IOException ex) { throw new IllegalStateException("Could not finish recovery for " + player.getName(), ex); }
        return true;
    }
}
