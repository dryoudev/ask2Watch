package com.ask2watch.dto.media;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PickResponse {

    private Long pickId;
    private MediaResponse media;
    private String weekDate;
    private boolean createdByAgent;
}
