package com.e24online.mdm.web.dto;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AppCatalog {
    private boolean enabled = true;
    private String appMapLocation = "classpath:sync/app_map.json";
    private int requestTimeoutSeconds = 20;
    private int retries = 2;
    private boolean enrichIosFromItunes = true;
}