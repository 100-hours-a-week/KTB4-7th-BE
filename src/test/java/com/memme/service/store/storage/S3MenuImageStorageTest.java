package com.memme.service.store.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

class S3MenuImageStorageTest {

    @Test
    void 이미지를_저장하고_10분짜리_접근_URL을_만든다() throws Exception {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);
        when(presigned.url()).thenReturn(URI.create("https://example.test/menu-image?signature=test").toURL());
        var storage = new S3MenuImageStorage(client, presigner, "private-menu-images");

        String key = storage.store(new MenuImageFile(7, "image/png", new byte[] {1, 2, 3}));
        URI url = storage.temporaryReadUrl(key);
        storage.delete(key);

        ArgumentCaptor<PutObjectRequest> put = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(put.capture(), any(RequestBody.class));
        assertThat(put.getValue().bucket()).isEqualTo("private-menu-images");
        assertThat(put.getValue().key()).isEqualTo(key);
        assertThat(put.getValue().contentType()).isEqualTo("image/png");

        ArgumentCaptor<GetObjectPresignRequest> presign =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(presign.capture());
        assertThat(presign.getValue().signatureDuration()).isEqualTo(Duration.ofMinutes(10));
        assertThat(presign.getValue().getObjectRequest().key()).isEqualTo(key);
        assertThat(url).isEqualTo(URI.create("https://example.test/menu-image?signature=test"));
        verify(client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void S3_저장_실패시_부분_저장된_객체를_정리한다() {
        S3Client client = mock(S3Client.class);
        S3Presigner presigner = mock(S3Presigner.class);
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(S3Exception.builder().message("upload failed").build());
        var storage = new S3MenuImageStorage(client, presigner, "private-menu-images");

        assertThatThrownBy(() -> storage.store(new MenuImageFile(7, "image/png", new byte[] {1})))
                .isInstanceOf(IllegalStateException.class);

        verify(client).deleteObject(any(DeleteObjectRequest.class));
    }
}
