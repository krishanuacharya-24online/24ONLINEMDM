package com.e24online.mdm.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginPageController {

    @GetMapping({"/", "/login", "/signin"})
    public String loginPage() {
        return "login";
    }

    @GetMapping("/login.html")
    public String loginHtmlRedirect() {
        return "redirect:/login";
    }
}
