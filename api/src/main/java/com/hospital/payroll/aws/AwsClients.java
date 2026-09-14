package com.hospital.payroll.aws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

@ApplicationScoped
public class AwsClients {

    private Region region() {
        String value = System.getenv("AWS_REGION");
        if (value == null || value.isBlank()) {
            value = System.getenv("AWS_DEFAULT_REGION");
        }
        if (value == null || value.isBlank()) {
            value = "ap-southeast-2";
        }
        return Region.of(value);
    }

    @Produces
    @ApplicationScoped
    DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(region())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Produces
    @ApplicationScoped
    S3Client s3Client() {
        return S3Client.builder()
                .region(region())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Produces
    @ApplicationScoped
    CognitoIdentityProviderClient cognitoClient() {
        return CognitoIdentityProviderClient.builder()
                .region(region())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
