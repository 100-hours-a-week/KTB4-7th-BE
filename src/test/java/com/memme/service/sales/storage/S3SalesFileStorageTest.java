package com.memme.service.sales.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3SalesFileStorageTest {

    @Test
    void storesDuplicateFilesWithDifferentKeysAndLoadsContent() throws IOException {
        S3Client s3Client = mock(S3Client.class);
        byte[] content = "sales".getBytes(StandardCharsets.UTF_8);
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(
                        GetObjectResponse.builder().build(),
                        content
                ));
        S3SalesFileStorage storage = new S3SalesFileStorage(s3Client, "private-sales");
        SalesFile file = new SalesFile(
                31L,
                "sales.xlsx",
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                content
        );

        StoredSalesFile first = storage.store(file);
        StoredSalesFile second = storage.store(file);
        byte[] loaded = storage.load(first.storageKey());

        assertThat(first.storageKey()).isNotEqualTo(second.storageKey());
        assertThat(first.storageKey()).startsWith("31/ab/");
        assertThat(loaded).isEqualTo(content);

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodyCaptor = ArgumentCaptor.forClass(RequestBody.class);
        verify(s3Client, times(2)).putObject(requestCaptor.capture(), bodyCaptor.capture());
        assertThat(requestCaptor.getAllValues())
                .allSatisfy(request -> assertThat(request.bucket()).isEqualTo("private-sales"));
        assertThat(requestCaptor.getAllValues())
                .extracting(PutObjectRequest::key)
                .containsExactly(first.storageKey(), second.storageKey());
        assertThat(bodyCaptor.getAllValues().getFirst().contentStreamProvider().newStream().readAllBytes())
                .isEqualTo(content);

        ArgumentCaptor<GetObjectRequest> getCaptor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(getCaptor.capture());
        assertThat(getCaptor.getValue().bucket()).isEqualTo("private-sales");
        assertThat(getCaptor.getValue().key()).isEqualTo(first.storageKey());
    }
}
