import { mountDtCrud } from '../dt-crud.js';
import { LOOKUP_TYPES, populateLookupSelect } from '../lookups.js';
import { initPolicyScope } from '../policy-scope.js';
import { canWritePolicyRow, renderPolicyActionButtons } from '../policy-row-actions.js';

const OPERATOR_OPTIONS = ['EQ', 'NEQ', 'GT', 'GTE', 'LT', 'LTE', 'IN', 'NOT_IN', 'REGEX', 'EXISTS', 'NOT_EXISTS'];
const STATUS_OPTIONS = ['ACTIVE', 'INACTIVE'];

document.addEventListener('DOMContentLoaded', async () => {
  const table = document.getElementById('ruleConditionsTable');
  if (!table) return;

  const ruleId = table.dataset.ruleId;
  if (!ruleId) return;
  const scope = await initPolicyScope();

  await Promise.all([
    populateLookupSelect('operator', {
      lookupType: LOOKUP_TYPES.ruleConditionOperator,
      fallbackOptions: OPERATOR_OPTIONS
    }),
    populateLookupSelect('status', {
      lookupType: LOOKUP_TYPES.recordStatus,
      fallbackOptions: STATUS_OPTIONS
    })
  ]);

  const crud = mountDtCrud({
    tableSelector: '#ruleConditionsTable',
    ajaxUrl: '/v1/ui/datatables/system-rule-conditions',
    idField: 'id',
    createUrl: `/v1/policies/system-rules/${encodeURIComponent(ruleId)}/conditions`,
    updateUrl: (id) => `/v1/policies/system-rules/${encodeURIComponent(ruleId)}/conditions/${encodeURIComponent(id)}`,
    deleteUrl: (id) => `/v1/policies/system-rules/${encodeURIComponent(ruleId)}/conditions/${encodeURIComponent(id)}`,
    requestHeaders: () => scope.getHeaders(),
    formId: 'ruleConditionsForm',
    resetId: 'resetBtn',
    fieldIds: [
      'condition_group',
      'field_name',
      'operator',
      'value_text',
      'value_numeric',
      'value_boolean',
      'status'
    ],
    defaults: () => ({
      condition_group: 1,
      operator: 'EQ',
      status: 'ACTIVE',
      value_boolean: false
    }),
    tableOptions: {
      defaultSortBy: 'id',
      defaultSortDir: 'desc',
      extraParams: () => ({ rule_id: ruleId })
    },
    columns: [
      { data: 'id' },
      { data: 'condition_group' },
      { data: 'field_name' },
      { data: 'operator' },
      { data: 'value_text' },
      { data: 'value_numeric' },
      { data: 'value_boolean' },
      { data: 'status' },
      {
        data: null,
        orderable: false,
        render: (_value, _type, row) => renderPolicyActionButtons(scope, row, { showClone: false })
      }
    ],
    canEditRow: (row) => canWritePolicyRow(scope, row),
    canDeleteRow: (row) => canWritePolicyRow(scope, row)
  });

  scope.onChange(() => {
    crud.reload();
    crud.reset();
  });
});
