package org.avpj;

import com.alibaba.fastjson.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.avpj.web3.TronConfig;
import org.avpj.web3.TronService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

import javax.annotation.PostConstruct;

/**
 * @author : yijin
 * @email : yijin840@gmail.com
 * @created : 9/24/25 5:33 PM
 **/
@SpringBootApplication
@RequiredArgsConstructor
@Slf4j
@EnableAutoConfiguration
@ComponentScan(basePackages = {"org.avpj", "org.avpj.web3"})  // 扫描 web3 包
public class AvpjApplication {

    private final TronService tronService;

    private final TronConfig tronConfig;

    public static void main(String[] args) {
        SpringApplication.run(AvpjApplication.class, args);
    }

}
