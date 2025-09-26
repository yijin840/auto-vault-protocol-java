package org.avpj.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.avpj.web3.TronService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author : yijin
 * @email : yijin840@gmail.com
 * @created : 9/25/25 2:45 PM
 **/
@RestController
@Slf4j
@RequiredArgsConstructor
public class Web3Controller {

    private final TronService tronService;

    @GetMapping("/tron")
    public String tronDevelopContracts() {
        try {
            tronService.processTronContracts();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return "success";
    }
}
