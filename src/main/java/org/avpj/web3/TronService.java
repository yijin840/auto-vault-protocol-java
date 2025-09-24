package org.avpj.web3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.utils.ByteArray;
import org.tron.trident.proto.Response;
import org.tron.trident.utils.Base58Check;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author : yijin
 * @email : yijin840@gmail.com
 * @created : 9/24/25 5:14 PM
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class TronService {

    private final TronConfig tronConfig;

    public void developContract() throws Exception {
        // 初始化 Trident 客户端
        ApiWrapper client = new ApiWrapper(tronConfig.getFullHost(), tronConfig.getSolidityNode(), tronConfig.getPrivateKey());

        // Credentials 对象包含私钥、公钥和地址
        Credentials credentials = Credentials.create(tronConfig.getPrivateKey());

        // 获取 ECKeyPair 对象
        ECKeyPair keyPair = credentials.getEcKeyPair();

        // 1. 获取私钥生成的十六进制地址，并移除 "0x" 前缀（如果有的话）
        String derivedHexAddress = credentials.getAddress();
        String hexWithoutPrefix = derivedHexAddress.startsWith("0x") ? derivedHexAddress.substring(2) : derivedHexAddress;

        // 2. 将十六进制地址正确转换为 Base58 格式
        // 核心步骤：手动添加 TRON 地址的十六进制前缀 "41"，然后进行转换
        String hexWithPrefix41 = "41" + hexWithoutPrefix;
        byte[] fromHexStringHexBytes = ByteArray.fromHexString(hexWithPrefix41);
        String derivedBase58Address = Base58Check.bytesToBase58(fromHexStringHexBytes);

        // 打印地址信息，帮助你确认格式
//        log.info("Private Key: {}", keyPair.getPrivateKey());
//        log.info("Public Key:  {}", keyPair.getPublicKey());
        log.info("Derived Address (Hex):    {}", hexWithoutPrefix);
        log.info("Derived Address (Hex41):    {}", hexWithPrefix41);
        log.info("Derived Address (Base58): {}", derivedBase58Address);
        log.info("Configured Address: {}", tronConfig.getAddress());

        // 确保从私钥生成的地址与配置文件中设置的地址一致
        if (!derivedBase58Address.equals(tronConfig.getAddress())) {
            throw new Exception("私钥生成的地址与TRON_ADDRESS不匹配");
        }

        // 接下来，所有API调用都使用格式统一的Base58地址
        long balance = client.getAccountBalance(derivedBase58Address);
        log.info("TRX 余额: {} TRX", balance / 1_000_000.0);
        if (balance < 1_000_000) {
            throw new Exception("账户余额不足，无法部署合约");
        }
        Response.AccountResourceMessage resources = client.getAccountResource(derivedBase58Address);
        long energyLimit = resources.getEnergyLimit();
        log.info("账户能量: {}", energyLimit);
        if (energyLimit < 200_000) {
            log.warn("警告: 账户能量可能不足，建议冻结 20-50 TRX 获取能量");
        }
        compileContract();
    }

    private void compileContract() throws IOException, InterruptedException {
        //编译和部署合约
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:contracts/Vault.sol");

        String contractSourceCode;
        try (InputStream is = resource.getInputStream();
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            contractSourceCode = new String(os.toByteArray(), StandardCharsets.UTF_8);
        }
        // 2. 编译合约
        ContractCompiler contractCompiler = new ContractCompiler();
        String[] compile = contractCompiler.compile(contractSourceCode);
        log.info("合约编译成功，ABI为: {}", compile);
    }
}