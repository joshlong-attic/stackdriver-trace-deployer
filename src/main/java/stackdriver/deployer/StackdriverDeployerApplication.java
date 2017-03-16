package stackdriver.deployer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@SpringBootApplication
public class StackdriverDeployerApplication {

	public static void main(String[] args) {
		SpringApplication.run(StackdriverDeployerApplication.class, args);
	}
}

@Component
class DeployerCommandLineRunner implements CommandLineRunner {

	private final Log log = LogFactory.getLog(getClass());
	private final CloudFoundryOperations cloudFoundryOperations;

	public DeployerCommandLineRunner(CloudFoundryOperations cloudFoundryOperations) {
		this.cloudFoundryOperations = cloudFoundryOperations;
	}

	@Override
	public void run(String... strings) throws Exception {

		String gcpServiceBrokerApplication = deployGcpServiceBrokerApplication();
		convertApplicationIntoServiceBroker(gcpServiceBrokerApplication);
		log.info("gcpServiceBrokerApplication = " + gcpServiceBrokerApplication);

		String stackdriverProxyApplication = deployStackdriverProxyApplication();
		log.info("stackdriverProxyApplication = " + stackdriverProxyApplication);
	}

	private String deployGcpServiceBrokerApplication() throws Exception {

		Resource rootGcpAssetsDirectory
				= new ClassPathResource("/gcp-service-broker-assets/");
		Assert.isTrue(rootGcpAssetsDirectory.exists(), "the directory containing the GCP assets should exist");

		Resource binary = rootGcpAssetsDirectory.createRelative("gcp-service-broker");
		Assert.isTrue(binary.exists(), "the gcp service broker binary should exist");

		Resource manifest = rootGcpAssetsDirectory.createRelative("manifest.yml");
		Assert.isTrue(manifest.exists(), "the manifest should exist");



		return null;
	}

	private String deployStackdriverProxyApplication() {


		return null;
	}

	private void convertApplicationIntoServiceBroker(String stackdriverProxyApplication) {

	}
}