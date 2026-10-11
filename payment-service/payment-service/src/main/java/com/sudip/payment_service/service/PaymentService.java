package com.sudip.payment_service.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.sudip.payment_service.dto.CreatePaymentRequest;
import com.sudip.payment_service.dto.PaymentOrderResponse;
import com.sudip.payment_service.entity.Payment;
import com.sudip.payment_service.entity.PaymentStatus;
import com.sudip.payment_service.repository.PaymentRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {
    @Value("${razorpay.key.id}")
    private String keyId;

    @Value("${razorpay.key.secret}")
    private String keySecret;

    private final PaymentRepository paymentRepository;
    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";
    private final KafkaTemplate<String, Object> kafkaTemplate;


    public PaymentOrderResponse createPaymentOrder(
            @Valid CreatePaymentRequest createPaymentRequest) throws RazorpayException {
        log.info("Creating payment order for account: {} amount: {}",
                createPaymentRequest.getAccountNumber(), createPaymentRequest.getAmount());


        RazorpayClient razorpayClient = new RazorpayClient(keyId, keySecret);

        //converted amount
        int convertedAmount = createPaymentRequest.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .intValue();

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", convertedAmount);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "order_rcpt_" + System.currentTimeMillis() + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 10));
        Order razorpayOrder = razorpayClient.orders.create(orderRequest);
        log.info("Razorpay order created: {}", razorpayOrder.get("id").toString());


        // save payment record in database
        Payment payment = new Payment();
        payment.setAccountNumber(createPaymentRequest.getAccountNumber());
        payment.setAmount(createPaymentRequest.getAmount());
        payment.setRazorpayOrderId(razorpayOrder.get("id").toString());
        payment.setCurrency("INR");
        payment.setPaymentStatus(PaymentStatus.CREATED);
        payment.setDescription(createPaymentRequest.getDescription());
        Payment savedPayment = paymentRepository.save(payment);

        return new PaymentOrderResponse(
                savedPayment.getId(),
                razorpayOrder.get("id").toString(),
                createPaymentRequest.getAmount(),
                "INR",
                "CREATED",
                keyId
        );
    }


    public void handleWebhook(Map<String, Object> payload) {
        log.info("Received Razorpay webhook: {} ", payload.get("event"));
        String event = (String) payload.get("event");
        if ("payment.captured".equals(event)) {
            handlePaymentSuccess(payload);

        } else if ("payment.failed".equals(event)) {
            handlePaymentFailed(payload);
        } else {
            log.warn("Unhandled Razorpay webhook event: {}", event);
        }
    }



    private void handlePaymentFailed(Map<String, Object> payload) {
        try {
            Map<String, Object> paymentData = extractPaymentData(payload);
            String OrderId = (String) paymentData.get("order_id");

            Payment payment = (Payment) paymentRepository.findByRazorpayOrderId(OrderId)
                    .orElseThrow(() -> new RuntimeException("Payment not found for Razorpay order ID: " +OrderId));

            payment.setPaymentStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment failed via Razorpay");
            paymentRepository.save(payment);

            //publish payment completed event
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("reason", "Payment failed via Razorpay");
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());

            kafkaTemplate.send(PAYMENT_FAILED_TOPIC, payment.getId(), event);
            log.warn("Payment failed: {}", payment.getId());

        } catch (Exception e) {
            log.error("Error handling payment faliure: {}", e.getMessage());
        }
    }



    private void handlePaymentSuccess(Map<String, Object> payload) {
        try {
            Map<String, Object> paymentData = extractPaymentData(payload);
            String OrderId = (String) paymentData.get("order_id");
            String PaymentId = (String) paymentData.get("id");

            Payment payment = (Payment) paymentRepository.findByRazorpayOrderId(OrderId)
                    .orElseThrow(() -> new RuntimeException("Payment not found for Razorpay order ID: " + OrderId));

            payment.setRazorpayPaymentId(PaymentId);
            payment.setPaymentStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            //publish payment completed event
            Map<String, Object> event = new HashMap<>();
            event.put("paymentId", payment.getId());
            event.put("razorpayPaymentId", PaymentId);
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());

            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, payment.getId(), event);
            log.info("Payment completed: {}", payment.getId());
        } catch (Exception e) {
            log.error("Error handling payment success: {}", e.getMessage());
        }
    }

    private Map<String, Object> extractPaymentData( Map<String, Object> payload) {
        Map<String, Object> entity =  (Map<String, Object>) payload.get("payload");
        Map<String, Object> paymentWrapper =  (Map<String, Object>) entity.get("payload");
        return (Map<String, Object>) paymentWrapper.get("entity");
    }
}