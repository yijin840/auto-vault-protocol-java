package org.avpj.web3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tron.trident.abi.FunctionEncoder;
import org.tron.trident.abi.datatypes.Address;
import org.tron.trident.abi.datatypes.Function;
import org.tron.trident.abi.datatypes.Type;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.utils.ByteArray;
import org.tron.trident.proto.Chain;
import org.tron.trident.proto.Response;
import org.tron.trident.utils.Base58Check;
import org.tron.trident.utils.Numeric;
import org.web3j.crypto.ContractUtils;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

@Slf4j
@Service
@RequiredArgsConstructor
public class TronService {

    private final TronConfig tronConfig;

    public void processTronContracts() throws Exception {
        // 初始化 Trident 客户端
        ApiWrapper client = new ApiWrapper(tronConfig.getFullHost(), tronConfig.getSolidityNode(), tronConfig.getPrivateKey());

        // Credentials 对象包含私钥、公钥和地址
        Credentials credentials = Credentials.create(tronConfig.getPrivateKey());
        ECKeyPair keyPair = credentials.getEcKeyPair();

        // 1. 获取私钥生成的十六进制地址
        String derivedHexAddress = credentials.getAddress();
        String hexWithoutPrefix = derivedHexAddress.startsWith("0x") ? derivedHexAddress.substring(2) : derivedHexAddress;

        // 2. 转换为 Base58 地址
        String hexWithPrefix41 = "41" + hexWithoutPrefix;
        byte[] fromHexStringHexBytes = ByteArray.fromHexString(hexWithPrefix41);
        String derivedBase58Address = Base58Check.bytesToBase58(fromHexStringHexBytes);

        log.info("Derived Address (Base58): {}", derivedBase58Address);
        log.info("Configured Address: {}", tronConfig.getAddress());

        if (!derivedBase58Address.equals(tronConfig.getAddress())) {
            throw new Exception("私钥生成的地址与TRON_ADDRESS不匹配");
        }

        // 检查账户余额和能量
        long balance = client.getAccountBalance(derivedBase58Address);
        log.info("TRX 余额: {} TRX", balance / 1_000_000.0);
        if (balance < 1_000_000) throw new Exception("账户余额不足，无法部署合约");

        Response.AccountResourceMessage resources = client.getAccountResource(derivedBase58Address);
        long energyLimit = resources.getEnergyLimit();
        log.info("账户能量: {}", energyLimit);
        if (energyLimit < 200_000) {
            log.warn("警告: 账户能量可能不足，建议冻结 200 TRX 获取能量");
        }

        // 1. 编译 Proxy.sol
        ContractCompiler.CompileResult proxyResult = ContractCompiler.compileHardhat(
                tronConfig.getContractFile(), "Proxy.sol", "Proxy"
        );
        log.info("Proxy ABI: {}", proxyResult.getAbi());
        log.info("Proxy Bytecode length: {}", proxyResult.getBytecode().length());

        // 2. 部署 Proxy 合约
        ArrayList<Type<?>> constructorParams = new ArrayList<>();
        constructorParams.add(new Address(tronConfig.getVaultAddress()));
        long feeLimit = 1_000_000_000L;
        long consumeUserResourcePercent = 100L;
        long originEnergyLimit = 10_000_000L;
        long callValue = 0L;
        String tokenId = "";
        long tokenValue = 0L;

        Response.TransactionExtention proxyTx = client.deployContract(
                "Proxy",
                proxyResult.getAbi(),
                proxyResult.getBytecode(),
                constructorParams,
                feeLimit,
                consumeUserResourcePercent,
                originEnergyLimit,
                callValue,
                tokenId,
                tokenValue
        );

        log.info("=== Deploy Proxy Result ===");
        log.info("TxID           : {}", ByteArray.toHexString(proxyTx.getTxid().toByteArray()));
        log.info("Result Code    : {}", proxyTx.getResult().getCode());
        log.info("Result Message : {}", proxyTx.getResult().getMessage().toStringUtf8());
        log.info("Energy Used    : {}", proxyTx.getEnergyUsed());

        Chain.Transaction signedProxyTx = client.signTransaction(proxyTx.getTransaction());
        String broadcastRes = client.broadcastTransaction(signedProxyTx);
        log.info("Proxy broadcast result: {}", broadcastRes);

        // ============== 3. 预测 Proxy 地址 ===============
        // 商户地址 Base58
        String merchantAddress = "TJSiouBApng8Efzmr2etM6xxuDAigr91GQ";

        // 预测 Proxy 部署地址（使用 CREATE2 计算）
        String predictionAddress = getPredictionAddress(
                hexWithPrefix41,
                ByteArray.toHexString(Base58Check.base58ToBytes(merchantAddress)),
                proxyResult.getBytecode().getBytes()
        );

        // ============== 4. 编码 initialize(address) 方法的 callData ==============
        // 构造 Trident Address 对象（直接用字节数组，不要用 Arrays.toString）
        Address merchantParam = new Address(merchantAddress);

        // 构造 ABI 方法调用对象
        Function function = new Function(
                "initialize",
                Collections.singletonList(merchantParam),
                new ArrayList<>()
        );

        // 编码成 callData
        String callData = FunctionEncoder.encode(function);

        // ============== 5. 调用 triggerContract 初始化 Proxy ==============
        Response.TransactionExtention initTx = client.triggerContract(
                derivedBase58Address, // 调用者地址（你的部署者 Base58 地址）
                predictionAddress,    // 预测的 Proxy 地址 Hex41
                callData,             // ABI 编码好的 callData
                0L,                   // callValue
                0L,                   // tokenValue
                "",                   // tokenId
                100_000_000L          // feeLimit
        );

        // 签名并广播交易
        Chain.Transaction signedInitTx = client.signTransaction(initTx.getTransaction());
        String broadcastInitRes = client.broadcastTransaction(signedInitTx);
        log.info("初始化商户地址结果: {}", broadcastInitRes);

    }

    public static String getPredictionAddress(String deployerHex41, String merchantHex41, byte[] proxyBytecode) {
        String deployerHex20 = deployerHex41.startsWith("41") ? deployerHex41.substring(2) : deployerHex41;
        byte[] deployerBytes = Numeric.hexStringToByteArray(deployerHex20);
        if (deployerBytes.length != 20) throw new RuntimeException("Deployer address must be 20 bytes");

        byte[] merchantBytes = Numeric.hexStringToByteArray(merchantHex41.startsWith("41") ?
                merchantHex41.substring(2) : merchantHex41);
        byte[] salt = new byte[32];
        Arrays.fill(salt, (byte) 0);
        System.arraycopy(merchantBytes, 0, salt, 0, Math.min(merchantBytes.length, 32));

        byte[] result = ContractUtils.generateCreate2ContractAddress(deployerBytes, salt, proxyBytecode);
        return "41" + Numeric.toHexStringNoPrefix(result);
    }
}
