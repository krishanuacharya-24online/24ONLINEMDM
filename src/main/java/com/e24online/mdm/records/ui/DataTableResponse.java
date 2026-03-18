package com.e24online.mdm.records.ui;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record DataTableResponse<T>(
        @JsonProperty("draw") int draw,
        @JsonProperty("recordsTotal") long recordsTotal,
        @JsonProperty("recordsFiltered") long recordsFiltered,
        @JsonProperty("data") List<T> data
) {
}
