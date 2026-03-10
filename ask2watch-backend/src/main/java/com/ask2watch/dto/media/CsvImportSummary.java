package com.ask2watch.dto.media;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CsvImportSummary {

    private int totalLines;
    private int imported;
    private int skipped;
    private int duplicates;
    private int errors;
}
