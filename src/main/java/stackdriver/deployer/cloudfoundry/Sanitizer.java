package stackdriver.deployer.cloudfoundry;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

@Component
class Sanitizer {

	private final Log log = LogFactory.getLog(getClass());
	private Method sanitizeMethod;
	private Object sanitizerObject;

	Sanitizer() {
		try {
			String sanitizerClass = "org.springframework.boot.actuate.endpoint.Sanitizer";
			Class<?> sanitizer = Class.forName(sanitizerClass);
			Constructor<?> ctor = sanitizer.getDeclaredConstructor();
			ctor.setAccessible(true);
			this.sanitizerObject = ctor.newInstance();
			this.sanitizeMethod = sanitizer.getMethod("sanitize", String.class,
					Object.class);
			this.sanitizeMethod.setAccessible(true);
		} catch (Throwable th) {
			this.log.error(th);
		}
	}

	String sanitize(String k, String v) {
		try {
			return String.class.cast(sanitizeMethod.invoke(sanitizerObject,
					"" + k.toLowerCase(), v));
		} catch (Exception e) {
			log.debug("couldn't sanitize value for key " + k + ".");
			log.error(e);
		}
		return v;
	}

}
