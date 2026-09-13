package com.hospital.payroll.aws;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

@ApplicationScoped
public class AwsClients {

    @Produces
    @ApplicationScoped
    DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Produces
    @ApplicationScoped
    S3Client s3Client() {
        return S3Client.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    @Produces
    @ApplicationScoped
    CognitoIdentityProviderClient cognitoClient() {
        return CognitoIdentityProviderClient.builder()
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }
}
