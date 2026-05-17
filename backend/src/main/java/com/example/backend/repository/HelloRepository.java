package com.example.backend.repository;

import org.springframework.stereotype.Repository;

@Repository
public class HelloRepository {

  public String fetchHelloMessage() {
    return "Hello from BACKEND!";
  }

  public String sendHelloRequest(String message) {
    return message;
  }
}
