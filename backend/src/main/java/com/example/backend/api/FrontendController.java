package com.example.backend.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Browser entry routes only; API and missing asset paths retain their normal responses. */
@Controller
public class FrontendController {

  @GetMapping({"/workspace", "/workspace/{*path}"})
  public String workspace() {
    return "forward:/index.html";
  }
}
