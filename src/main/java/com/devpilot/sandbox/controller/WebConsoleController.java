package com.devpilot.sandbox.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebConsoleController {

    @GetMapping("/")
    public String root() { return "redirect:/ui/index.html"; }

    @GetMapping("/ui")
    public String uiRedirect() { return "redirect:/ui/index.html"; }
}