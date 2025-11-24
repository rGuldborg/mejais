package org.example.service.lcu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Periodically polls the local League Client Update (LCU) API to mirror champ select picks/bans.
 */
public final class LeagueClientChampSelectWatcher {
    private static final int SLOT_COUNT = 5;
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<Consumer<ChampSelectSnapshot>> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "lcu-champ-select");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ScheduledFuture<?> pollHandle;
    private volatile ChampSelectSnapshot lastSnapshot = ChampSelectSnapshot.waiting("Looking for League client...");
    private volatile LockfileInfo cachedInfo;
    private volatile HttpClient httpClient;
    private volatile Path lockfilePath;

    public void addListener(Consumer<ChampSelectSnapshot> listener) {
        listeners.add(listener);
    }

    public void removeListener(Consumer<ChampSelectSnapshot> listener) {
        listeners.remove(listener);
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            pollHandle = executor.scheduleWithFixedDelay(this::poll, 0, 2, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        running.set(false);
        Optional.ofNullable(pollHandle).ifPresent(handle -> {
            handle.cancel(true);
            pollHandle = null;
        });
    }

    private void poll() {
        if (!running.get()) {
            return;
        }
        ChampSelectSnapshot snapshot = fetchSnapshot();
        if (!snapshot.equals(lastSnapshot)) {
            lastSnapshot = snapshot;
            listeners.forEach(listener -> {
                try {
                    listener.accept(snapshot);
                } catch (Exception ex) {
                    System.err.println("[LCU Watcher] Listener error: " + ex.getMessage());
                }
            });
        }
    }

    private ChampSelectSnapshot fetchSnapshot() {
        try {
            Path path = locateLockfile().orElse(null);
            if (path == null) {
                cachedInfo = null;
                httpClient = null;
                lockfilePath = null;
                return ChampSelectSnapshot.waiting("Looking for League client...");
            }
            lockfilePath = path;
            LockfileInfo info = readLockfile(path);
            HttpClient client = ensureClient(info);
            HttpRequest request = HttpRequest.newBuilder(info.endpoint("/lol-champ-select/v1/session"))
                    .timeout(Duration.ofSeconds(2))
                    .header("Authorization", info.authHeader())
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return ChampSelectSnapshot.waiting("Client idle (no champ select).");
            }
            if (response.statusCode() != 200) {
                return ChampSelectSnapshot.waiting("Champ select unavailable (HTTP " + response.statusCode() + ").");
            }
            JsonNode root = mapper.readTree(response.body());
            return parseSnapshot(root);
        } catch (IOException ex) {
            cachedInfo = null;
            httpClient = null;
            return ChampSelectSnapshot.waiting("Looking for League client...");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ChampSelectSnapshot.waiting("Looking for League client...");
        }
    }

    private ChampSelectSnapshot parseSnapshot(JsonNode root) {
        List<String> allyBans = convertBans(root.path("bans").path("myTeam"));
        List<String> enemyBans = convertBans(root.path("bans").path("theirTeam"));
        List<String> allyPicks = convertTeam(root.path("myTeam"));
        List<String> enemyPicks = convertTeam(root.path("theirTeam"));
        ChampSelectSnapshot.Side firstPickSide = resolveFirstPickSide(root.path("actions"));
        String phase = root.path("timer").path("phase").asText("BAN_PICK");
        String status = "Mirroring live champ select (" + phase + ")";
        return new ChampSelectSnapshot(
                true,
                firstPickSide,
                allyBans,
                enemyBans,
                allyPicks,
                enemyPicks,
                status
        );
    }

    private List<String> convertBans(JsonNode node) {
        List<String> bans = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            String entry = null;
            if (node != null && node.has(i)) {
                int id = node.get(i).asInt(-1);
                entry = mapChampion(id);
            }
            bans.add(entry);
        }
        return List.copyOf(bans);
    }

    private List<String> convertTeam(JsonNode teamNode) {
        List<String> picks = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            String entry = null;
            if (teamNode != null && teamNode.has(i)) {
                int id = teamNode.get(i).path("championId").asInt(-1);
                entry = mapChampion(id);
            }
            picks.add(entry);
        }
        return List.copyOf(picks);
    }

    private String mapChampion(int id) {
        if (id <= 0) {
            return null;
        }
        return ChampionIdMapper.nameForId(id);
    }

    private ChampSelectSnapshot.Side resolveFirstPickSide(JsonNode actionsNode) {
        if (actionsNode == null || !actionsNode.isArray()) {
            return ChampSelectSnapshot.Side.UNKNOWN;
        }
        for (JsonNode turn : actionsNode) {
            for (JsonNode action : turn) {
                if ("pick".equalsIgnoreCase(action.path("type").asText(""))) {
                    boolean ally = action.path("isAllyAction").asBoolean(false);
                    return ally ? ChampSelectSnapshot.Side.ALLY : ChampSelectSnapshot.Side.ENEMY;
                }
            }
        }
        return ChampSelectSnapshot.Side.UNKNOWN;
    }

    private LockfileInfo readLockfile(Path path) throws IOException {
        String content = Files.readString(path).trim();
        String[] parts = content.split(":");
        if (parts.length < 5) {
            throw new IOException("Unexpected lockfile format");
        }
        int port = Integer.parseInt(parts[2]);
        String password = parts[3];
        String protocol = parts[4];
        String auth = "Basic " + Base64.getEncoder().encodeToString(("riot:" + password).getBytes(StandardCharsets.UTF_8));
        return new LockfileInfo(port, password, protocol, auth);
    }

    private Optional<Path> locateLockfile() {
        Path override = overrideLockfile();
        if (override != null) {
            if (Files.exists(override)) {
                return Optional.of(override);
            }
            System.err.println("[LCU Watcher] LEAGUE_LOCKFILE_PATH set but not found: " + override);
        }
        List<Path> candidates = candidatePaths();
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private Path overrideLockfile() {
        String override = System.getenv("LEAGUE_LOCKFILE_PATH");
        if (override == null || override.isBlank()) {
            return null;
        }
        return Path.of(override.trim());
    }

    private List<Path> candidatePaths() {
        List<Path> paths = new ArrayList<>();
        String os = System.getProperty("os.name", "generic").toLowerCase();
        if (os.contains("mac")) {
            Path system = Path.of("/Applications/League of Legends.app/Contents/LoL/lockfile");
            Path user = Path.of(System.getProperty("user.home", ""), "Applications", "League of Legends.app", "Contents", "LoL", "lockfile");
            paths.add(system);
            paths.add(user);
        } else { // default to Windows order
            paths.add(Path.of("C:", "Riot Games", "League of Legends", "lockfile"));
            String localAppData = Optional.ofNullable(System.getenv("LOCALAPPDATA")).orElse("");
            if (!localAppData.isBlank()) {
                paths.add(Path.of(localAppData, "Riot Games", "League of Legends", "lockfile"));
                paths.add(Path.of(localAppData, "Riot Games", "Riot Client", "Config", "lockfile"));
            }
            String programFiles = Optional.ofNullable(System.getenv("PROGRAMFILES")).orElse("C:\\Program Files");
            if (!programFiles.isBlank()) {
                paths.add(Path.of(programFiles, "Riot Games", "League of Legends", "lockfile"));
            }
        }
        return paths;
    }

    private HttpClient ensureClient(LockfileInfo info) {
        LockfileInfo cached = this.cachedInfo;
        if (cached != null && cached.port == info.port && Objects.equals(cached.password, info.password)) {
            return httpClient;
        }
        HttpClient client = HttpClient.newBuilder()
                .sslContext(insecureContext())
                .connectTimeout(Duration.ofSeconds(2))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.cachedInfo = info;
        this.httpClient = client;
        return client;
    }

    private SSLContext insecureContext() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return context;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to build SSL context", ex);
        }
    }

    private record LockfileInfo(int port, String password, String protocol, String authHeader) {
        URI endpoint(String path) {
            return URI.create(protocol + "://127.0.0.1:" + port + path);
        }
    }
}
