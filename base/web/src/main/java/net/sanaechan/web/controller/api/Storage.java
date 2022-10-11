package net.sanaechan.web.controller.api;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.sanaechan.storage.manager.crypt.FileNameEncoder;
import net.sanaechan.storage.manager.crypt.OpenSSLLikeCrypt;
import net.sanaechan.storage.manager.storage.BlobRef;
import net.sanaechan.storage.manager.storage.BucketConfig;
import net.sanaechan.storage.manager.storage.GStorage;
import net.sanaechan.storage.manager.workspace.Workspace;
import net.sanaechan.web.result.BlobFiles;
import net.sanaechan.web.result.Bukets;
import net.sanaechan.web.result.LogicResult;

@RestController
@RequestMapping(value = "/api/storage")
public class Storage {

    @PostMapping("getBukets")
    public LogicResult bukets(@RequestParam("token") String token) {
        Workspace w = Workspace.get(token);
        if (w != null) {
            Bukets bukets = new Bukets();
            bukets.setBukets(w.getBuckets().stream().map(BucketConfig::getName).toList());
            return bukets;
        }
        return LogicResult.fail();
    }

    @PostMapping("getFiles")
    public LogicResult files(@RequestParam("token") String token, @RequestParam("bucket") String bucket)
            throws IOException {
        Workspace w = Workspace.get(token);
        if (w != null) {
            BucketConfig config = w.getBuckets().stream()
                    .filter(b -> b.getName().equals(bucket))
                    .findAny()
                    .orElse(null);
            if (config != null) {
                GStorage storage = new GStorage(config);

                BlobFiles files = new BlobFiles();
                files.setFiles(
                        storage.list(FileNameEncoder.getInstance(config.getPassword())).stream().map(b -> {
                            BlobFiles.File f = new BlobFiles.File();
                            f.setBucket(bucket);
                            f.setRealName(b.getRealName());
                            f.setDisplayName(b.getDisplayName());
                            f.setSize(b.getSize());
                            return f;
                        }).toList());
                return files;
            }
        }
        return LogicResult.fail();
    }

    @PostMapping("getFile")
    public ResponseEntity<Resource> file(@RequestParam("token") String token, @RequestParam("bucket") String bucket,
            @RequestParam("file") String file) throws IOException, GeneralSecurityException {
        Workspace w = Workspace.get(token);

        BucketConfig config = w.getBuckets().stream().filter(b -> b.getName().equals(bucket)).findAny().orElse(null);

        GStorage storage = new GStorage(config);
        BlobRef blob = storage.list(FileNameEncoder.getInstance(config.getPassword())).stream()
                .filter(b -> b.getRealName().equals(file)).findAny().orElse(null);

        Path tempFrom = Workspace.tempFileEncrypted(w);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFrom))) {
            blob.transferTo(out);
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = new BufferedInputStream(Files.newInputStream(tempFrom))) {
            try (InputStream decrypt = OpenSSLLikeCrypt.decrypt(in, blob.getFileKey().toCharArray())) {
                decrypt.transferTo(out);
            }
        } finally {
            Files.deleteIfExists(tempFrom);
        }

        byte[] content = out.toByteArray();

        return ResponseEntity.ok()
                .contentLength(content.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(content));
    }
}
