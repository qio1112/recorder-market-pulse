package com.yipeng.recorder.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class FrontendRouteController {

    @GetMapping("/reset-password")
    public String resetPasswordRoute(@RequestParam("token") String token) {
        return "redirect:/#/reset-password?token=" + token;
    }
}
