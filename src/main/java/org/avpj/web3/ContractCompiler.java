package org.avpj.web3;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ContractCompiler {

    /**
     * 编译 Solidity 合约文件并返回字节码和 ABI。
     *
     * @param contractName 合约名称，例如 "MyContract"
     * @return 包含编译结果的字符串数组，[0] 为字节码，[1] 为 ABI
     * @throws IOException 如果文件读取或进程执行失败
     */
    public String[] compile(String contractName) throws IOException, InterruptedException {
        // 获取合约文件
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:contracts/" + contractName + ".sol");
        if (!resource.exists()) {
            throw new IOException("无法找到合约文件: " + contractName + ".sol");
        }

        // 将资源文件内容复制到临时文件，以便 solc 访问 (兼容Java 1.8)
        File contractFile = File.createTempFile(contractName, ".sol");
        try (InputStream is = resource.getInputStream();
             OutputStream os = new FileOutputStream(contractFile)) {
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        }

        // 1. 确定 solc 可执行文件路径
        // 在 macOS 上，通常是 /usr/local/bin/solc
        String solcExecutablePath = "/usr/local/bin/solc";

        // 2. 构建编译命令
        List<String> command = Arrays.asList(
                solcExecutablePath,
                "--bin",
                "--abi",
                "--optimize",
                contractFile.getAbsolutePath()
        );

        log.info("执行编译命令: {}", String.join(" ", command));

        // 3. 使用 ProcessBuilder 执行命令
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = processBuilder.start();

        // 4. 读取编译器的输出 (兼容Java 1.8)
        String output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));

        String errorOutput = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));

        // 等待进程结束
        int exitCode = process.waitFor();

        // 5. 检查退出码并处理结果
        if (exitCode != 0) {
            log.error("合约编译失败，退出码: {}", exitCode);
            log.error("错误信息: {}", errorOutput);
            throw new IOException("合约编译失败: " + errorOutput);
        }

        // 6. 解析输出，提取字节码和 ABI
        String bytecode = extractValue(output, "Binary:");
        String abi = extractValue(output, "Contract JSON ABI");

        // 清理临时文件
        contractFile.delete();

        return new String[]{bytecode, abi};
    }

    private String extractValue(String output, String key) {
        String[] lines = output.split("\n");
        boolean foundKey = false;
        StringBuilder value = new StringBuilder();
        for (String line : lines) {
            if (line.contains(key)) {
                foundKey = true;
                continue;
            }
            if (foundKey && line.trim().isEmpty()) {
                break;
            }
            if (foundKey) {
                value.append(line.trim());
            }
        }
        return value.toString();
    }
}