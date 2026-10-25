package br.com.livrariaonline.catalog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class LocalCoverStorage implements CoverStorage {
    private final Path root;

    LocalCoverStorage(@Value("${app.storage.covers-path}") String coversPath) {
        this.root = Path.of(coversPath).toAbsolutePath().normalize();
    }

    @Override
    public StoredCover store(byte[] content) throws IOException {
        CoverFileInfo info = CoverFileInfo.validate(content);
        Files.createDirectories(root);
        String storageKey = UUID.randomUUID() + info.extension();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) throw new IOException("Caminho de armazenamento inválido.");
        Files.write(target, content);
        return new StoredCover(storageKey, info.contentType(), content.length, info.width(), info.height());
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) throw new IOException("Caminho de armazenamento inválido.");
        Files.deleteIfExists(target);
    }
}
