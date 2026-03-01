package com.housingplatform.exhibition.api;

import com.housingplatform.exhibition.dto.ExhibitionInterestRequest;
import com.housingplatform.exhibition.dto.ExhibitionInterestResponse;
import com.housingplatform.exhibition.service.ExhibitionInterestService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/exhibition/interest")
@Tag(name = "Exhibition", description = "Exhibition interest (public)")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.UNSECURED)
public class ExhibitionInterestController {

  private final ExhibitionInterestService service;

  @PostMapping
  @Operation(
      summary = "Register exhibition interest",
      description =
          "Capture interest for the exhibition (exhibitor or visitor). Public endpoint - no authentication required.")
  public ResponseEntity<ExhibitionInterestResponse> register(
      @Valid @RequestBody ExhibitionInterestRequest request) {
    ExhibitionInterestResponse response = service.register(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
