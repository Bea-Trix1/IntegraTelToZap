package com.consumertelo.consumer_to_zap.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class MessageDTO {

    @JsonProperty("messageId")
    private String id;

    private Long chatId;

    @NotBlank
    private String from;

    @NotBlank
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "'to' deve estar no formato E.164, ex.: +5511999999999")
    private String to;

    private String text;

    private String mediaUrl;

    private String mediaType;
}
