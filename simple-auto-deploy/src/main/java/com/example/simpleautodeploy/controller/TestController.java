package com.example.simpleautodeploy.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author shiweisen
 * @since 2018-11-06
 */
@RestController
public class TestController {

    @RequestMapping(value = "test")
    public String test(){
        return "just test last  999999";
    }


}
