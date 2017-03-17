package stackdriver.deployer.cloudfoundry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.applications.PushApplicationRequest;
import org.cloudfoundry.operations.applications.SetEnvironmentVariableApplicationRequest;
import org.cloudfoundry.operations.applications.StartApplicationRequest;
import org.cloudfoundry.operations.serviceadmin.CreateServiceBrokerRequest;
import org.cloudfoundry.operations.services.BindServiceInstanceRequest;
import org.cloudfoundry.operations.services.CreateServiceInstanceRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DeployService {

	private final Sanitizer sanitizer;
	private final ManifestService manifestService;
	private final CloudFoundryOperations cloudFoundryOperations;

	private final Log log = LogFactory.getLog(getClass());

	public DeployService(Sanitizer sanitizer,
	                     ManifestService manifestService,
	                     CloudFoundryOperations cloudFoundryOperations) {
		this.sanitizer = sanitizer;
		this.manifestService = manifestService;
		this.cloudFoundryOperations = cloudFoundryOperations;
	}

	public void provisionServiceInstance(String serviceName,
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


	public void convertApplicationIntoServiceBroker(
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

	public Path stageTemporaryUploadDir(String inputUploadDirectory,
	                                    String binaryName,
	                                    String manifestName,
	                                    String... others) {
		try {
			Resource rootAssetsDirectory = new ClassPathResource(inputUploadDirectory);
			Assert.isTrue(rootAssetsDirectory.exists(), "the directory containing the assets should exist");

			Resource binary = rootAssetsDirectory.createRelative(binaryName);
			Assert.isTrue(binary.exists(), "the binary " +
					binaryName+" should exist");

			Resource manifest = rootAssetsDirectory.createRelative(manifestName);
			Assert.isTrue(manifest.exists(), "the manifest should exist");

			File tmpDir = Files.createTempDirectory("staging").toFile();
			Assert.isTrue(tmpDir.exists() || tmpDir.mkdirs(), "tried to create a staging directory");

			copyResourceToFile(binary, new File(tmpDir, binaryName).toPath());
			copyResourceToFile(manifest, new File(tmpDir, "manifest.yml").toPath());

			for (String o : others) {
				copyResourceToFile(rootAssetsDirectory.createRelative(o), new File(tmpDir, o).toPath());
			}

			return tmpDir.toPath();
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static String url(String u) {
		return StringUtils.hasText(u) && u.toLowerCase().startsWith("http") ? u : "http://" + u;
	}
}
