package com.housingplatform.publicsupport.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class PublicSupportChatRequest {

  @NotEmpty
  @Size(max = 24)
  @Valid
  private List<ChatMessageDto> messages;
}
