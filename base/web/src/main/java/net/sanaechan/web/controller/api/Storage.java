package net.sanaechan.web.controller.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import net.sanaechan.storage.manager.crypt.FileNameEncoder;
import net.sanaechan.storage.manager.storage.BlobRef;
import net.sanaechan.storage.manager.storage.BucketConfig;
import net.sanaechan.storage.manager.storage.GStorage;
import net.sanaechan.storage.manager.workspace.Workspace;

@RestController
@RequestMapping(value = "/api/storage")
public class Storage {

    @PostMapping("getBukets")
    public List<String> bukets(@RequestParam("token") String token) {
        Workspace w = Workspace.get(token);

        return w.getBuckets().stream().map(b -> b.getName()).toList();
    }

    @PostMapping("getFiles")
    public List<File> files(@RequestParam("token") String token, @RequestParam("bucket") String bucket)
            throws IOException {
        Workspace w = Workspace.get(token);

        BucketConfig config = w.getBuckets().stream().filter(b -> b.getName().equals(bucket)).findAny().orElse(null);

        return new GStorage(config).list(FileNameEncoder.getInstance(config.getPassword())).stream().map(b -> {
            File f = new File();
            f.setBucket(bucket);
            f.setRealName(b.getRealName());
            f.setDisplayName(b.getDisplayName());
            f.setSize(b.getSize());
            return f;
        }).toList();
    }

    @PostMapping("getFile")
    public ResponseEntity<Resource> file(@RequestParam("token") String token, @RequestParam("bucket") String bucket,
            @RequestParam("file") String file) throws IOException {
        Workspace w = Workspace.get(token);

        BucketConfig config = w.getBuckets().stream().filter(b -> b.getName().equals(bucket)).findAny().orElse(null);

        GStorage storage = new GStorage(config);
        BlobRef blob = storage.list(FileNameEncoder.getInstance(config.getPassword())).stream().filter(b -> b.getRealName().equals(file)).findAny().orElse(null);
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        blob.transferTo(out);
        byte[] content = out.toByteArray();
        return ResponseEntity.ok()
                .contentLength(content.length)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(content));
    }
    
    @Data
    public static class File {
        
        private String bucket;
        
        private String realName;
        
        private String displayName;
        
        private long size;
    }
}
