package com.housingplatform.property.api;

import com.housingplatform.property.dto.PropertyResponse;
import com.housingplatform.property.service.FavoritePropertyService;
import com.housingplatform.shared.security.UserContext;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/favorites")
@AuthPolicyScope(AuthPolicyScope.Policy.AUTHENTICATED)
@Tag(name = "Favorites", description = "Saved property listings for the current user")
@RequiredArgsConstructor
public class FavoritePropertyController {

  private final FavoritePropertyService favoritePropertyService;

  @GetMapping
  @Operation(summary = "List saved properties")
  public ResponseEntity<List<PropertyResponse>> getFavorites() {
    return ResponseEntity.ok(favoritePropertyService.getFavorites(UserContext.getCurrentUserId()));
  }

  @PostMapping("/{propertyId}")
  @Operation(summary = "Save a property")
  public ResponseEntity<Void> addFavorite(@PathVariable UUID propertyId) {
    favoritePropertyService.addFavorite(UserContext.getCurrentUserId(), propertyId);
    return ResponseEntity.status(HttpStatus.CREATED).build();
  }

  @DeleteMapping("/{propertyId}")
  @Operation(summary = "Remove a saved property")
  public ResponseEntity<Void> removeFavorite(@PathVariable UUID propertyId) {
    favoritePropertyService.removeFavorite(UserContext.getCurrentUserId(), propertyId);
    return ResponseEntity.noContent().build();
  }
}
