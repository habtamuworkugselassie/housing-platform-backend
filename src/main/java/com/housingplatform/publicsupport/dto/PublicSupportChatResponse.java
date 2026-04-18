package com.housingplatform.publicsupport.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicSupportChatResponse {

  /** Assistant reply text */
  private String reply;

  /** true when the answer came from the configured AI provider */
  private boolean ai;
}
