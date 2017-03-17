package stackdriver.deployer.cloudfoundry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.CloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationManifest;
import org.cloudfoundry.operations.applications.PushApplicationRequest;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.PathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class ManifestService {

	private final Log log = LogFactory.getLog(getClass());
	private final CloudFoundryOperations cf;

	public ManifestService(CloudFoundryOperations cf) {
		this.cf = cf;
	}

	public PushApplicationRequest fromApplicationManifest(Path path,
	                                                      ApplicationManifest applicationManifest) {
		PushApplicationRequest.Builder builder = PushApplicationRequest.builder();
  	builder.application(path);
		if (applicationManifest.getHosts() != null
				&& applicationManifest.getHosts().size() > 0) {
			builder.host(applicationManifest.getHosts().iterator().next());
		}
		if (StringUtils.hasText(applicationManifest.getBuildpack())) {
			builder.buildpack(applicationManifest.getBuildpack());
		}
		if (applicationManifest.getMemory() != null) {
			builder.memory(applicationManifest.getMemory());
		}
		if (applicationManifest.getDisk() != null) {
			builder.diskQuota(applicationManifest.getDisk());
		}
		if (applicationManifest.getInstances() != null) {
			builder.instances(applicationManifest.getInstances());
		}
		if (StringUtils.hasText(applicationManifest.getName())) {
			builder.name(applicationManifest.getName());
		}
		if (applicationManifest.getDomains() != null
				&& applicationManifest.getDomains().size() > 0) {
			builder.domain(applicationManifest.getDomains().iterator().next());
		}
		if (applicationManifest.getEnvironmentVariables() != null
				&& applicationManifest.getEnvironmentVariables().size() > 0) {
			builder.noStart(true);
		}
		if (applicationManifest.getServices() != null
				&& applicationManifest.getServices().size() > 0) {
			builder.noStart(true);
		}
		builder.stagingTimeout(Duration.ofMinutes(1));
		builder.startupTimeout(Duration.ofMinutes(1));
		return builder.build();
	}

	public ApplicationManifest applicationManifestFrom(
			Path manifestFile) {
		log.debug("manifest: " + manifestFile.toString());
		YamlMapFactoryBean yamlMapFactoryBean = new YamlMapFactoryBean();
		yamlMapFactoryBean.setResources(new PathResource(manifestFile));
		yamlMapFactoryBean.afterPropertiesSet();
		Map<String, Object> manifestYmlFile = yamlMapFactoryBean.getObject();
		ApplicationManifest.Builder builder = ApplicationManifest.builder();
		Map lhm = Map.class.cast(List.class
				.cast(manifestYmlFile.get("applications")).iterator().next());
		optionalIfExists(lhm, "name", String.class).ifPresent(builder::name);
		optionalIfExists(lhm, "buildpack", String.class).ifPresent(
				builder::buildpack);
		optionalIfExists(lhm, "memory", String.class).ifPresent(mem -> {
			builder.memory(1024);
		});
		optionalIfExists(lhm, "disk", Integer.class).ifPresent(builder::disk);
		optionalIfExists(lhm, "command", String.class).ifPresent(builder::command);
		optionalIfExists(lhm, "domains", String.class).ifPresent(builder::domain);
		optionalIfExists(lhm, "instances", Integer.class).ifPresent(
				builder::instances);
		optionalIfExists(lhm, "host", String.class).ifPresent(host -> {
			String rw = "${random-word}";
			if (host.contains(rw)) {
				builder.host(host.replace(rw, UUID.randomUUID().toString()));
			} else {
				builder.host(host);
			}
		});
		optionalIfExists(lhm, "services", Object.class).ifPresent(svcs -> {
			if (svcs instanceof String) {
				builder.host(String.class.cast(svcs));
			} else if (svcs instanceof Iterable) {
				builder.addAllServices(Iterable.class.cast(svcs));
			}
		});
		optionalIfExists(lhm, ("env"), Map.class).ifPresent(
				builder::putAllEnvironmentVariables);

		return builder.build();
	}

	private static <T> T ifExists(Map m, String k, Class<T> tClass) {
		if (m.containsKey(k)) {
			return tClass.cast(m.get(k));
		}
		return null;
	}


	private static <T> Optional<T> optionalIfExists(Map m, String k,
	                                                Class<T> tClass) {
		return Optional.ofNullable(ifExists(m, k, tClass));
	}


}
