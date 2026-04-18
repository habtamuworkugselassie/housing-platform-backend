package com.housingplatform.publicsupport;

import com.housingplatform.publicsupport.dto.PublicSupportChatRequest;
import com.housingplatform.publicsupport.dto.PublicSupportChatResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/support")
@RequiredArgsConstructor
public class PublicSupportChatController {

  private final PublicSupportChatService publicSupportChatService;

  @PostMapping("/chat")
  public PublicSupportChatResponse chat(@Valid @RequestBody PublicSupportChatRequest request) {
    return publicSupportChatService.reply(request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<PublicSupportChatResponse> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(new PublicSupportChatResponse(e.getMessage(), false));
  }
}
