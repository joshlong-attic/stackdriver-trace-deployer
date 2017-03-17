package stackdriver.deployer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import stackdriver.deployer.cloudfoundry.DeployService;
import stackdriver.deployer.cloudfoundry.ManifestService;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
class DeployRunner implements CommandLineRunner {

	private final Log log = LogFactory.getLog(getClass());
	private final ManifestService manifestService;
	private final DeployService deployService;
	private final CloudFoundryOperations cloudFoundryOperations;

	DeployRunner(DeployService deployService,
	             CloudFoundryOperations cloudFoundryOperations,
	             ManifestService manifestService) {
		this.deployService = deployService;
		this.cloudFoundryOperations = cloudFoundryOperations;
		this.manifestService = manifestService;
	}

	@Override
	public void run(String... strings) throws Exception {
		String jsonPath = "/Users/jlong/Desktop/gcp.json";

		if (gcpServiceBrokerNotProvisioned()) {
			String gcpServiceBrokerApplicationURL = this.deployGcpServiceBrokerApplication(
					jsonPath, "p-mysql", "512mb");
			this.deployService.convertApplicationIntoServiceBroker(
					"gcp-service-broker", gcpServiceBrokerApplicationURL, "admin", "admin");
			log.info("service broker application URL = " + gcpServiceBrokerApplicationURL);
		}

		String stackdriverProxyApplication = deployStackdriverProxyApplication();
		log.info("stackdriverProxyApplication = " + stackdriverProxyApplication);
	}

	// TODO detect if the service broker is already deployed
	private boolean gcpServiceBrokerNotProvisioned() {
		return true;
	}

	private String deployStackdriverProxyApplication() {
		String inputUploadDirectory = "/stackdriver-proxy-assets/",
				binaryName = "proxy.jar",
				manifestName = "manifest.yml";
		Path uploadPath = this.deployService.stageTemporaryUploadDir(inputUploadDirectory,
				binaryName, manifestName);
		this.deployService.provisionServiceInstance("google-stackdriver-trace", "default", "proxy-stackdriver-trace");
		ApplicationManifest manifest = this.manifestService
				.applicationManifestFrom(new File(uploadPath.toFile(), "manifest.yml").toPath());
		this.deployService.pushApplicationUsingManifest(new File(uploadPath.toFile(), "proxy.jar").toPath(),
				manifest, true);
		ApplicationDetail applicationDetail = this.cloudFoundryOperations.applications()
				.get(GetApplicationRequest.builder().name(manifest.getName()).build())
				.block();
		return url(applicationDetail.getUrls().get(0));
	}

	private String deployGcpServiceBrokerApplication(String jsonPath, String serviceTypeKey, String planName) throws Exception {
		String inputUploadDirectory = "/gcp-service-broker-assets/";
		String binaryName = "gcp-service-broker";
		String manifestName = "manifest.yml";
		String procfile = "Procfile";
		Path uploadPath = this.deployService.stageTemporaryUploadDir(inputUploadDirectory,
				binaryName, manifestName, procfile);
		String serviceInstanceName = "gcp-service-broker-db";

		this.deployService.provisionServiceInstance(serviceTypeKey, planName, serviceInstanceName);
		ApplicationManifest manifest = this.manifestService.applicationManifestFrom(
				new File(uploadPath.toFile(), "manifest.yml").toPath());
		this.deployService.pushApplicationUsingManifest(uploadPath, manifest, false);

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

	private static String url(String u) {
		return StringUtils.hasText(u) && u.toLowerCase().startsWith("http") ? u : "http://" + u;
	}
}
