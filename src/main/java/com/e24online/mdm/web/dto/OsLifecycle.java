package com.e24online.mdm.web.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class OsLifecycle {
    private boolean enabled = true;
    private int restoreFromYear = 2015;
    private int requestTimeoutSeconds = 20;
    private int retries = 3;
}