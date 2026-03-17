package sand.anticheat.data;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;

public final class DataManager {

    private final Map<UUID, PlayerData> dataMap = new ConcurrentHashMap<UUID, PlayerData>();

    public PlayerData get(Player player) {
        return this.dataMap.computeIfAbsent(player.getUniqueId(), id -> new PlayerData(id));
    }

    public PlayerData get(UUID uniqueId) {
        return this.dataMap.get(uniqueId);
    }

    public void remove(UUID uniqueId) {
        this.dataMap.remove(uniqueId);
    }

    public void clear() {
        this.dataMap.clear();
    }
}
