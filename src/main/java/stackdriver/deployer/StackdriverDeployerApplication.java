package stackdriver.deployer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.*;
import org.cloudfoundry.operations.serviceadmin.CreateServiceBrokerRequest;
import org.cloudfoundry.operations.services.BindServiceInstanceRequest;
import org.cloudfoundry.operations.services.CreateServiceInstanceRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SpringBootApplication
public class StackdriverDeployerApplication {

	public static void main(String[] args) {
		SpringApplication.run(StackdriverDeployerApplication.class, args);
	}
}

@Component
class DeployerCommandLineRunner implements CommandLineRunner {

	private final Sanitizer sanitizer;
	private final ManifestService manifestService;
	private final Log log = LogFactory.getLog(getClass());

	private final CloudFoundryOperations cloudFoundryOperations;

	public DeployerCommandLineRunner(
			CloudFoundryOperations cloudFoundryOperations,
			ManifestService manifestService,
			Sanitizer sanitizer) {
		this.cloudFoundryOperations = cloudFoundryOperations;
		this.manifestService = manifestService;
		this.sanitizer = sanitizer;
	}

	@Override
	public void run(String... strings) throws Exception {
		String jsonPath = "/Users/jlong/Desktop/gcp.json";
		String gcpServiceBrokerApplicationURL = this.deployGcpServiceBrokerApplication(jsonPath);
		this.convertApplicationIntoServiceBroker("gcp-service-broker", gcpServiceBrokerApplicationURL, "admin", "admin");
		log.info("service broker application URL = " + gcpServiceBrokerApplicationURL);

		String stackdriverProxyApplication = deployStackdriverProxyApplication();
		log.info("stackdriverProxyApplication = " + stackdriverProxyApplication);
	}

	private String deployGcpServiceBrokerApplication(String jsonPath) throws Exception {
		String inputUploadDirectory = "/gcp-service-broker-assets/";
		String binaryName = "gcp-service-broker";
		String manifestName = "manifest.yml";
		String procfile = "Procfile";
		Path uploadPath = stageTemporaryUploadDir(inputUploadDirectory, binaryName, manifestName, procfile);
		String planName = "512mb";
		String serviceInstanceName = "gcp-service-broker-db";
		String serviceTypeKey = "p-mysql";

		this.provisionServiceInstance(serviceTypeKey, planName, serviceInstanceName);
		ApplicationManifest manifest = this.manifestService.applicationManifestFrom(new File(uploadPath.toFile(), "manifest.yml").toPath());
		this.pushApplicationUsingManifest(uploadPath, manifest, false);

		ApplicationEnvironments environments = this.cloudFoundryOperations.applications().getEnvironments(GetApplicationEnvironmentsRequest.builder().name(manifest.getName()).build()).block();
		Map<String, String> env = readDatabaseEnvironment(environments, serviceTypeKey);
		env.put("ROOT_SERVICE_ACCOUNT_JSON", Files.readAllLines(Paths.get(jsonPath))
				.stream().collect(Collectors.joining(System.lineSeparator())));
		env.forEach((k, v) -> cloudFoundryOperations.applications().setEnvironmentVariable(SetEnvironmentVariableApplicationRequest
				.builder()
				.name(manifest.getName())
				.variableName(k)
				.variableValue(v)
				.build()).block());
		this.cloudFoundryOperations.applications().start(StartApplicationRequest.builder().name(manifest.getName()).build()).block();
		ApplicationDetail applicationDetail = this.cloudFoundryOperations.applications()
				.get(GetApplicationRequest.builder().name(manifest.getName()).build())
				.block();
		return url(applicationDetail.getUrls().get(0));
	}

	private void provisionServiceInstance(String serviceName,
	                                      String planName,
	                                      String serviceInstanceName) {
		this.cloudFoundryOperations.services()
				.createInstance(CreateServiceInstanceRequest.builder()
						.planName(planName)
						.serviceInstanceName(serviceInstanceName)
						.serviceName(serviceName)
						.build())
				.block();
	}

	private String deployStackdriverProxyApplication() {


		return null;
	}

	private void convertApplicationIntoServiceBroker(
			String serviceBrokerName,
			String stackdriverProxyApplicationUrl,
			String usr, String pw) {

		this.cloudFoundryOperations
				.serviceAdmin()
				.create(
						CreateServiceBrokerRequest.builder()
								.spaceScoped(true)
								.url(stackdriverProxyApplicationUrl)
								.name(serviceBrokerName)
								.username(usr)
								.password(pw)
								.build())
				.block();

	}

