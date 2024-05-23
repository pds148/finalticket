package com.sparta.finalticket.domain.user.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/v1/admins")
public class AdminController {

  @GetMapping("/admin-page")
  public String adminPage(){
    return "admin-page";
  }

}
