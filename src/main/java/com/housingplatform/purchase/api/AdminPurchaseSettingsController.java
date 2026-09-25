package com.housingplatform.purchase.api;

import com.housingplatform.purchase.service.DepositPolicy;
import com.housingplatform.shared.security.annotation.AuthPolicyScope;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Purchase settings admins change without a redeploy. */
@RestController
@RequestMapping("/api/v1/admin/purchase-settings")
@Tag(name = "Admin - Purchase Orders")
@AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
@RequiredArgsConstructor
public class AdminPurchaseSettingsController {

  private final DepositPolicy policy;
  private final com.housingplatform.purchase.service.PurchaseFeeSettings feeSettings;
  private final com.housingplatform.purchase.service.ProviderBankAccountSettings bankAccount;

  /** Birr per USD for deposits paid in USD by international card; null turns USD off. */
  public record UsdRate(
      @DecimalMin(value = "1", message = "The rate must be at least 1 birr per USD")
          @DecimalMax(value = "100000", message = "The rate looks too high")
          BigDecimal etbPerUsd) {}

  @GetMapping("/usd-rate")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Current ETB per USD rate for USD deposits (null = USD off)")
  public ResponseEntity<UsdRate> getUsdRate() {
    return ResponseEntity.ok(new UsdRate(policy.etbPerUsd()));
  }

  @PutMapping("/usd-rate")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Transactional
  @Operation(summary = "Set the ETB per USD rate for USD deposits; null clears it")
  public ResponseEntity<UsdRate> setUsdRate(@Valid @RequestBody UsdRate request) {
    policy.setAdminEtbPerUsd(request.etbPerUsd());
    return ResponseEntity.ok(new UsdRate(policy.etbPerUsd()));
  }

  @GetMapping("/bank-account")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "The provider's bank account for balance transfers (null until set)")
  public ResponseEntity<com.housingplatform.purchase.dto.PurchaseBalanceResponse.BankAccount>
      getBankAccount() {
    return ResponseEntity.ok(bankAccount.get());
  }

  @PutMapping("/bank-account")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Set the bank account buyers transfer the balance to; blank fields clear it")
  public ResponseEntity<com.housingplatform.purchase.dto.PurchaseBalanceResponse.BankAccount>
      setBankAccount(
          @RequestBody
              com.housingplatform.purchase.dto.PurchaseBalanceResponse.BankAccount request) {
    return ResponseEntity.ok(bankAccount.set(request));
  }

  @GetMapping("/fees")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(summary = "Markup % and VAT rate charged to buyers on top of the price")
  public ResponseEntity<com.housingplatform.purchase.service.PurchaseFeeSettings.Rates> getFees() {
    return ResponseEntity.ok(feeSettings.rates());
  }

  @PutMapping("/fees")
  @AuthPolicyScope(AuthPolicyScope.Policy.ADMIN_SECURED)
  @Operation(
      summary = "Set the markup % and VAT rate",
      description = "Applies to orders placed from now on; placed orders keep their amounts.")
  public ResponseEntity<com.housingplatform.purchase.service.PurchaseFeeSettings.Rates> setFees(
      @RequestBody com.housingplatform.purchase.service.PurchaseFeeSettings.Rates request) {
    return ResponseEntity.ok(feeSettings.set(request.markupPercent(), request.vatRate()));
  }
}
