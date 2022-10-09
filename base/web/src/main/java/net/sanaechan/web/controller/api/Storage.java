package net.sanaechan.web.controller.api;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.sanaechan.storage.manager.workspace.Workspace;

@RestController
@RequestMapping(value = "/api/storage")
public class Storage {

	@PostMapping("getBukets")
	public List<String> bukets(@RequestParam("token") String token) {
		Workspace w = Workspace.get(token);
		return w.getBuckets().stream().map(b -> b.getName()).toList();
	}
}
