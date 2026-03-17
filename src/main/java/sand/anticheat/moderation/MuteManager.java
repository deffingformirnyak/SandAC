package sand.anticheat.moderation;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.configuration.file.YamlConfiguration;
import sand.anticheat.SandAC;

public final class MuteManager {

    private final SandAC plugin;
    private final File file;
    private final Map<String, MuteEntry> muted = new ConcurrentHashMap<String, MuteEntry>();

    public MuteManager(SandAC plugin) {
        this.plugin = plugin;
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        this.file = new File(plugin.getDataFolder(), "mutes.yml");
        load();
    }

    public synchronized void mute(String targetName, String source, String reason, Long expiresAt) {
        this.muted.put(key(targetName), new MuteEntry(targetName, source, reason, expiresAt));
        save();
    }

    public synchronized boolean unmute(String targetName) {
        boolean removed = this.muted.remove(key(targetName)) != null;
        if (removed) {
            save();
        }
        return removed;
    }

    public MuteEntry getMute(String targetName) {
        MuteEntry entry = this.muted.get(key(targetName));
        if (entry == null) {
            return null;
        }

        if (entry.getExpiresAt() != null && System.currentTimeMillis() >= entry.getExpiresAt().longValue()) {
            unmute(targetName);
            return null;
        }
        return entry;
    }

    public boolean isMuted(String targetName) {
        return getMute(targetName) != null;
    }

    private void load() {
        if (!this.file.exists()) {
            return;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(this.file);
        for (String key : configuration.getKeys(false)) {
            String name = configuration.getString(key + ".name", key);
            String source = configuration.getString(key + ".source", "SandAC");
            String reason = configuration.getString(key + ".reason", "Не указана");
            Long expiresAt = configuration.contains(key + ".expiresAt") ? Long.valueOf(configuration.getLong(key + ".expiresAt")) : null;
            this.muted.put(key, new MuteEntry(name, source, reason, expiresAt));
        }
    }

    private synchronized void save() {
        YamlConfiguration configuration = new YamlConfiguration();
        for (Map.Entry<String, MuteEntry> entry : this.muted.entrySet()) {
            String key = entry.getKey();
            MuteEntry muteEntry = entry.getValue();
            configuration.set(key + ".name", muteEntry.getTargetName());
            configuration.set(key + ".source", muteEntry.getSource());
            configuration.set(key + ".reason", muteEntry.getReason());
            configuration.set(key + ".expiresAt", muteEntry.getExpiresAt());
        }

        try {
            configuration.save(this.file);
        } catch (IOException exception) {
            this.plugin.getLogger().warning("Failed to save mutes.yml: " + exception.getMessage());
        }
    }

    private String key(String targetName) {
        return targetName.toLowerCase();
    }

    public static final class MuteEntry {
        private final String targetName;
        private final String source;
        private final String reason;
        private final Long expiresAt;

        public MuteEntry(String targetName, String source, String reason, Long expiresAt) {
            this.targetName = targetName;
            this.source = source;
            this.reason = reason;
            this.expiresAt = expiresAt;
        }

        public String getTargetName() {
            return this.targetName;
        }

        public String getSource() {
            return this.source;
        }

        public String getReason() {
            return this.reason;
        }

        public Long getExpiresAt() {
            return this.expiresAt;
        }
    }
}
