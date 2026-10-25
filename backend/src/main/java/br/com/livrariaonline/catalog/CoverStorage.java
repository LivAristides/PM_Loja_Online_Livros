package br.com.livrariaonline.catalog;

import java.io.IOException;

interface CoverStorage {
    StoredCover store(byte[] content) throws IOException;
    void delete(String storageKey) throws IOException;
}

record StoredCover(String storageKey, String contentType, int byteSize, int width, int height) {
}
