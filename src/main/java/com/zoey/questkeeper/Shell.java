package com.zoey.questkeeper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

final class Shell {
    private Shell() {}

    static Result run(String command, long timeoutMs) {
        Process process = null;
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        try {
            process = new ProcessBuilder("sh", "-c", command).redirectErrorStream(false).start();
            boolean done = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                process.destroyForcibly();
                return new Result(false, -1, out.toString(), "Timed out: " + command);
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) out.append(line).append('\n');
            }
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) err.append(line).append('\n');
            }
            int code = process.exitValue();
            return new Result(code == 0, code, out.toString().trim(), err.toString().trim());
        } catch (Exception e) {
            return new Result(false, -2, out.toString(), e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            if (process != null) process.destroy();
        }
    }

    static final class Result {
        final boolean ok;
        final int exitCode;
        final String stdout;
        final String stderr;

        Result(boolean ok, int exitCode, String stdout, String stderr) {
            this.ok = ok;
            this.exitCode = exitCode;
            this.stdout = stdout == null ? "" : stdout;
            this.stderr = stderr == null ? "" : stderr;
        }

        String compact() {
            String text = (stdout + " " + stderr).trim();
            if (text.length() > 300) text = text.substring(0, 300) + "…";
            return text.isEmpty() ? "exit " + exitCode : "exit " + exitCode + ": " + text;
        }
    }
}