	public void pushApplicationUsingManifest(Path binaryFile,
	                                         ApplicationManifest manifest,
	                                         boolean start) {

		PushApplicationRequest request = this.manifestService.fromApplicationManifest(binaryFile, manifest);
		cloudFoundryOperations.applications().push(request).block();

		if (request.getNoStart() != null && request.getNoStart()) {
			Assert.notNull(manifest, "the manifest for application " + binaryFile.getFileName() + " is null! Can't proceed.");
			if (manifest.getServices() != null) {
				manifest.getServices().forEach(
						svc -> {
							cloudFoundryOperations.services()
									.bind(
											BindServiceInstanceRequest.builder()
													.applicationName(request.getName()).serviceInstanceName(svc)
													.build()).block();
							log.debug("bound service '" + svc + "' to '" + request.getName() + "'.");
						});
			}
			if (manifest.getEnvironmentVariables() != null) {
				manifest.getEnvironmentVariables().forEach(
						(e, v) -> {
							cloudFoundryOperations.applications()
									.setEnvironmentVariable(
											SetEnvironmentVariableApplicationRequest.builder()
													.name(request.getName()).variableName(e)
													.variableValue("" + v).build()).block();
							log.debug("set environment variable '" + e + "' to the value '"
									+ this.sanitizer.sanitize(e, "" + v) + "' for application "
									+ request.getName());
						});
			}
			if (start) {
				cloudFoundryOperations.applications()
						.start(
								StartApplicationRequest.builder().name(request.getName()).build())
						.block();
			}
		}
	}


	private static Path copyResourceToFile(Resource resource, Path targetPath) {
		Assert.notNull(resource, "the resource must be non-null!");
		Assert.isTrue(resource.exists(), "the resource " + resource.getFilename() + " must exist");
		try (InputStream in = resource.getInputStream()) {
			Files.copy(in, targetPath);
			return targetPath;
		} catch (IOException e) {
			throw new RuntimeException("Exception while copying file to path: " + targetPath.toString(), e);
		}
	}


	@SuppressWarnings("unchecked")
	private static Map<String, String> readDatabaseEnvironment(ApplicationEnvironments environments, String serviceTypeKey) {
		Map<String, Object> vcapServices = (Map<String, Object>) environments.getSystemProvided().get("VCAP_SERVICES");
		List<Object> list = (List<Object>) vcapServices.get(serviceTypeKey);
		Map<String, Object> vars = (Map<String, Object>) list.get(0);
		Map<String, Object> credentials = (Map<String, Object>) vars.get("credentials");
		Map<String, String> env = new HashMap<>();
		env.put("DB_NAME", credentials.get("name").toString());
		env.put("DB_PORT", credentials.get("port").toString());
		env.put("DB_HOST", credentials.get("hostname").toString());
		env.put("DB_USERNAME", credentials.get("username").toString());
		env.put("DB_PASSWORD", credentials.get("password").toString());
		env.put("SECURITY_USER_NAME", "admin");
		env.put("SECURITY_USER_PASSWORD", "admin");
		return env;
	}

	private static Path stageTemporaryUploadDir(String inputUploadDirectory,
	                                            String binaryName,
	                                            String manifestName,
	                                            String... others) throws IOException {
		Resource rootGcpAssetsDirectory = new ClassPathResource(inputUploadDirectory);
		Assert.isTrue(rootGcpAssetsDirectory.exists(), "the directory containing the GCP assets should exist");

		Resource binary = rootGcpAssetsDirectory.createRelative(binaryName);
		Assert.isTrue(binary.exists(), "the gcp service broker binary should exist");

		Resource manifest = rootGcpAssetsDirectory.createRelative(manifestName);
		Assert.isTrue(manifest.exists(), "the manifest should exist");

		File tmpDir = Files.createTempDirectory("staging").toFile();
		Assert.isTrue(tmpDir.exists() || tmpDir.mkdirs(), "tried to create a staging directory");

		copyResourceToFile(binary, new File(tmpDir, binaryName).toPath());
		copyResourceToFile(manifest, new File(tmpDir, "manifest.yml").toPath());

		for (String o : others) {
			copyResourceToFile(rootGcpAssetsDirectory.createRelative(o), new File(tmpDir, o).toPath());
		}

		return tmpDir.toPath();
	}

	private static String url(String u) {
		return StringUtils.hasText(u) && u.toLowerCase().startsWith("http") ? u : "http://" + u;
	}
}