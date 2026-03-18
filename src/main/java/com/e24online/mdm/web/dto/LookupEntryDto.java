package com.e24online.mdm.web.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class LookupEntryDto {
    private String lookupType;
    private String code;
    private String description;
}