package com.e24online.mdm.service;

import com.e24online.mdm.records.ui.DataTablePage;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Types;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class UiDataTableService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Map<String, Boolean> columnExistsCache = new ConcurrentHashMap<>();

    public UiDataTableService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public DataTablePage systemRules(int draw, int start, int length, String tenantId, String status,
                                     String search, String sortBy, String sortDir) {
        boolean tenantScoped = hasColumn("system_information_rule", "tenant_id");
        String selectSql = tenantScoped
                ? """
                        SELECT sir.id, sir.rule_code, sir.os_type, sir.device_type, sir.status, sir.priority, sir.tenant_id
                        """
                : """
                        SELECT sir.id, sir.rule_code, sir.os_type, sir.device_type, sir.status, sir.priority, CAST(NULL AS TEXT) AS tenant_id
                        """;
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                selectSql,
                tenantScoped
                        ? """
                        FROM system_information_rule sir
                        WHERE sir.is_deleted = false
                          AND sir.status = COALESCE(:status, sir.status)
                          AND (
                            (CAST(:tenantId AS TEXT) IS NULL AND sir.tenant_id IS NULL)
                            OR (
                              CAST(:tenantId AS TEXT) IS NOT NULL
                              AND (sir.tenant_id IS NULL OR LOWER(sir.tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                            )
                          )
                        """
                        : """
                        FROM system_information_rule sir
                        WHERE sir.is_deleted = false
                          AND sir.status = COALESCE(:status, sir.status)
                        """,
                tenantScoped
                        ? params("tenantId", emptyToNull(tenantId), "status", emptyToNull(status))
                        : params("status", emptyToNull(status)),
                List.of("sir.rule_code", "sir.os_type", "sir.device_type", "sir.status", "sir.description"),
                sortable(
                        "id", "sir.id",
                        "rule_code", "sir.rule_code",
                        "os_type", "sir.os_type",
                        "device_type", "sir.device_type",
                        "status", "sir.status",
                        "priority", "sir.priority"
                ),
                "sir.id",
                "DESC"
        );
    }

    public DataTablePage rejectApps(int draw, int start, int length, String tenantId, String osType, String status,
                                    String search, String sortBy, String sortDir) {
        boolean tenantScoped = hasColumn("reject_application_list", "tenant_id");
        String selectSql = tenantScoped
                ? """
                        SELECT ral.id, ral.app_os_type, ral.app_name, ral.package_id, ral.severity, ral.status, ral.tenant_id
                        """
                : """
                        SELECT ral.id, ral.app_os_type, ral.app_name, ral.package_id, ral.severity, ral.status, CAST(NULL AS TEXT) AS tenant_id
                        """;
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                selectSql,
                tenantScoped
                        ? """
                        FROM reject_application_list ral
                        WHERE ral.is_deleted = false
                          AND ral.app_os_type = COALESCE(:osType, ral.app_os_type)
                          AND ral.status = COALESCE(:status, ral.status)
                          AND (
                            (CAST(:tenantId AS TEXT) IS NULL AND ral.tenant_id IS NULL)
                            OR (
                              CAST(:tenantId AS TEXT) IS NOT NULL
                              AND (ral.tenant_id IS NULL OR LOWER(ral.tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                            )
                          )
                        """
                        : """
                        FROM reject_application_list ral
                        WHERE ral.is_deleted = false
                          AND ral.app_os_type = COALESCE(:osType, ral.app_os_type)
                          AND ral.status = COALESCE(:status, ral.status)
                        """,
                tenantScoped
                        ? params("tenantId", emptyToNull(tenantId), "osType", emptyToNull(osType), "status", emptyToNull(status))
                        : params("osType", emptyToNull(osType), "status", emptyToNull(status)),
                List.of("ral.policy_tag", "ral.threat_type", "ral.app_os_type", "ral.app_name", "ral.package_id", "ral.status", "ral.app_category"),
                sortable(
                        "id", "ral.id",
                        "app_os_type", "ral.app_os_type",
                        "app_name", "ral.app_name",
                        "package_id", "ral.package_id",
                        "severity", "ral.severity",
                        "status", "ral.status"
                ),
                "ral.id",
                "DESC"
        );
    }

    public DataTablePage trustScorePolicies(int draw, int start, int length, String tenantId, String status,
                                            String search, String sortBy, String sortDir) {
        boolean tenantScoped = hasColumn("trust_score_policy", "tenant_id");
        String selectSql = tenantScoped
                ? """
                        SELECT tsp.id, tsp.policy_code, tsp.source_type, tsp.signal_key, tsp.score_delta, tsp.status, tsp.tenant_id
                        """
                : """
                        SELECT tsp.id, tsp.policy_code, tsp.source_type, tsp.signal_key, tsp.score_delta, tsp.status, CAST(NULL AS TEXT) AS tenant_id
                        """;
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                selectSql,
                tenantScoped
                        ? """
                        FROM trust_score_policy tsp
                        WHERE tsp.is_deleted = false
                          AND tsp.status = COALESCE(:status, tsp.status)
                          AND (
                            (CAST(:tenantId AS TEXT) IS NULL AND tsp.tenant_id IS NULL)
                            OR (
                              CAST(:tenantId AS TEXT) IS NOT NULL
                              AND (tsp.tenant_id IS NULL OR LOWER(tsp.tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                            )
                          )
                        """
                        : """
                        FROM trust_score_policy tsp
                        WHERE tsp.is_deleted = false
                          AND tsp.status = COALESCE(:status, tsp.status)
                        """,
                tenantScoped
                        ? params("tenantId", emptyToNull(tenantId), "status", emptyToNull(status))
                        : params("status", emptyToNull(status)),
                List.of("tsp.policy_code", "tsp.source_type", "tsp.signal_key", "tsp.status"),
                sortable(
                        "id", "tsp.id",
                        "policy_code", "tsp.policy_code",
                        "source_type", "tsp.source_type",
                        "signal_key", "tsp.signal_key",
                        "score_delta", "tsp.score_delta",
                        "status", "tsp.status"
                ),
                "tsp.id",
                "DESC"
        );
    }

    public DataTablePage trustDecisionPolicies(int draw, int start, int length, String tenantId, String status,
                                               String search, String sortBy, String sortDir) {
        boolean tenantScoped = hasColumn("trust_score_decision_policy", "tenant_id");
        String selectSql = tenantScoped
                ? """
                        SELECT tsdp.id, tsdp.policy_name, tsdp.score_min, tsdp.score_max, tsdp.decision_action, tsdp.status, tsdp.tenant_id
                        """
                : """
                        SELECT tsdp.id, tsdp.policy_name, tsdp.score_min, tsdp.score_max, tsdp.decision_action, tsdp.status, CAST(NULL AS TEXT) AS tenant_id
                        """;
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                selectSql,
                tenantScoped
                        ? """
                        FROM trust_score_decision_policy tsdp
                        WHERE tsdp.is_deleted = false
                          AND tsdp.status = COALESCE(:status, tsdp.status)
                          AND (
                            (CAST(:tenantId AS TEXT) IS NULL AND tsdp.tenant_id IS NULL)
                            OR (
                              CAST(:tenantId AS TEXT) IS NOT NULL
                              AND (tsdp.tenant_id IS NULL OR LOWER(tsdp.tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                            )
                          )
                        """
                        : """
                        FROM trust_score_decision_policy tsdp
                        WHERE tsdp.is_deleted = false
                          AND tsdp.status = COALESCE(:status, tsdp.status)
                        """,
                tenantScoped
                        ? params("tenantId", emptyToNull(tenantId), "status", emptyToNull(status))
                        : params("status", emptyToNull(status)),
                List.of("tsdp.policy_name", "tsdp.decision_action", "tsdp.status"),
                sortable(
                        "id", "tsdp.id",
                        "policy_name", "tsdp.policy_name",
                        "score_min", "tsdp.score_min",
                        "score_max", "tsdp.score_max",
                        "decision_action", "tsdp.decision_action",
                        "status", "tsdp.status"
                ),
                "tsdp.id",
                "DESC"
        );
    }

    public DataTablePage remediationRules(int draw, int start, int length, String tenantId, String status,
                                          String search, String sortBy, String sortDir) {
        boolean tenantScoped = hasColumn("remediation_rule", "tenant_id");
        String selectSql = tenantScoped
                ? """
                        SELECT rr.id, rr.remediation_code, rr.remediation_type, rr.os_type, rr.device_type, rr.status, rr.tenant_id
                        """
                : """
                        SELECT rr.id, rr.remediation_code, rr.remediation_type, rr.os_type, rr.device_type, rr.status, CAST(NULL AS TEXT) AS tenant_id
                        """;
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                selectSql,
                tenantScoped
                        ? """
                        FROM remediation_rule rr
                        WHERE rr.is_deleted = false
                          AND rr.status = COALESCE(:status, rr.status)
                          AND (
                            (CAST(:tenantId AS TEXT) IS NULL AND rr.tenant_id IS NULL)
                            OR (
                              CAST(:tenantId AS TEXT) IS NOT NULL
                              AND (rr.tenant_id IS NULL OR LOWER(rr.tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                            )
                          )
                        """
                        : """
                        FROM remediation_rule rr
                        WHERE rr.is_deleted = false
                          AND rr.status = COALESCE(:status, rr.status)
                        """,
                tenantScoped
                        ? params("tenantId", emptyToNull(tenantId), "status", emptyToNull(status))
                        : params("status", emptyToNull(status)),
                List.of("rr.remediation_code", "rr.title", "rr.description", "rr.remediation_type", "rr.os_type", "rr.device_type", "rr.status"),
                sortable(
                        "id", "rr.id",
                        "remediation_code", "rr.remediation_code",
                        "remediation_type", "rr.remediation_type",
                        "os_type", "rr.os_type",
                        "device_type", "rr.device_type",
                        "status", "rr.status"
                ),
                "rr.id",
                "DESC"
        );
    }

    public DataTablePage ruleRemediationMappings(int draw, int start, int length, String tenantId, String sourceType,
                                                 String search, String sortBy, String sortDir) {
        boolean tenantScoped = hasColumn("rule_remediation_mapping", "tenant_id");
        String selectSql = tenantScoped
                ? """
                        SELECT rrm.id, rrm.source_type, rrm.system_information_rule_id, rrm.reject_application_list_id,
                               rrm.trust_score_policy_id, rrm.decision_action, rrm.remediation_rule_id,
                               rrm.enforce_mode, rrm.rank_order, rrm.status, rrm.tenant_id
                        """
                : """
                        SELECT rrm.id, rrm.source_type, rrm.system_information_rule_id, rrm.reject_application_list_id,
                               rrm.trust_score_policy_id, rrm.decision_action, rrm.remediation_rule_id,
                               rrm.enforce_mode, rrm.rank_order, rrm.status, CAST(NULL AS TEXT) AS tenant_id
                        """;
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                selectSql,
                tenantScoped
                        ? """
                        FROM rule_remediation_mapping rrm
                        WHERE rrm.is_deleted = false
                          AND rrm.source_type = COALESCE(:sourceType, rrm.source_type)
                          AND (
                            (CAST(:tenantId AS TEXT) IS NULL AND rrm.tenant_id IS NULL)
                            OR (
                              CAST(:tenantId AS TEXT) IS NOT NULL
                              AND (rrm.tenant_id IS NULL OR LOWER(rrm.tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                            )
                          )
                        """
                        : """
                        FROM rule_remediation_mapping rrm
                        WHERE rrm.is_deleted = false
                          AND rrm.source_type = COALESCE(:sourceType, rrm.source_type)
                        """,
                tenantScoped
                        ? params("tenantId", emptyToNull(tenantId), "sourceType", emptyToNull(sourceType))
                        : params("sourceType", emptyToNull(sourceType)),
                List.of("rrm.source_type", "rrm.decision_action", "rrm.status"),
                sortable(
                        "id", "rrm.id",
                        "source_type", "rrm.source_type",
                        "system_information_rule_id", "rrm.system_information_rule_id",
                        "reject_application_list_id", "rrm.reject_application_list_id",
                        "trust_score_policy_id", "rrm.trust_score_policy_id",
                        "remediation_rule_id", "rrm.remediation_rule_id"
                ),
                "rrm.id",
                "DESC"
        );
    }

    public DataTablePage policyAudit(int draw, int start, int length, String tenantId, String policyType,
                                     String operation, String actor, String search, String sortBy, String sortDir) {
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                """
                        SELECT pca.id, pca.created_at, pca.policy_type, pca.policy_id, pca.operation, pca.tenant_id, pca.actor, pca.approval_ticket
                        """,
                """
                        FROM policy_change_audit pca
                        WHERE (CAST(:policyType AS TEXT) IS NULL OR pca.policy_type = CAST(:policyType AS TEXT))
                          AND (CAST(:operation AS TEXT) IS NULL OR pca.operation = CAST(:operation AS TEXT))
                          AND (CAST(:actor AS TEXT) IS NULL OR LOWER(pca.actor) = LOWER(CAST(:actor AS TEXT)))
                          AND (
                              CAST(:tenantId AS TEXT) IS NULL
                              OR pca.tenant_id IS NULL
                              OR LOWER(pca.tenant_id) = LOWER(CAST(:tenantId AS TEXT))
                          )
                        """,
                params(
                        "tenantId", emptyToNull(tenantId),
                        "policyType", emptyToNull(policyType),
                        "operation", emptyToNull(operation),
                        "actor", emptyToNull(actor)
                ),
                List.of("pca.policy_type", "pca.policy_id", "pca.operation", "pca.tenant_id", "pca.actor", "pca.approval_ticket"),
                sortable(
                        "id", "pca.id",
                        "created_at", "pca.created_at",
                        "policy_type", "pca.policy_type",
                        "policy_id", "pca.policy_id",
                        "operation", "pca.operation",
                        "tenant_id", "pca.tenant_id",
                        "actor", "pca.actor"
                ),
                "pca.created_at",
                "DESC"
        );
    }

    public DataTablePage auditEvents(int draw, int start, int length, String tenantId, String eventCategory,
                                     String eventType, String action, String status, String actor, String search,
                                     String sortBy, String sortDir) {
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                """
                        SELECT ael.id, ael.created_at, ael.event_category, ael.event_type, ael.action,
                               ael.tenant_id, ael.actor, ael.entity_type, ael.entity_id, ael.status, ael.metadata_json
                        """,
                """
                        FROM audit_event_log ael
                        WHERE (CAST(:eventCategory AS TEXT) IS NULL OR ael.event_category = CAST(:eventCategory AS TEXT))
                          AND (CAST(:eventType AS TEXT) IS NULL OR ael.event_type = CAST(:eventType AS TEXT))
                          AND (CAST(:action AS TEXT) IS NULL OR ael.action = CAST(:action AS TEXT))
                          AND (CAST(:status AS TEXT) IS NULL OR ael.status = CAST(:status AS TEXT))
                          AND (CAST(:actor AS TEXT) IS NULL OR LOWER(ael.actor) = LOWER(CAST(:actor AS TEXT)))
                          AND (
                              CAST(:tenantId AS TEXT) IS NULL
                              OR ael.tenant_id IS NULL
                              OR LOWER(ael.tenant_id) = LOWER(CAST(:tenantId AS TEXT))
                          )
                        """,
                params(
                        "tenantId", emptyToNull(tenantId),
                        "eventCategory", emptyToNull(eventCategory),
                        "eventType", emptyToNull(eventType),
                        "action", emptyToNull(action),
                        "status", emptyToNull(status),
                        "actor", emptyToNull(actor)
                ),
                List.of(
                        "ael.event_category",
                        "ael.event_type",
                        "ael.action",
                        "ael.tenant_id",
                        "ael.actor",
                        "ael.entity_type",
                        "ael.entity_id",
                        "ael.status",
                        "ael.metadata_json"
                ),
                sortable(
                        "id", "ael.id",
                        "created_at", "ael.created_at",
                        "event_category", "ael.event_category",
                        "event_type", "ael.event_type",
                        "action", "ael.action",
                        "tenant_id", "ael.tenant_id",
                        "actor", "ael.actor",
                        "entity_type", "ael.entity_type",
                        "entity_id", "ael.entity_id",
                        "status", "ael.status"
                ),
                "ael.created_at",
                "DESC"
        );
    }

    public DataTablePage catalogApplications(int draw, int start, int length, String osType, String search,
                                             String sortBy, String sortDir) {
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                """
                        SELECT id, os_type, package_id, app_name, publisher
                        """,
                """
                        FROM application_catalog
                        WHERE os_type = COALESCE(:osType, os_type)
                        """,
                params("osType", emptyToNull(osType)),
                List.of("os_type", "package_id", "app_name", "publisher"),
                sortable(
                        "id", "id",
                        "os_type", "os_type",
                        "package_id", "package_id",
                        "app_name", "app_name",
                        "publisher", "publisher"
                ),
                "id",
                "DESC"
        );
    }

    public DataTablePage osLifecycle(int draw, int start, int length, String platformCode, String search,
                                     String sortBy, String sortDir) {
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                """
                        SELECT id, platform_code, cycle, released_on, eol_on, support_state
                        """,
                """
                        FROM os_release_lifecycle_master
                        WHERE is_deleted = false
                          AND platform_code = COALESCE(:platformCode, platform_code)
                        """,
                params("platformCode", emptyToNull(platformCode)),
                List.of("platform_code", "os_type", "os_name", "cycle", "latest_version", "support_state"),
                sortable(
                        "id", "id",
                        "platform_code", "platform_code",
                        "cycle", "cycle",
                        "released_on", "released_on",
                        "eol_on", "eol_on",
                        "support_state", "support_state"
                ),
                "id",
                "DESC"
        );
    }

    public DataTablePage lookupValues(int draw, int start, int length, String lookupType, String search,
                                      String sortBy, String sortDir) {
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                """
                        SELECT lookup_type, code, description
                        """,
                """
                        FROM lkp_master
                        WHERE lookup_type = COALESCE(:lookupType, lookup_type)
                        """,
                params("lookupType", emptyToNull(lookupType)),
                List.of("lookup_type", "code", "description"),
                sortable(
                        "lookup_type", "lookup_type",
                        "code", "code",
                        "description", "description"
                ),
                "code",
                "ASC"
        );
    }

    public DataTablePage systemRuleConditions(int draw, int start, int length, String tenantId, Long ruleId, String search,
                                              String sortBy, String sortDir) {
        boolean tenantScoped = hasColumn("system_information_rule", "tenant_id");
        String selectSql = tenantScoped
                ? """
                        SELECT src.id, src.condition_group, src.field_name, src.operator, src.value_text, src.value_numeric, src.value_boolean, src.status, sir.tenant_id
                        """
                : """
                        SELECT src.id, src.condition_group, src.field_name, src.operator, src.value_text, src.value_numeric, src.value_boolean, src.status, CAST(NULL AS TEXT) AS tenant_id
                        """;
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                selectSql,
                tenantScoped
                        ? """
                        FROM system_information_rule_condition src
                        JOIN system_information_rule sir ON sir.id = src.system_information_rule_id
                        WHERE src.is_deleted = false
                          AND sir.is_deleted = false
                          AND src.system_information_rule_id = COALESCE(:ruleId, src.system_information_rule_id)
                          AND (
                            (CAST(:tenantId AS TEXT) IS NULL AND sir.tenant_id IS NULL)
                            OR (
                              CAST(:tenantId AS TEXT) IS NOT NULL
                              AND (sir.tenant_id IS NULL OR LOWER(sir.tenant_id) = LOWER(CAST(:tenantId AS TEXT)))
                            )
                          )
                        """
                        : """
                        FROM system_information_rule_condition src
                        JOIN system_information_rule sir ON sir.id = src.system_information_rule_id
                        WHERE src.is_deleted = false
                          AND sir.is_deleted = false
                          AND src.system_information_rule_id = COALESCE(:ruleId, src.system_information_rule_id)
                        """,
                tenantScoped
                        ? params("tenantId", emptyToNull(tenantId), "ruleId", ruleId)
                        : params("ruleId", ruleId),
                List.of("src.field_name", "src.operator", "src.value_text", "src.status"),
                sortable(
                        "id", "src.id",
                        "condition_group", "src.condition_group",
                        "field_name", "src.field_name",
                        "operator", "src.operator",
                        "value_text", "src.value_text",
                        "value_numeric", "src.value_numeric",
                        "value_boolean", "src.value_boolean",
                        "status", "src.status"
                ),
                "src.id",
                "DESC"
        );
    }

    public DataTablePage deviceTrustProfiles(int draw,
                                             int start,
                                             int length,
                                             String tenantId,
                                             Long ownerUserId,
                                             String osType,
                                             String scoreBand,
                                             String search,
                                             String sortBy,
                                             String sortDir) {
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                """
                        SELECT id, tenant_id, device_external_id, os_type, os_name, current_score, score_band, posture_status
                        """,
                """
                        FROM device_trust_profile dtp
                        WHERE dtp.is_deleted = false
                          AND COALESCE(dtp.tenant_id, '') = COALESCE(:tenantId, COALESCE(dtp.tenant_id, ''))
                          AND dtp.os_type = COALESCE(:osType, dtp.os_type)
                          AND dtp.score_band = COALESCE(:scoreBand, dtp.score_band)
                          AND (:ownerUserId IS NULL OR EXISTS (
                                SELECT 1
                                FROM device_enrollment de
                                WHERE COALESCE(de.tenant_id, '') = COALESCE(dtp.tenant_id, '')
                                  AND de.enrollment_no = dtp.device_external_id
                                  AND de.owner_user_id = :ownerUserId
                                  AND de.status = 'ACTIVE'
                          ))
                        """,
                params(
                        "tenantId", emptyToNull(tenantId),
                        "ownerUserId", ownerUserId,
                        "osType", emptyToNull(osType),
                        "scoreBand", emptyToNull(scoreBand)
                ),
                List.of("dtp.tenant_id", "dtp.device_external_id", "dtp.os_type", "dtp.os_name", "dtp.score_band", "dtp.posture_status"),
                sortable(
                        "id", "dtp.id",
                        "tenant_id", "dtp.tenant_id",
                        "device_external_id", "dtp.device_external_id",
                        "os_type", "dtp.os_type",
                        "current_score", "dtp.current_score",
                        "score_band", "dtp.score_band",
                        "posture_status", "dtp.posture_status"
                ),
                "dtp.id",
                "DESC"
        );
    }

    public DataTablePage posturePayloads(int draw, int start, int length, String tenantId, String processStatus,
                                         String search, String sortBy, String sortDir) {
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                """
                        SELECT id, tenant_id, device_external_id, process_status, received_at
                        """,
                """
                        FROM device_posture_payload
                        WHERE COALESCE(tenant_id, '') = COALESCE(:tenantId, COALESCE(tenant_id, ''))
                          AND process_status = COALESCE(:processStatus, process_status)
                        """,
                params("tenantId", emptyToNull(tenantId), "processStatus", emptyToNull(processStatus)),
                List.of("tenant_id", "device_external_id", "process_status", "payload_hash"),
                sortable(
                        "id", "id",
                        "tenant_id", "tenant_id",
                        "device_external_id", "device_external_id",
                        "process_status", "process_status",
                        "received_at", "received_at"
                ),
                "received_at",
                "DESC"
        );
    }

    public DataTablePage tenants(int draw, int start, int length, String status, String search, String sortBy,
                                 String sortDir) {
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                """
                        SELECT id, tenant_id, name, status, modified_at
                        """,
                """
                        FROM tenant_master
                        WHERE is_deleted = false
                          AND status = COALESCE(:status, status)
                        """,
                params("status", emptyToNull(status)),
                List.of("tenant_id", "name", "status"),
                sortable(
                        "id", "id",
                        "tenant_id", "tenant_id",
                        "name", "name",
                        "status", "status",
                        "modified_at", "modified_at"
                ),
                "id",
                "DESC"
        );
    }

    public DataTablePage users(int draw,
                               int start,
                               int length,
                               String role,
                               String status,
                               Long tenantMasterId,
                               boolean tenantScopedOnly,
                               String search,
                               String sortBy,
                               String sortDir) {
        return query(
                draw,
                start,
                length,
                search,
                sortBy,
                sortDir,
                """
                        SELECT
                            u.id,
                            u.username,
                            u.role,
                            u.status,
                            u.tenant_id AS tenant_master_id,
                            t.tenant_id,
                            u.modified_at
                        """,
                """
                        FROM auth_user u
                        LEFT JOIN tenant_master t ON t.id = u.tenant_id
                        WHERE u.is_deleted = false
                          AND u.role = COALESCE(:role, u.role)
                          AND u.status = COALESCE(:status, u.status)
                          AND (:tenantMasterId IS NULL OR u.tenant_id = :tenantMasterId)
                          AND (:tenantScopedOnly = false OR u.role = 'TENANT_USER')
                        """,
                params(
                        "role", emptyToNull(role),
                        "status", emptyToNull(status),
                        "tenantMasterId", tenantMasterId,
                        "tenantScopedOnly", tenantScopedOnly
                ),
                List.of("u.username", "u.role", "u.status", "t.tenant_id"),
                sortable(
                        "id", "u.id",
                        "username", "u.username",
                        "role", "u.role",
                        "status", "u.status",
                        "tenant_id", "t.tenant_id",
                        "modified_at", "u.modified_at"
                ),
                "u.id",
                "DESC"
        );
    }

    private DataTablePage query(int draw,
                                int start,
                                int length,
                                String search,
                                String sortBy,
                                String sortDir,
                                String selectSql,
                                String fromWhereSql,
                                Map<String, Object> baseParams,
                                List<String> searchableColumns,
                                Map<String, String> sortableColumns,
                                String defaultSortColumn,
                                String defaultSortDir) {
        int safeLength = clampLength(length);
        int safeStart = Math.max(start, 0);

        MapSqlParameterSource params = new MapSqlParameterSource();
        baseParams.forEach((k, v) -> addTypedParam(params, k, v));

        String totalCountSql = "SELECT COUNT(*) " + fromWhereSql;
        Long total = jdbc.queryForObject(totalCountSql, params, Long.class);
        long recordsTotal = total == null ? 0L : total;

        String whereSearch = buildSearchPredicate(searchableColumns, search, params);
        String orderColumn = resolveOrderColumn(sortBy, sortableColumns, defaultSortColumn);
        String orderDirection = resolveSortDirection(sortDir, defaultSortDir);

        long recordsFiltered = recordsTotal;
        if (!whereSearch.isBlank()) {
            String filteredCountSql = "SELECT COUNT(*) " + fromWhereSql + whereSearch;
            Long filtered = jdbc.queryForObject(filteredCountSql, params, Long.class);
            recordsFiltered = filtered == null ? 0L : filtered;
        }

        params.addValue("limit", safeLength);
        params.addValue("offset", safeStart);
        String dataSql = selectSql
                + fromWhereSql
                + whereSearch
                + " ORDER BY " + orderColumn + " " + orderDirection
                + " LIMIT :limit OFFSET :offset";

        List<Map<String, Object>> data = jdbc.queryForList(dataSql, params);
        return new DataTablePage(draw, recordsTotal, recordsFiltered, data);
    }

    private String buildSearchPredicate(List<String> searchableColumns, String search, MapSqlParameterSource params) {
        String term = emptyToNull(search);
        if (term == null || searchableColumns == null || searchableColumns.isEmpty()) {
            return "";
        }
        params.addValue("searchTerm", "%" + term.toLowerCase(Locale.ROOT) + "%");

        List<String> clauses = new ArrayList<>();
        for (String column : searchableColumns) {
            clauses.add("LOWER(CAST(" + column + " AS TEXT)) LIKE :searchTerm");
        }
        return " AND (" + String.join(" OR ", clauses) + ")";
    }

    private String resolveOrderColumn(String requestedSortBy, Map<String, String> sortableColumns,
                                      String defaultColumn) {
        String sort = emptyToNull(requestedSortBy);
        if (sort == null) {
            return defaultColumn;
        }
        return sortableColumns.getOrDefault(sort, defaultColumn);
    }

    private String resolveSortDirection(String requestedSortDir, String defaultDirection) {
        String dir = emptyToNull(requestedSortDir);
        if (dir == null) {
            return "DESC".equalsIgnoreCase(defaultDirection) ? "DESC" : "ASC";
        }
        return "DESC".equalsIgnoreCase(dir) ? "DESC" : "ASC";
    }

    private static int clampLength(int length) {
        if (length <= 0) {
            return 25;
        }
        return Math.min(length, 200);
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static Map<String, Object> params(Object... keyValuePairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(String.valueOf(keyValuePairs[i]), keyValuePairs[i + 1]);
        }
        return map;
    }

    private static void addTypedParam(MapSqlParameterSource params, String key, Object value) {
        if (value != null) {
            params.addValue(key, value);
            return;
        }
        if (isNumericParam(key)) {
            params.addValue(key, null, Types.BIGINT);
        } else {
            params.addValue(key, null, Types.VARCHAR);
        }
    }

    private static boolean isNumericParam(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return "ruleid".equals(normalized)
                || "tenantmasterid".equals(normalized)
                || "owneruserid".equals(normalized);
    }

    /**
     * Backward-compatible guard for environments that have not applied the
     * tenant-scope Flyway migration yet.
     */
    private boolean hasColumn(String tableName, String columnName) {
        if (tableName == null || tableName.isBlank() || columnName == null || columnName.isBlank()) {
            return false;
        }
        String key = tableName + "." + columnName;
        return columnExistsCache.computeIfAbsent(key, ignored -> {
            String sql = """
                    SELECT COUNT(*) > 0
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = :tableName
                      AND column_name = :columnName
                    """;
            Boolean present = jdbc.queryForObject(sql, params(
                    "tableName", tableName,
                    "columnName", columnName
            ), Boolean.class);
            return Boolean.TRUE.equals(present);
        });
    }

    private static Map<String, String> sortable(String... keyValuePairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValuePairs.length; i += 2) {
            map.put(keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return map;
    }


}
