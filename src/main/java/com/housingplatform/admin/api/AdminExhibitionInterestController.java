package com.housingplatform.admin.api;

import com.housingplatform.exhibition.dto.AdminExhibitionInterestResponse;
import com.housingplatform.exhibition.service.AdminExhibitionInterestService;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/exhibition-interests")
@Tag(name = "Admin — Exhibition", description = "Admin listing of exhibition interest registrations")
@RequiredArgsConstructor
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
public class AdminExhibitionInterestController {

  private final AdminExhibitionInterestService adminExhibitionInterestService;

  @GetMapping
  @Operation(
      summary = "List exhibition interest registrations",
      description =
          "Paginated list of exhibition leads (exhibitor/visitor, optional package, linked organization). Admin only.")
  public ResponseEntity<Page<AdminExhibitionInterestResponse>> list(
      @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
          Pageable pageable) {
    return ResponseEntity.ok(adminExhibitionInterestService.list(pageable));
  }
}
