package net.sanaechan.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping(value = "/")
public class Home {
	@RequestMapping(name = "/", method = RequestMethod.GET)
	public String index(Model model) {
		return "home";
	}
}
