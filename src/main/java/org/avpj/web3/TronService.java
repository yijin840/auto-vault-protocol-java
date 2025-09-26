package org.avpj.web3;

import com.google.protobuf.ByteString;
import com.google.protobuf.Type;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Hex;
import org.springframework.stereotype.Service;
import org.tron.trident.core.ApiWrapper;
import org.tron.trident.core.utils.ByteArray;
import org.tron.trident.proto.Chain;
import org.tron.trident.proto.Common;
import org.tron.trident.proto.Contract;
import org.tron.trident.proto.Response;
import org.tron.trident.utils.Base58Check;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.ECKeyPair;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;

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

    public void processTronContracts() throws Exception {
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
            log.warn("警告: 账户能量可能不足，建议冻结 200 TRX 获取能量");
//            //质押能量
//            long engine = 500_000_000L;
//            Response.TransactionExtention txe = client.freezeBalanceV2(tronConfig.getAddress(), engine, 1);
//            //签名
//            Chain.Transaction signedTransaction = client.signTransaction(txe);
//            String txId = client.broadcastTransaction(signedTransaction);
//            log.info("冻结交易ID: {}, 预计获得能量约: {} Energy", txId, engine / 10_000);
        }
        //编译合约
        ContractCompiler.CompileResult vaultResult = ContractCompiler.compileHardhat(this.tronConfig.getContractFile(), "Vault.sol", "Vault");
        log.info("vaultResult: {}", vaultResult.getAbi());
        log.info("vaultResult: {}", vaultResult.getBytecode());

        long feeLimit = 1_000_000_000L; // 1000 TRX,最高上限
        long consumeUserResourcePercent = 100L;
        long originEnergyLimit = 10_000_000L;
        long callValue = 0L;
        String tokenId = "";
        long tokenValue = 0L;

        Response.TransactionExtention vault = client.deployContract(
                "Vault",
                vaultResult.getAbi(),
                vaultResult.getBytecode(),
                new ArrayList<>(),
                feeLimit,
                consumeUserResourcePercent,
                originEnergyLimit,
                callValue,
                tokenId,
                tokenValue
        );

        // 直接打印关键信息
        log.info("=== Deploy Contract Result ===");
        log.info("TxID           : {}", ByteArray.toHexString(vault.getTxid().toByteArray()));
        log.info("Result Code    : {}", vault.getResult().getCode());
        log.info("Result Message : {}", vault.getResult().getMessage().toStringUtf8());
        log.info("Energy Used    : {}", vault.getEnergyUsed());

        // 如果需要完整 protobuf 文本
        log.info("Full Response  : {}", Hex.toHexString(vault.toByteArray()));


        // 2. 签名
        Chain.Transaction signed = client.signTransaction(vault.getTransaction());

        // 3. 广播
        String signedRes = client.broadcastTransaction(signed);

        log.info("broadcast result: {}", signedRes);

    }
}