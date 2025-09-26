package org.avpj.web3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ContractCompiler {

    /**
     * 编译 Hardhat 并读取 ABI/Bytecode
     */
    public static CompileResult compileHardhat(File projectDir,
                                               String contractPath,
                                               String contractName)
            throws IOException, InterruptedException {

        log.info("开始 Hardhat 编译: {}", projectDir.getAbsolutePath());

        // 使用我们新创建的脚本来编译
        String compileScriptPath = projectDir.toPath().resolve("compile_with_nvm.sh").toString();

        // 执行脚本
        ProcessBuilder pb = new ProcessBuilder("bash", compileScriptPath);
        pb.directory(projectDir);
        pb.inheritIO();
        Process p = pb.start();
        int exitCode = p.waitFor();

        if (exitCode != 0) {
            throw new IOException("Hardhat 编译失败，exitCode=" + exitCode);
        }

        // 读取 artifact
        Path artifact = projectDir.toPath()
                .resolve("artifacts/contracts")
                .resolve(contractPath)
                .resolve(contractName + ".json");

        log.info("读取 artifact: {}", artifact);
        String json = new String(Files.readAllBytes(artifact), StandardCharsets.UTF_8);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(json);
        String abi = root.get("abi").toString();
        String bytecode = root.get("bytecode").asText();

        log.info("合约 {} 编译完成", contractName);
        return new CompileResult(abi, bytecode);
    }

    /**
     * 批量编译
     */
    public static Map<String, CompileResult> compileMultiple(File projectDir,
                                                             Map<String, List<String>> contractInfos)
            throws IOException, InterruptedException {
        Map<String, CompileResult> results = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : contractInfos.entrySet()) {
            String path = entry.getKey();
            List<String> names = entry.getValue();
            for (String name : names) {
                CompileResult res = compileHardhat(projectDir, path, name);
                results.put(name, res);
            }
        }
        return results;
    }

    public static class CompileResult {
        private final String abi;
        private final String bytecode;

        public CompileResult(String abi, String bytecode) {
            this.abi = abi;
            this.bytecode = bytecode;
        }

        public String getAbi() {
            return abi;
        }

        public String getBytecode() {
            return bytecode;
        }
    }

}