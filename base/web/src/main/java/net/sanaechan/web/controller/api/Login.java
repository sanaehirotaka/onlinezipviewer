package net.sanaechan.web.controller.api;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.Data;
import net.sanaechan.storage.manager.workspace.EncryptedConfig;
import net.sanaechan.storage.manager.workspace.Workspace;

@RestController
@RequestMapping(value = "/api/login")
public class Login {

    @PostMapping("getToken")
    public Token index(@RequestParam("key") String key) throws IOException {
        try (EncryptedConfig config = new EncryptedConfig(Path.of("./workspace"), key.toCharArray())) {
            return new Token(Workspace.set(config.get(Workspace.class, Workspace::new)));
        }
    }

    @Data
    @AllArgsConstructor
    public static class Token {
        private String token;
    }
}
