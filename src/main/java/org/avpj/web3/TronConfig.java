package org.avpj.web3;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author : yijin
 * @email : yijin840@gmail.com
 * @created : 9/24/25 5:41 PM
 **/
@Configuration
@ConfigurationProperties(value = "tron")
@Data
public class TronConfig {

    private String privateKey;
    private String address;
    private String fullHost;
    private String solidityNode;
    private String contractPath;
}
