package com.pm.patient.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * gRPC client that calls the internal billing-service when a patient is created.
 *
 * <p>Wrapped in Resilience4j retry + circuit-breaker so a transient billing outage or a rolling
 * restart of a billing task does not fail patient creation catastrophically, and tail latency
 * stays bounded (a call that keeps timing out trips the breaker instead of piling up).
 */
@Service
public class BillingServiceGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceGrpcClient.class);

    @GrpcClient("billing-service")
    private BillingServiceGrpc.BillingServiceBlockingStub blockingStub;

    @CircuitBreaker(name = "billingService", fallbackMethod = "createBillingAccountFallback")
    @Retry(name = "billingService")
    public BillingResponse createBillingAccount(String patientId, String name, String email) {
        BillingRequest request = BillingRequest.newBuilder()
                .setPatientId(patientId)
                .setName(name)
                .setEmail(email)
                .build();

        BillingResponse response = blockingStub.createBillingAccount(request);
        log.info("Received billing response accountId={} status={} for patientId={}",
                response.getAccountId(), response.getStatus(), patientId);
        return response;
    }

    /**
     * Fallback used when billing is unavailable or the circuit is open. Patient creation still
     * succeeds; the billing account is expected to be reconciled asynchronously.
     */
    @SuppressWarnings("unused")
    private BillingResponse createBillingAccountFallback(String patientId, String name,
                                                         String email, Throwable t) {
        log.error("Billing call failed for patientId={} - degrading gracefully. Cause: {}",
                patientId, t.toString());
        return BillingResponse.newBuilder()
                .setAccountId("PENDING")
                .setStatus("PENDING_BILLING")
                .build();
    }
}
