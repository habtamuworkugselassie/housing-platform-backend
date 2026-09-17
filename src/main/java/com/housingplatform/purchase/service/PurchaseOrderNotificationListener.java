package com.housingplatform.purchase.service;

import com.housingplatform.identity.domain.User;
import com.housingplatform.identity.repository.RealEstateAgentRepository;
import com.housingplatform.identity.repository.UserRepository;
import com.housingplatform.notification.domain.Notification;
import com.housingplatform.notification.repository.NotificationRepository;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder;
import com.housingplatform.purchase.domain.PropertyPurchaseOrder.PurchaseOrderStatus;
import com.housingplatform.purchase.repository.PropertyPurchaseOrderRepository;
import com.housingplatform.purchase.service.PurchaseOrderEvents.PurchaseOrderCreatedEvent;
import com.housingplatform.purchase.service.PurchaseOrderEvents.PurchaseOrderStatusChangedEvent;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Fans purchase-order events out to the people involved. Runs after commit and asynchronously, so a
 * notification failure can never roll an order back.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderNotificationListener {

  private final PropertyPurchaseOrderRepository orderRepository;
  private final NotificationRepository notificationRepository;
  private final UserRepository userRepository;
  private final RealEstateAgentRepository agentRepository;
  private final PurchaseOrderContactNotifier contactNotifier;

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onCreated(PurchaseOrderCreatedEvent event) {
    orderRepository
        .findById(event.purchaseOrderId())
        .ifPresent(
            order -> {
              String title = "New purchase order " + order.getOrderNumber();
              String body =
                  order.isFinanced()
                      ? "A buyer placed a bank-financed purchase order on your listing."
                      : "A buyer placed a cash purchase order on your listing.";
              notify(sellerUsers(order), title, body, order);
              if (order.isFinanced()) {
                notify(
                    bankUsers(order),
                    "Loan application via purchase order " + order.getOrderNumber(),
                    "A buyer applied for financing through a property purchase order.",
                    order);
              }
              contactNotifier.notifyBuyer(
                  order,
                  "Purchase order " + order.getOrderNumber() + " received",
                  "We have sent your order to the seller. You will be notified when they respond.");
            });
  }

  @Async
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void onStatusChanged(PurchaseOrderStatusChangedEvent event) {
    orderRepository
        .findById(event.purchaseOrderId())
        .ifPresent(
            order -> {
              String title =
                  "Purchase order "
                      + order.getOrderNumber()
                      + " is now "
                      + describe(event.toStatus());
              String body =
                  "Status changed from " + event.fromStatus() + " to " + event.toStatus() + ".";
              notify(Set.of(order.getBuyerId()), title, body, order);
              contactNotifier.notifyBuyer(order, title, body);
              if (event.toStatus() == PurchaseOrderStatus.CANCELLED
                  || event.toStatus() == PurchaseOrderStatus.AWAITING_PAYMENT
                  || event.toStatus() == PurchaseOrderStatus.AWAITING_FINANCING) {
                notify(sellerUsers(order), title, body, order);
              }
              if (order.getFinancing() != null
                  && (event.toStatus() == PurchaseOrderStatus.CANCELLED
                      || event.toStatus() == PurchaseOrderStatus.REJECTED
                      || event.toStatus() == PurchaseOrderStatus.EXPIRED)) {
                notify(bankUsers(order), title, body, order);
              }
            });
  }

  private Set<UUID> sellerUsers(PropertyPurchaseOrder order) {
    Set<UUID> users = new LinkedHashSet<>();
    if (order.getAgentId() != null) {
      agentRepository
          .findById(order.getAgentId())
          .map(a -> a.getUser())
          .map(User::getId)
          .ifPresent(users::add);
    }
    if (order.getRealEstateCompanyId() != null) {
      userRepository.findByOrganizationId(order.getRealEstateCompanyId()).stream()
          .map(User::getId)
          .forEach(users::add);
    }
    return users;
  }

  private Set<UUID> bankUsers(PropertyPurchaseOrder order) {
    Set<UUID> users = new LinkedHashSet<>();
    if (order.getFinancing() != null) {
      userRepository.findByOrganizationId(order.getFinancing().getBankId()).stream()
          .map(User::getId)
          .forEach(users::add);
    }
    return users;
  }

  private void notify(
      Set<UUID> userIds, String title, String message, PropertyPurchaseOrder order) {
    for (UUID userId : userIds) {
      try {
        notificationRepository.save(
            Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(Notification.NotificationType.PURCHASE_ORDER_UPDATE)
                .status(Notification.NotificationStatus.SENT)
                .actionUrl("/purchase-orders/" + order.getId())
                .build());
      } catch (RuntimeException e) {
        log.warn(
            "Could not notify user {} about purchase order {}", userId, order.getOrderNumber(), e);
      }
    }
  }

  private static String describe(PurchaseOrderStatus status) {
    return status.name().toLowerCase().replace('_', ' ');
  }
}
