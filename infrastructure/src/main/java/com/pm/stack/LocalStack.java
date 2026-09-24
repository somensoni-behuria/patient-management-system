package com.pm.stack;

import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.BootstraplessSynthesizer;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.CloudMapNamespaceOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;

import java.util.List;
import java.util.Map;

/**
 * AWS CDK stack for the Patient Management System, targeted at LocalStack for local
 * infrastructure-as-code testing (see {@code localstack-deploy.sh}).
 *
 * <p>Provisions:
 * <ul>
 *   <li>a VPC</li>
 *   <li>three PostgreSQL RDS instances (auth / patient / analytics)</li>
 *   <li>an MSK (managed Kafka) cluster</li>
 *   <li>an ECS cluster and a Fargate service per microservice, wired via Cloud Map DNS</li>
 * </ul>
 *
 * <p>Note: MSK and ECS on LocalStack require LocalStack Pro. The stack synthesizes cleanly with
 * community LocalStack; RDS + VPC deploy on community.
 */
public class LocalStack extends Stack {

    private final Vpc vpc;
    private final Cluster ecsCluster;

    public LocalStack(final App scope, final String id, final StackProps props) {
        super(scope, id, props);

        this.vpc = createVpc();

        DatabaseInstance authDb = createDatabase("AuthDb", "auth_db");
        DatabaseInstance patientDb = createDatabase("PatientDb", "patient_db");
        DatabaseInstance analyticsDb = createDatabase("AnalyticsDb", "analytics_db");

        CfnCluster mskCluster = createMskCluster();

        this.ecsCluster = createEcsCluster();

        // Internal gRPC service (no DB).
        FargateService billing = createFargateService(
                "BillingService", "billing-service", List.of(9005, 4001), null, Map.of());

        // Auth service (its own DB).
        createFargateService("AuthService", "auth-service", List.of(4005), authDb,
                Map.of("JWT_SECRET",
                        "ZGV2LW9ubHktc2VjcmV0LXBsZWFzZS1vdmVycmlkZS1pbi1wcm9kLTAxMjM0NTY3ODlBQkNERUY="));

        // Patient service (DB + gRPC to billing + Kafka).
        FargateService patient = createFargateService(
                "PatientService", "patient-service", List.of(4000), patientDb,
                Map.of(
                        "BILLING_SERVICE_HOST", "billing-service.patient-management.local",
                        "BILLING_SERVICE_GRPC_PORT", "9005",
                        "SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4511"));
        patient.getNode().addDependency(billing);
        patient.getNode().addDependency(mskCluster);

        // Kafka consumers.
        createFargateService("NotificationService", "notification-service", List.of(4003), null,
                Map.of("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4511"))
                .getNode().addDependency(mskCluster);

        createFargateService("AnalyticsService", "analytics-service", List.of(4002), analyticsDb,
                Map.of("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4511"))
                .getNode().addDependency(mskCluster);

        // API gateway (public entrypoint).
        createFargateService("ApiGateway", "api-gateway", List.of(4004), null,
                Map.of(
                        "AUTH_SERVICE_URL", "http://auth-service.patient-management.local:4005",
                        "PATIENT_SERVICE_URL", "http://patient-service.patient-management.local:4000",
                        "ANALYTICS_SERVICE_URL", "http://analytics-service.patient-management.local:4002"));

        CfnOutput.Builder.create(this, "VpcId").value(vpc.getVpcId()).build();
    }

    private Vpc createVpc() {
        return Vpc.Builder.create(this, "PatientManagementVpc")
                .vpcName("patient-management-vpc")
                .maxAzs(2)
                .natGateways(0)
                .build();
    }

    private DatabaseInstance createDatabase(String id, String dbName) {
        return DatabaseInstance.Builder.create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_16_4)
                                .build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("postgres"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private CfnCluster createMskCluster() {
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("patient-management-kafka")
                .kafkaVersion("3.9.x")
                .numberOfBrokerNodes(2)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.large")
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .map(s -> s.getSubnetId())
                                .toList())
                        .brokerAzDistribution("DEFAULT")
                        .build())
                .build();
    }

    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local")
                        .build())
                .build();
    }

    private FargateService createFargateService(String id,
                                                String imageName,
                                                List<Integer> ports,
                                                DatabaseInstance db,
                                                Map<String, String> additionalEnvVars) {
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, id + "Task")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions.Builder container =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry(imageName))
                        .portMappings(ports.stream()
                                .map(p -> PortMapping.builder()
                                        .containerPort(p)
                                        .hostPort(p)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix(imageName)
                                .build()));

        var envVars = new java.util.HashMap<String, String>();
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4511");

        if (db != null) {
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s".formatted(
                    db.getDbInstanceEndpointAddress(),
                    db.getDbInstanceEndpointPort(),
                    imageName.replace("-service", "") + "_db"));
            envVars.put("SPRING_DATASOURCE_USERNAME", "postgres");
            envVars.put("SPRING_DATASOURCE_PASSWORD",
                    db.getSecret() != null
                            ? db.getSecret().secretValueFromJson("password").toString()
                            : "password");
        }
        envVars.putAll(additionalEnvVars);
        container.environment(envVars);

        taskDefinition.addContainer(id + "Container", container.build());

        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false)
                .serviceName(imageName)
                .build();
    }

    public static void main(final String[] args) {
        App app = new App(AppProps.builder().outdir("cdk.out").build());

        StackProps props = StackProps.builder()
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        new LocalStack(app, "localstack", props);
        app.synth();
        System.out.println("Synthesizing Patient Management System infrastructure...");
    }
}
