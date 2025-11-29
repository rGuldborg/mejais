package org.example.service.lcu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.util.AppPaths;
import org.example.util.DebugLog;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class LeagueClientChampSelectWatcher {

    private static final int SLOT_COUNT = 5;
    private static final String SUBSCRIBE_ALL_EVENTS = "[5, \"OnJsonApiEvent\"]";

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Consumer<ChampSelectSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "lcu-reconnect-thread"));

    private LcuWebSocketClient wsClient;

    public void addListener(Consumer<ChampSelectSnapshot> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<ChampSelectSnapshot> listener) {
        listeners.remove(listener);
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        debug("LCU watcher starting.");
        connect();
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        if (wsClient != null) {
            wsClient.close();
        }
        reconnectExecutor.shutdownNow();
        debug("LCU watcher stopped.");
    }

    private void connect() {
        if (!running.get()) return;

        try {
            Path lockfilePath = locateLockfile().orElse(null);
            if (lockfilePath == null) {
                debug("No lockfile detected; scheduling reconnect.");
                broadcast(ChampSelectSnapshot.waiting("Looking for League client..."));
                scheduleReconnect();
                return;
            }

            debug("Lockfile found at " + lockfilePath);
            LockfileInfo info = readLockfile(lockfilePath);
            debug("Connecting to LCU WebSocket on port " + info.port());
            URI uri = new URI("wss://127.0.0.1:" + info.port());

            wsClient = new LcuWebSocketClient(uri, info.authHeader());
            SSLContext sslContext = buildSecureContext(lockfilePath);
            wsClient.setSocket(sslContext.getSocketFactory().createSocket());
            wsClient.connect();

        } catch (Exception e) {
            debug("LCU connection failure: " + e);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (!running.get()) return;
        reconnectExecutor.schedule(this::connect, 5, TimeUnit.SECONDS);
    }

    private void broadcast(ChampSelectSnapshot snapshot) {
        listeners.forEach(listener -> {
            try {
                listener.accept(snapshot);
            } catch (Exception ex) {
            }
        });
    }

    private void handleWebSocketMessage(String message) {
        try {
            JsonNode event = mapper.readTree(message);
            if (!event.isArray() || event.size() < 3) {
                return;
            }

            String eventName = event.get(1).asText();
            if (!"OnJsonApiEvent".equals(eventName)) {
                return;
            }

            JsonNode payload = event.get(2);
            String uri = payload.path("uri").asText();
            if (!uri.contains("/lol-champ-select/v1/session")) {
                return;
            }

            String eventType = payload.path("eventType").asText();
            JsonNode data = payload.path("data");

            if ("Delete".equals(eventType)) {
                broadcast(ChampSelectSnapshot.waiting("Client idle (no champ select)."));
            } else if (("Create".equals(eventType) || "Update".equals(eventType)) && data != null && !data.isNull()) {
                broadcast(parseSnapshot(data));
            }
        } catch (IOException e) {
        }
    }

    private ChampSelectSnapshot parseSnapshot(JsonNode root) {
        List<String> allyBans = extractBansFromActions(root.path("actions"), true);
        List<String> enemyBans = extractBansFromActions(root.path("actions"), false);
        List<String> allyPicks = convertTeam(root.path("myTeam"), true);
        List<String> enemyPicks = convertTeam(root.path("theirTeam"), false);
        ChampSelectSnapshot.Side firstPickSide = resolveFirstPickSide(root.path("actions"));
        String phase = root.path("timer").path("phase").asText("BAN_PICK");
        String status;
        if ("BAN_PICK".equalsIgnoreCase(phase)) {
            status = "Banning and Picking in progress...";
        } else {
            status = "Waiting for Champion Select...";
        }
        return new ChampSelectSnapshot(true, firstPickSide, allyBans, enemyBans, allyPicks, enemyPicks, status);
    }

    private List<String> extractBansFromActions(JsonNode actionsNode, boolean isAlly) {
        List<String> bans = new ArrayList<>();
        if (actionsNode != null && actionsNode.isArray()) {
            for (JsonNode turn : actionsNode) {
                for (JsonNode action : turn) {
                    if ("ban".equalsIgnoreCase(action.path("type").asText("")) &&
                        action.path("isAllyAction").asBoolean(false) == isAlly &&
                        action.path("completed").asBoolean(false)) {

                        int championId = action.path("championId").asInt(-1);
                        if (championId > 0) {
                            bans.add(mapChampion(championId));
                        }
                    }
                }
            }
        }
        while (bans.size() < SLOT_COUNT) {
            bans.add(null);
        }
        return bans.subList(0, SLOT_COUNT);
    }

    private List<String> convertTeam(JsonNode teamNode, boolean isAlly) {
        List<String> picks = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            picks.add(null);
        }
        if (teamNode != null && teamNode.isArray()) {
            for (JsonNode pick : teamNode) {
                int cellId = pick.path("cellId").asInt(-1);
                int index;
                if (isAlly) {
                    index = cellId - SLOT_COUNT;
                } else {
                    index = cellId;
                }

                if (index >= 0 && index < SLOT_COUNT) {
                    int championId = pick.path("championId").asInt(-1);
                    if (championId <= 0) {
                        championId = pick.path("championPickIntent").asInt(-1);
                    }
                    picks.set(index, mapChampion(championId));
                }
            }
        }
        return picks;
    }

    private String mapChampion(int id) {
        if (id <= 0) return null;
        return ChampionIdMapper.nameForId(id);
    }

    private ChampSelectSnapshot.Side resolveFirstPickSide(JsonNode actionsNode) {
        if (actionsNode == null || !actionsNode.isArray()) return ChampSelectSnapshot.Side.UNKNOWN;
        for (JsonNode turn : actionsNode) {
            for (JsonNode action : turn) {
                if ("pick".equalsIgnoreCase(action.path("type").asText(""))) {
                    boolean isAlly = action.path("isAllyAction").asBoolean(false);
                    return isAlly ? ChampSelectSnapshot.Side.ALLY : ChampSelectSnapshot.Side.ENEMY;
                }
            }
        }
        return ChampSelectSnapshot.Side.UNKNOWN;
    }

    private LockfileInfo readLockfile(Path path) throws IOException {
        String content = Files.readString(path, StandardCharsets.UTF_8).trim();
        String[] parts = content.split(":");
        if (parts.length < 4) throw new IOException("Invalid lockfile format");
        return new LockfileInfo(Integer.parseInt(parts[2]), parts[3]);
    }

    private Optional<Path> locateLockfile() {
        Path override = asExistingPath(asLockfileOverride(System.getProperty("LEAGUE_LOCKFILE_PATH")));
        if (override == null) {
            override = asExistingPath(asLockfileOverride(System.getenv("LEAGUE_LOCKFILE_PATH")));
        }
        if (override == null) {
            override = asExistingPath(asLockfileOverride(readLockfileOverrideFromFile()));
        }
        if (override != null) {
            debug("Using lockfile override from environment: " + override);
            return Optional.of(override);
        }

        List<Path> candidates = new ArrayList<>();
        String os = System.getProperty("os.name", "generic").toLowerCase();
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            String programFiles = System.getenv("PROGRAMFILES");
            String programFilesX86 = System.getenv("PROGRAMFILES(X86)");
            String programW6432 = System.getenv("ProgramW6432");
            String systemDrive = System.getenv("SystemDrive");

            addWindowsLockfileCandidates(candidates, localAppData);
            addWindowsProgramCandidates(candidates, programFiles);
            addWindowsProgramCandidates(candidates, programFilesX86);
            addWindowsProgramCandidates(candidates, programW6432);
            if (systemDrive != null && !systemDrive.isBlank()) {
                Path driveRoot = Path.of(systemDrive + "\\");
                candidates.add(driveRoot.resolve(Path.of("Riot Games", "League of Legends", "lockfile")));
                candidates.add(driveRoot.resolve(Path.of("Riot Games", "Riot Client", "Config", "lockfile")));
            }
            candidates.add(Path.of("C:/", "Riot Games", "League of Legends", "lockfile"));
            candidates.add(Path.of("C:/", "Riot Games", "Riot Client", "Config", "lockfile"));
        } else if (os.contains("mac")) {
            candidates.add(Path.of("/Applications/League of Legends.app/Contents/LoL/lockfile"));
            candidates.add(Path.of(System.getProperty("user.home"), "Applications/League of Legends.app/Contents/LoL/lockfile"));
        }

        for (Path candidate : candidates) {
            if (isLeagueClientLockfile(candidate)) {
                debug("Using LeagueClient lockfile candidate: " + candidate);
                return Optional.of(candidate);
            }
        }

        for (Path candidate : candidates) {
            if (candidate != null && Files.exists(candidate)) {
                debug("Using fallback lockfile candidate: " + candidate);
                return Optional.of(candidate);
            }
        }
        debug("Lockfile not found among candidates.");
        return Optional.empty();
    }

    private Path asExistingPath(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(candidate);
            if (Files.exists(path)) {
                return path;
            }
        } catch (Exception ex) {
            debug("Ignoring invalid lockfile override '" + candidate + "': " + ex.getMessage());
        }
        return null;
    }

    private String asLockfileOverride(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() > 1) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String readLockfileOverrideFromFile() {
        try {
            Path overrideFile = AppPaths.locateDataFile("lockfile.override");
            if (overrideFile != null && Files.exists(overrideFile)) {
                return Files.readString(overrideFile, StandardCharsets.UTF_8);
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private boolean isLeagueClientLockfile(Path candidate) {
        if (candidate == null || !Files.exists(candidate)) {
            return false;
        }
        try {
            String content = Files.readString(candidate, StandardCharsets.UTF_8).trim();
            int separator = content.indexOf(':');
            if (separator <= 0) {
                return false;
            }
            String clientName = content.substring(0, separator);
            return clientName.startsWith("LeagueClient");
        } catch (IOException ignored) {
            return false;
        }
    }

    private void addWindowsLockfileCandidates(List<Path> candidates, String localAppData) {
        if (localAppData == null || localAppData.isBlank()) return;
        Path lad = Path.of(localAppData);
        candidates.add(lad.resolve(Path.of("Riot Games", "League of Legends", "lockfile")));
        candidates.add(lad.resolve(Path.of("Riot Games", "Riot Client", "Config", "lockfile")));
        candidates.add(lad.resolve(Path.of("Programs", "League of Legends", "lockfile")));
        candidates.add(lad.resolve(Path.of("Programs", "Riot Client", "Config", "lockfile")));
    }

    private void addWindowsProgramCandidates(List<Path> candidates, String base) {
        if (base == null || base.isBlank()) return;
        Path root = Path.of(base);
        candidates.add(root.resolve(Path.of("Riot Games", "League of Legends", "lockfile")));
        candidates.add(root.resolve(Path.of("Riot Games", "Riot Client", "Config", "lockfile")));
    }

    private SSLContext buildSecureContext(Path lockfilePath) {
        List<Path> certCandidates = new ArrayList<>();
        Path packagedCert = AppPaths.locateDataFile("riotgames.pem");
        if (packagedCert != null && Files.exists(packagedCert)) {
            certCandidates.add(packagedCert);
        }
        if (lockfilePath != null) {
            certCandidates.add(lockfilePath.getParent().resolve("riotgames.pem"));
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            certCandidates.add(Path.of(localAppData, "Riot Games", "Riot Client", "Config", "riotgames.pem"));
        }
        certCandidates.add(Path.of("C:/", "Riot Games", "League of Legends", "riotgames.pem"));

        for (Path candidate : certCandidates) {
            if (candidate != null && Files.exists(candidate)) {
                try {
                    debug("Loading LCU certificate from " + candidate);
                    return sslContextFromCertificate(candidate);
                } catch (Exception ex) {
                    System.err.println("Failed to load LCU certificate from " + candidate + ": " + ex.getMessage());
                    debug("Failed to load LCU certificate from " + candidate + ": " + ex.getMessage());
                }
            }
        }

        System.err.println("Warning: LCU certificate not found; falling back to insecure SSL context.");
        debug("LCU certificate not found; using insecure SSL context.");
        return insecureContext();
    }

    private SSLContext sslContextFromCertificate(Path certPath) throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());

        try (var input = Files.newInputStream(certPath)) {
            X509Certificate certificate = (X509Certificate) factory.generateCertificate(input);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("riotgames", certificate);
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, tmf.getTrustManagers(), new SecureRandom());
        return context;
    }

    private SSLContext insecureContext() {
        try {
            TrustManager[] trustAll = {new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return context;
        } catch (Exception e) {
            throw new RuntimeException("Failed to create SSL context for LCU connection", e);
        }
    }

    private record LockfileInfo(int port, String password) {
        String authHeader() {
            return "Basic " + Base64.getEncoder().encodeToString(("riot:" + password).getBytes(StandardCharsets.UTF_8));
        }
    }

    private class LcuWebSocketClient extends WebSocketClient {
        LcuWebSocketClient(URI serverUri, String authHeader) {
            super(serverUri);
            addHeader("Authorization", authHeader);
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            send(SUBSCRIBE_ALL_EVENTS);
            debug("LCU websocket opened.");
            broadcast(ChampSelectSnapshot.waiting("Client idle (no champ select)."));
        }

        @Override
        public void onMessage(String message) {
            handleWebSocketMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            debug("LCU websocket closed code=" + code + ", reason=" + reason + ", remote=" + remote);
            broadcast(ChampSelectSnapshot.waiting("Looking for League client..."));
            if (running.get()) {
                scheduleReconnect();
            }
        }

        @Override
        public void onError(Exception ex) {
            debug("LCU websocket error: " + ex);
        }
    }

    private void debug(String message) {
        DebugLog.log("[LCU] " + message);
    }
}
