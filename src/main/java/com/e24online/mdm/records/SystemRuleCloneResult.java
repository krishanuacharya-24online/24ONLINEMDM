package com.e24online.mdm.records;

import com.e24online.mdm.domain.SystemInformationRule;

public record SystemRuleCloneResult(SystemInformationRule rule, int clonedConditions) {
}
