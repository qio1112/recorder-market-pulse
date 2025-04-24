package com.yipeng.recorder.utils;

import java.io.File;

public class RunScriptResult {

    private String output;
    private int exitCode;
    private File tempScriptFile;

    public RunScriptResult() {}

    public String getOutput() {
        return output;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }

    public File getTempScriptFile() {
        return tempScriptFile;
    }

    public void setTempScriptFile(File tempScriptFile) {
        this.tempScriptFile = tempScriptFile;
    }
}
