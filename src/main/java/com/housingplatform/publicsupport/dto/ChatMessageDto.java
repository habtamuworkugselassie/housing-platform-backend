package com.housingplatform.publicsupport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatMessageDto {

  @NotBlank
  @Pattern(regexp = "(?i)(user|assistant)")
  private String role;

  @NotBlank
  @Size(max = 4000)
  private String content;
}
