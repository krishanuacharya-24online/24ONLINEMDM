package com.e24online.mdm.records.ui;

import java.util.List;
import java.util.Map;

public record DataTablePage(
        int draw,
        long recordsTotal,
        long recordsFiltered,
        List<Map<String, Object>> data
) {
}