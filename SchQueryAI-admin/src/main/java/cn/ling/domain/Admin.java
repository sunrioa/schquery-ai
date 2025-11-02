package cn.ling.domain;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class Admin {

    @PostConstruct
    public void init() {
        System.out.println("Admin init");
    }

}
