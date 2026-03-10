package com.ask2watch.dto.media;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CsvImportError {

    private int lineNumber;
    private String title;
    private String reason;
}
