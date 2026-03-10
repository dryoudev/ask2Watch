package com.ask2watch.dto.media;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CsvImportResponse {

    private String message;
    private CsvImportSummary summary;
    private List<CsvImportError> errors;
}
