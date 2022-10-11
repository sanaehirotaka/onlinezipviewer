package net.sanaechan.web.controller.api;

import java.nio.file.Path;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.sanaechan.storage.manager.workspace.EncryptedConfig;
import net.sanaechan.storage.manager.workspace.Workspace;
import net.sanaechan.web.result.LogicResult;
import net.sanaechan.web.result.Token;

@RestController
@RequestMapping(value = "/api/login")
public class Login {

    @PostMapping("getToken")
    public LogicResult index(@RequestParam("key") String key) {
        try (EncryptedConfig config = new EncryptedConfig(Path.of("./workspace"), key.toCharArray())) {
            Token result = new Token();
            result.setToken(Workspace.set(config.get(Workspace.class, Workspace::new)));
            return result;
        } catch (Exception e) {
            return LogicResult.fail();
        }
    }
}
