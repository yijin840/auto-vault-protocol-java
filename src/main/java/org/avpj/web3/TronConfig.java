package org.avpj.web3;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Files;

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
    private String proxyFactoryAddress;
    private String vaultAddress;

    public File getContractFile() {
        File file = new File(this.contractPath);
        if (file.exists()) {
            return file;
        } else {
            throw new RuntimeException("File not exist");
        }
    }
}
