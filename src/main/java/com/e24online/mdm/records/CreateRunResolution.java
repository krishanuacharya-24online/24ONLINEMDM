package com.e24online.mdm.records;

import com.e24online.mdm.domain.PostureEvaluationRun;
import org.springframework.http.HttpStatus;

public record CreateRunResolution(PostureEvaluationRun run, boolean evaluate, HttpStatus responseStatus) {
}
