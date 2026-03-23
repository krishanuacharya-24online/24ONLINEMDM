package com.e24online.mdm.web.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SimplePolicySimulationFinding {

    private String category;
    private String title;
    private String detail;
    private Short severity;
    private String action;
    private Short scoreDelta;
}
