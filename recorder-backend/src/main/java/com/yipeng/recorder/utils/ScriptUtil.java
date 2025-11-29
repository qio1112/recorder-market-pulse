package com.yipeng.recorder.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScriptUtil {

    private static final Logger logger = LoggerFactory.getLogger(ScriptUtil.class);

    public static File extractScript(String resourcePath) throws IOException {

        try (InputStream in = ScriptUtil.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            // Create a temporary file
            File tempScript = File.createTempFile("run" + System.currentTimeMillis(), ".sh");
            tempScript.deleteOnExit(); // Ensure cleanup on JVM exit

            // Copy the contents of the resource to the temporary file
            Files.copy(in, tempScript.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // Make the file executable
            tempScript.setExecutable(true);
            return tempScript;
        }
    }

    public static RunScriptResult runScript(String scriptName, Map<String, String> arguments) {
        StringBuilder output = new StringBuilder();
        File scriptFile = null;
        int exitCode;
        RunScriptResult result = new RunScriptResult();

        try {
            // Extract the script from resources (e.g., /scripts/run.sh)
            scriptFile = extractScript("/scripts/" + scriptName + ".sh");
            result.setTempScriptFile(scriptFile);

            // Prepare the command with arguments
            List<String> command = new ArrayList<>();
            command.add("bash");
            command.add(scriptFile.getAbsolutePath());
            if (arguments != null) {
                for (Map.Entry<String, String> e : arguments.entrySet()) {
                    command.add("--" + e.getKey());
                    command.add(e.getValue());
                }
            }

            // Build and start the process to execute the script
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Read the script's output
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            exitCode = process.waitFor();
            result.setExitCode(exitCode);
            result.setOutput(output.toString());
            logger.info("Ran script: {} with exit code: {}, output: \n{}", scriptName, exitCode, output);
        } catch (IOException | InterruptedException e) {
            result.setExitCode(1);
            result.setOutput(e.getMessage());
        } finally {
            // Delete the temporary script file immediately after execution
            if (scriptFile != null && scriptFile.exists()) {
                if (!scriptFile.delete()) {
                    logger.warn("Warning: Failed to delete temporary script file: {}", scriptFile.getAbsolutePath());
                }
            }
        }
        return result;
    }
}

