package com.example.demo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Starts the local dependencies even when only some Compose services are already running. */
final class LocalDockerServices {

	private static final String READY_PROPERTY = "techtra.local-services-ready";
	private static final Duration DOCKER_START_TIMEOUT = Duration.ofMinutes(2);
	private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(3);

	private LocalDockerServices() {
	}

	static void startIfNeeded() {
		if (System.getProperty(READY_PROPERTY) != null
				|| hasText(System.getenv("SPRING_DATASOURCE_URL"))
				|| "false".equalsIgnoreCase(System.getenv("SPRING_DOCKER_COMPOSE_ENABLED"))) {
			return;
		}

		Path composeFile = Path.of("compose.yaml").toAbsolutePath();
		if (!Files.isRegularFile(composeFile)) {
			return; // A packaged deployment does not include the local Compose file.
		}

		String docker = dockerExecutable();
		if (!dockerIsReady(docker)) {
			startDockerDesktop();
			waitForDocker(docker);
		}

		System.out.println("Starting local PostgreSQL and Qdrant with Docker Compose...");
		runCommand(COMMAND_TIMEOUT, docker, "compose", "-f", composeFile.toString(),
				"up", "-d", "--wait", "--wait-timeout", "120", "postgres", "qdrant");
		System.setProperty(READY_PROPERTY, "true");
	}

	private static String dockerExecutable() {
		Path windowsDocker = Path.of("C:\\Program Files\\Docker\\Docker\\resources\\bin\\docker.exe");
		return Files.isRegularFile(windowsDocker) ? windowsDocker.toString() : "docker";
	}

	private static boolean dockerIsReady(String docker) {
		try {
			Process process = new ProcessBuilder(docker, "info", "--format", "{{.ServerVersion}}")
					.redirectOutput(ProcessBuilder.Redirect.DISCARD)
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();
			if (!process.waitFor(10, TimeUnit.SECONDS)) {
				process.destroyForcibly();
				return false;
			}
			return process.exitValue() == 0;
		} catch (IOException e) {
			throw new IllegalStateException("Docker CLI is missing. Install Docker Desktop before running locally.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while checking Docker.", e);
		}
	}

	private static void startDockerDesktop() {
		if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
			throw new IllegalStateException("Start Docker, then run the application again.");
		}
		Path desktop = Path.of("C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe");
		if (!Files.isRegularFile(desktop)) {
			throw new IllegalStateException("Docker Desktop was not found. Install it, then run again.");
		}
		System.out.println("Starting Docker Desktop; waiting for the engine...");
		runCommand(Duration.ofSeconds(20), "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
				"Start-Process -FilePath 'C:\\Program Files\\Docker\\Docker\\Docker Desktop.exe' -WindowStyle Hidden");
	}

	private static void waitForDocker(String docker) {
		long deadline = System.nanoTime() + DOCKER_START_TIMEOUT.toNanos();
		while (System.nanoTime() < deadline) {
			if (dockerIsReady(docker)) {
				return;
			}
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for Docker Desktop.", e);
			}
		}
		throw new IllegalStateException("Docker Desktop did not become ready within two minutes. Check its status and run again.");
	}

	private static void runCommand(Duration timeout, String... command) {
		try {
			Process process = new ProcessBuilder(command).inheritIO().start();
			if (!process.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)) {
				process.destroyForcibly();
				throw new IllegalStateException("Timed out starting local Docker services.");
			}
			if (process.exitValue() != 0) {
				throw new IllegalStateException("Docker Compose failed. Review the output above in the Run console.");
			}
		} catch (IOException e) {
			throw new IllegalStateException("Could not start local Docker services.", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while starting local Docker services.", e);
		}
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
