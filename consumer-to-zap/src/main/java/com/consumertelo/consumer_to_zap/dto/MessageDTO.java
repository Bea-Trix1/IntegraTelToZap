package com.consumertelo.consumer_to_zap.dto;

import lombok.Data;

@Data
public class MessageDTO {
    private String from;
    private String to;
    private String text;
}
