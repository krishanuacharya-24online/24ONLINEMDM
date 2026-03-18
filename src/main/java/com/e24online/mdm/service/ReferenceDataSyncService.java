package com.e24online.mdm.service;

import com.e24online.mdm.config.ReferenceDataSyncProperties;
import com.e24online.mdm.records.CatalogSyncResult;
import com.e24online.mdm.records.lookup.IosLookup;
import com.e24online.mdm.records.LifecyclePlatform;
import com.e24online.mdm.records.SyncReport;
import com.e24online.mdm.web.dto.AppCatalog;
import com.e24online.mdm.web.dto.OsLifecycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ReferenceDataSyncService {
    private static final Logger log = LoggerFactory.getLogger(ReferenceDataSyncService.class);

    private static final String LIFECYCLE_UPSERT_SQL = """
            INSERT INTO os_release_lifecycle_master (
                platform_code,
                os_type,
                os_name,
                cycle,
                released_on,
                eol_on,
                eeol_on,
                latest_version,
                support_state,
                source_name,
                source_url,
                notes,
                status,
                is_deleted,
                created_by,
                modified_at,
                modified_by
            )
            VALUES (
                :platformCode,
                :osType,
                :osName,
                :cycle,
                :releasedOn,
                :eolOn,
                :eeolOn,
                :latestVersion,
                :supportState,
                :sourceName,
                :sourceUrl,
                :notes,
                :status,
                false,
                :actor,
                now(),
                :actor
            )
            ON CONFLICT (platform_code, cycle)
            DO UPDATE SET
                os_type = EXCLUDED.os_type,
                os_name = EXCLUDED.os_name,
                released_on = EXCLUDED.released_on,
                eol_on = EXCLUDED.eol_on,
                eeol_on = EXCLUDED.eeol_on,
                latest_version = EXCLUDED.latest_version,
                support_state = EXCLUDED.support_state,
                source_name = EXCLUDED.source_name,
                source_url = EXCLUDED.source_url,
                notes = EXCLUDED.notes,
                status = EXCLUDED.status,
                is_deleted = false,
                modified_at = now(),
                modified_by = EXCLUDED.modified_by
            """;

    private static final String APP_CATALOG_UPSERT_SQL = """
            INSERT INTO application_catalog (
                os_type,
                package_id,
                app_name,
                publisher,
                created_at,
                modified_at,
                is_deleted
            )
            VALUES (
                :osType,
                :packageId,
                :appName,
                :publisher,
                now(),
                now(),
                false
            )
            ON CONFLICT (os_type, package_id_norm, app_name_norm)
            DO UPDATE SET
                package_id = EXCLUDED.package_id,
                app_name = EXCLUDED.app_name,
                publisher = COALESCE(EXCLUDED.publisher, application_catalog.publisher),
                modified_at = now(),
                is_deleted = false
            """;

    private static final String USER_AGENT = "24Online-MDM-ReferenceSync/1.0";
    private static final String SYNC_STATE_UPSERT_SQL = """
            INSERT INTO reference_data_sync_state (
                id,
                trigger,
                started_at,
                finished_at,
                lifecycle_upserts,
                app_catalog_upserts,
                ios_enriched_rows,
                success,
                errors_json,
                updated_at
            )
            VALUES (
                1,
                :trigger,
                :startedAt,
                :finishedAt,
                :lifecycleUpserts,
                :appCatalogUpserts,
                :iosEnrichedRows,
                :success,
                CAST(:errorsJson AS jsonb),
                now()
            )
            ON CONFLICT (id)
            DO UPDATE SET
                trigger = EXCLUDED.trigger,
                started_at = EXCLUDED.started_at,
                finished_at = EXCLUDED.finished_at,
                lifecycle_upserts = EXCLUDED.lifecycle_upserts,
                app_catalog_upserts = EXCLUDED.app_catalog_upserts,
                ios_enriched_rows = EXCLUDED.ios_enriched_rows,
                success = EXCLUDED.success,
                errors_json = EXCLUDED.errors_json,
                updated_at = now()
            """;
    private static final String SYNC_STATE_SELECT_SQL = """
            SELECT
                trigger,
                started_at,
                finished_at,
                lifecycle_upserts,
                app_catalog_upserts,
                ios_enriched_rows,
                success,
                errors_json
            FROM reference_data_sync_state
            WHERE id = 1
            """;

    private static final Set<String> APP_IOS_SLUGS = Set.of("ios", "ipados", "watchos", "tvos");
    private static final Set<String> APP_LINUXISH_SLUGS = Set.of(
            "ubuntu", "debian", "rhel", "centos", "centos-stream",
            "rocky-linux", "almalinux", "oracle-linux", "sles",
            "opensuse", "arch", "kali", "alpine", "fedora",
            "freebsd", "openbsd", "netbsd"
    );

    private static final List<LifecyclePlatform> LIFECYCLE_PLATFORMS = List.of(
            new LifecyclePlatform("android", "ANDROID", "ANDROID", null),
            new LifecyclePlatform("ios", "IOS", "IOS", null),
            new LifecyclePlatform("macos", "MACOS", "MACOS", null),
            new LifecyclePlatform("windows", "WINDOWS", "WINDOWS", null),
            new LifecyclePlatform("windows-server", "WINDOWS_SERVER", "WINDOWS", null),
            new LifecyclePlatform("ubuntu", "UBUNTU", "LINUX", "UBUNTU"),
            new LifecyclePlatform("debian", "DEBIAN", "LINUX", "DEBIAN"),
            new LifecyclePlatform("rhel", "RHEL", "LINUX", "RHEL"),
            new LifecyclePlatform("centos", "CENTOS", "LINUX", "CENTOS"),
            new LifecyclePlatform("centos-stream", "CENTOS_STREAM", "LINUX", "CENTOS"),
            new LifecyclePlatform("rocky-linux", "ROCKY", "LINUX", "ROCKY"),
            new LifecyclePlatform("almalinux", "ALMALINUX", "LINUX", "ALMALINUX"),
            new LifecyclePlatform("oracle-linux", "ORACLE_LINUX", "LINUX", "OTHER"),
            new LifecyclePlatform("sles", "SUSE", "LINUX", "SUSE"),
            new LifecyclePlatform("opensuse", "OPENSUSE", "LINUX", "OPENSUSE"),
            new LifecyclePlatform("arch", "ARCH", "LINUX", "ARCH"),
            new LifecyclePlatform("kali", "KALI", "LINUX", "KALI"),
            new LifecyclePlatform("alpine", "ALPINE", "LINUX", "OTHER"),
            new LifecyclePlatform("fedora", "FEDORA", "LINUX", "FEDORA"),
            new LifecyclePlatform("freebsd", "FREEBSD", "FREEBSD", null),
            new LifecyclePlatform("openbsd", "OPENBSD", "OPENBSD", null)
    );

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final ReferenceDataSyncProperties properties;
    private final HttpClient httpClient;
    private final AtomicReference<SyncReport> latestReport = new AtomicReference<>();

    public ReferenceDataSyncService(NamedParameterJdbcTemplate jdbc,
                                    ObjectMapper objectMapper,
                                    ResourceLoader resourceLoader,
                                    ReferenceDataSyncProperties properties) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public SyncReport syncAll(String trigger) {
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<String> errors = new ArrayList<>();
        int lifecycleUpserts = 0;
        int catalogUpserts = 0;
        int iosEnriched = 0;

        String actor = clean(properties.getActor());
        if (actor == null) {
            actor = "system-seed";
        }

        if (!properties.isEnabled()) {
            return finalizeReport(new SyncReport(
                    trigger,
                    startedAt,
                    OffsetDateTime.now(ZoneOffset.UTC),
                    0,
                    0,
                    0,
                    List.of("Reference sync disabled")
            ));
        }

        if (properties.getOsLifecycle().isEnabled()) {
            try {
                lifecycleUpserts = syncOsLifecycle(actor);
            } catch (Exception ex) {
                String message = "OS lifecycle sync failed: " + ex.getMessage();
                log.error(message, ex);
                errors.add(message);
            }
        }

        if (properties.getAppCatalog().isEnabled()) {
            try {
                CatalogSyncResult result = syncAppCatalog();
                catalogUpserts = result.upserts();
                iosEnriched = result.iosEnriched();
            } catch (Exception ex) {
                String message = "Application catalog sync failed: " + ex.getMessage();
                log.error(message, ex);
                errors.add(message);
            }
        }

        OffsetDateTime finishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return finalizeReport(new SyncReport(
                trigger,
                startedAt,
                finishedAt,
                lifecycleUpserts,
                catalogUpserts,
                iosEnriched,
                List.copyOf(errors)
        ));
    }

    public Optional<SyncReport> latestReport() {
        SyncReport inMemory = latestReport.get();
        if (inMemory != null) {
            return Optional.of(inMemory);
        }

        Optional<SyncReport> loaded = loadLatestReportFromDb();
        loaded.ifPresent(report -> latestReport.compareAndSet(null, report));
        return Optional.ofNullable(latestReport.get());
    }

    private SyncReport finalizeReport(SyncReport report) {
        latestReport.set(report);
        persistLatestReport(report);
        return report;
    }

    private void persistLatestReport(SyncReport report) {
        String errorsJson = "[]";
        List<String> errors = report.errors() == null ? List.of() : report.errors();
        try {
            errorsJson = objectMapper.writeValueAsString(errors);
        } catch (Exception ex) {
            log.warn("Unable to serialize reference sync errors for persistence: {}", ex.getMessage());
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("trigger", report.trigger())
                .addValue("startedAt", report.startedAt())
                .addValue("finishedAt", report.finishedAt())
                .addValue("lifecycleUpserts", report.lifecycleUpserts())
                .addValue("appCatalogUpserts", report.appCatalogUpserts())
                .addValue("iosEnrichedRows", report.iosEnrichedRows())
                .addValue("success", report.success())
                .addValue("errorsJson", errorsJson);
        try {
            jdbc.update(SYNC_STATE_UPSERT_SQL, params);
        } catch (DataAccessException ex) {
            log.warn("Unable to persist reference sync state: {}", ex.getMessage());
        }
    }

    private Optional<SyncReport> loadLatestReportFromDb() {
        try {
            List<SyncReport> reports = jdbc.query(SYNC_STATE_SELECT_SQL, (rs, rowNum) -> {
                OffsetDateTime startedAt = rs.getObject("started_at", OffsetDateTime.class);
                OffsetDateTime finishedAt = rs.getObject("finished_at", OffsetDateTime.class);
                List<String> errors = parseErrorsJson(rs.getString("errors_json"));

                return new SyncReport(
                        rs.getString("trigger"),
                        startedAt,
                        finishedAt,
                        rs.getInt("lifecycle_upserts"),
                        rs.getInt("app_catalog_upserts"),
                        rs.getInt("ios_enriched_rows"),
                        errors
                );
            });
            if (reports.isEmpty()) {
                return Optional.empty();
            }
            return Optional.ofNullable(reports.getFirst());
        } catch (DataAccessException ex) {
            log.warn("Unable to load persisted reference sync state: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private List<String> parseErrorsJson(String rawErrorsJson) {
        String safeJson = clean(rawErrorsJson);
        if (safeJson == null) {
            return List.of();
        }
        try {
            TypeReference<List<String>> type = new TypeReference<>() {
            };
            List<String> parsed = objectMapper.readValue(safeJson, type);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (Exception ex) {
            log.warn("Unable to parse persisted reference sync errors: {}", ex.getMessage());
            return List.of();
        }
    }

    private int syncOsLifecycle(String actor) {
        OsLifecycle config = properties.getOsLifecycle();
        int upserts = 0;
        int retries = Math.max(1, config.getRetries());
        int timeoutSeconds = Math.max(5, config.getRequestTimeoutSeconds());
        int restoreFromYear = Math.max(1970, config.getRestoreFromYear());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (LifecyclePlatform platform : LIFECYCLE_PLATFORMS) {
            String url = "https://endoflife.date/api/" + platform.slug() + ".json";
            JsonNode root = fetchJson(url, retries, timeoutSeconds).orElse(null);
            if (root == null || !root.isArray()) {
                log.warn("Lifecycle sync: skipping {} due to empty response", platform.slug());
                continue;
            }

            for (JsonNode node : root) {
                String cycle = clean(node.path("cycle").asText(null));
                if (cycle == null) {
                    continue;
                }

                LocalDate releasedOn = parseDate(clean(node.path("releaseDate").asText(null)));
                if (releasedOn != null && releasedOn.getYear() < restoreFromYear) {
                    continue;
                }

                LocalDate eolOn = parseDate(clean(node.path("eol").asText(null)));
                LocalDate eeolOn = parseDate(clean(node.path("extendedSupport").asText(null)));

                String supportState = (eolOn == null) ? "SUPPORTED" : "TRACKED";
                if (eolOn == null && eeolOn != null) {
                    supportState = "SUPPORTED";
                }

                String latestVersion = firstNonBlank(
                        clean(node.path("latest").asText(null)),
                        clean(node.path("latestReleaseDate").asText(null))
                );

                String lifecycleStatus = "ACTIVE";
                if (eolOn != null && eeolOn == null && today.isAfter(eolOn)) {
                    lifecycleStatus = "ACTIVE";
                } else if (eeolOn != null && today.isAfter(eeolOn)) {
                    lifecycleStatus = "ACTIVE";
                }

                MapSqlParameterSource params = new MapSqlParameterSource()
                        .addValue("platformCode", platform.platformCode())
                        .addValue("osType", platform.osType())
                        .addValue("osName", platform.osName())
                        .addValue("cycle", cycle)
                        .addValue("releasedOn", releasedOn)
                        .addValue("eolOn", eolOn)
                        .addValue("eeolOn", eeolOn)
                        .addValue("latestVersion", latestVersion)
                        .addValue("supportState", supportState)
                        .addValue("sourceName", "endoflife.date")
                        .addValue("sourceUrl", "https://endoflife.date/" + platform.slug())
                        .addValue("notes", "Auto-synced by scheduler")
                        .addValue("status", lifecycleStatus)
                        .addValue("actor", actor);
                jdbc.update(LIFECYCLE_UPSERT_SQL, params);
                upserts += 1;
            }
        }

        return upserts;
    }

    private CatalogSyncResult syncAppCatalog() {
        AppCatalog config = properties.getAppCatalog();
        int retries = Math.max(1, config.getRetries());
        int timeoutSeconds = Math.max(5, config.getRequestTimeoutSeconds());
        boolean enrichIos = config.isEnrichIosFromItunes();

        Map<String, List<String>> appMap = loadAppMap(config.getAppMapLocation());
        int upserts = 0;
        int iosEnriched = 0;

        for (Map.Entry<String, List<String>> entry : appMap.entrySet()) {
            String slug = clean(entry.getKey());
            if (slug == null) {
                continue;
            }
            String normalizedSlug = slug.toLowerCase(Locale.ROOT);
            String osType = classifyCatalogOsType(normalizedSlug);
            if (osType == null) {
                continue;
            }
            List<String> packageIds = entry.getValue();
            if (packageIds == null || packageIds.isEmpty()) {
                continue;
            }

            for (String rawPackageId : packageIds) {
                String packageId = clean(rawPackageId);
                if (packageId == null) {
                    continue;
                }

                String appName = packageId;
                String publisher = null;

                if (enrichIos && "IOS".equals(osType)) {
                    Optional<IosLookup> lookup = lookupIosBundle(packageId, retries, timeoutSeconds);
                    if (lookup.isPresent()) {
                        IosLookup iosLookup = lookup.get();
                        if (iosLookup.appName() != null) {
                            appName = iosLookup.appName();
                        }
                        publisher = iosLookup.publisher();
                        iosEnriched += 1;
                    }
                }

                MapSqlParameterSource params = new MapSqlParameterSource()
                        .addValue("osType", osType)
                        .addValue("packageId", packageId)
                        .addValue("appName", appName)
                        .addValue("publisher", publisher);
                jdbc.update(APP_CATALOG_UPSERT_SQL, params);
                upserts += 1;
            }
        }

        return new CatalogSyncResult(upserts, iosEnriched);
    }

    private Map<String, List<String>> loadAppMap(String location) {
        String normalizedLocation = clean(location);
        if (normalizedLocation == null) {
            normalizedLocation = "classpath:sync/app_map.json";
        }
        Resource resource = resourceLoader.getResource(normalizedLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("App map file not found: " + normalizedLocation);
        }

        try (InputStream in = resource.getInputStream()) {
            TypeReference<LinkedHashMap<String, List<String>>> type = new TypeReference<>() {
            };
            Map<String, List<String>> loaded = objectMapper.readValue(in, type);
            if (loaded == null) {
                return Map.of();
            }
            LinkedHashMap<String, List<String>> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : loaded.entrySet()) {
                String key = clean(entry.getKey());
                if (key == null) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                if (entry.getValue() != null) {
                    for (String item : entry.getValue()) {
                        String packageId = clean(item);
                        if (packageId != null) {
                            values.add(packageId);
                        }
                    }
                }
                normalized.put(key, values);
            }
            return normalized;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read app map: " + normalizedLocation, ex);
        }
    }

    private Optional<IosLookup> lookupIosBundle(String bundleId, int retries, int timeoutSeconds) {
        String url = "https://itunes.apple.com/lookup?bundleId=" + bundleId;
        JsonNode root = fetchJson(url, retries, timeoutSeconds).orElse(null);
        if (root == null) {
            return Optional.empty();
        }
        JsonNode results = root.path("results");
        if (!results.isArray() || results.isEmpty()) {
            return Optional.empty();
        }
        JsonNode first = results.get(0);
        String appName = clean(first.path("trackName").asText(null));
        String publisher = firstNonBlank(
                clean(first.path("sellerName").asText(null)),
                clean(first.path("artistName").asText(null))
        );
        if (appName == null && publisher == null) {
            return Optional.empty();
        }
        return Optional.of(new IosLookup(appName, publisher));
    }

    private Optional<JsonNode> fetchJson(String url, int retries, int timeoutSeconds) {
        for (int attempt = 1; attempt <= retries; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .header("Accept", "application/json")
                        .header("User-Agent", USER_AGENT)
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    log.warn("Reference sync HTTP {} for {}", response.statusCode(), url);
                    continue;
                }
                return Optional.ofNullable(objectMapper.readTree(response.body()));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while fetching " + url, ex);
            } catch (Exception ex) {
                if (attempt >= retries) {
                    log.warn("Reference sync failed for {} after {} attempt(s): {}", url, retries, ex.getMessage());
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    private LocalDate parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (Exception _) {
            return null;
        }
    }

    private String classifyCatalogOsType(String slug) {
        if (slug == null || slug.isBlank()) {
            return null;
        }
        if (slug.startsWith("windows")) {
            return "WINDOWS";
        }
        if ("android".equals(slug)) {
            return "ANDROID";
        }
        if ("macos".equals(slug)) {
            return "MACOS";
        }
        if (APP_IOS_SLUGS.contains(slug)) {
            return "IOS";
        }
        if (APP_LINUXISH_SLUGS.contains(slug)) {
            return "LINUX";
        }
        return null;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String out = value.trim();
        return out.isEmpty() ? null : out;
    }

    private String firstNonBlank(String left, String right) {
        if (left != null && !left.isBlank()) {
            return left;
        }
        if (right != null && !right.isBlank()) {
            return right;
        }
        return null;
    }

}
