package com.pm.billing.grpc;

import billing.BillingRequest;
import billing.BillingResponse;
import billing.BillingServiceGrpc.BillingServiceImplBase;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * gRPC server implementation of the billing service.
 *
 * <p>This service is internal-only: it is reached exclusively by the patient-service over gRPC
 * (port 9005) when a patient is created. It is never exposed through the API gateway.
 */
@GrpcService
public class BillingGrpcService extends BillingServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(BillingGrpcService.class);

    @Override
    public void createBillingAccount(BillingRequest request,
                                     StreamObserver<BillingResponse> responseObserver) {
        log.info("createBillingAccount request received for patientId={}, name={}, email={}",
                request.getPatientId(), request.getName(), request.getEmail());

        // In a real system this would persist a billing account and integrate with a payment
        // provider. Here we simulate account creation and return a generated account id.
        String accountId = UUID.randomUUID().toString();

        BillingResponse response = BillingResponse.newBuilder()
                .setAccountId(accountId)
                .setStatus("ACTIVE")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

        log.info("Billing account created accountId={} status=ACTIVE for patientId={}",
                accountId, request.getPatientId());
    }
}
