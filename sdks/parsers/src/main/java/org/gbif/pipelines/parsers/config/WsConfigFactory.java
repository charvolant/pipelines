package org.gbif.pipelines.parsers.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * Creates the configuration to use a specific WS.
 *
 * <p>By default it reads the configuration from the "http.properties" file.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class WsConfigFactory {

  public static final String METADATA_PREFIX = "metadata";
  public static final String BLAST_PREFIX = "blast";

  // property suffixes
  private static final String WS_BASE_PATH_PROP = "gbif.api.url";
  private static final String WS_TIMEOUT_PROP = ".ws.timeout";
  private static final String CACHE_SIZE_PROP = ".ws.cache.sizeMb";
  private static final String URL_PROP = ".ws.url";

  // property defaults
  private static final String DEFAULT_TIMEOUT = "60";
  private static final String DEFAULT_CACHE_SIZE_MB = "64";

  public static WsConfig create(@NonNull String wsName, @NonNull Path propertiesPath) {
    // load properties or throw exception if cannot be loaded
    Properties props = loadProperties(propertiesPath);

    // get the base path or throw exception if not present

    String url = props.getProperty(wsName + URL_PROP);
    if (Strings.isNullOrEmpty(url)) {
      url = props.getProperty(WS_BASE_PATH_PROP);
      if (Strings.isNullOrEmpty(url)) {
        throw new IllegalArgumentException("WS base path is required");
      }
    }

    String cacheSize = props.getProperty(wsName + CACHE_SIZE_PROP, DEFAULT_CACHE_SIZE_MB);
    String timeout = props.getProperty(wsName + WS_TIMEOUT_PROP, DEFAULT_TIMEOUT);

    return new WsConfig(url, timeout, cacheSize);
  }

  /** Creates a {@link WsConfig} from a url and uses default timeout and cache size. */
  public static WsConfig create(String url) {
    return create(url, DEFAULT_TIMEOUT, DEFAULT_CACHE_SIZE_MB);
  }

  /** Creates a {@link WsConfig} from a url and uses default timeout and cache size. */
  public static WsConfig create(String url, String timeout, String cacheSize) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(url), "url is required");
    return new WsConfig(url, timeout, cacheSize);
  }

  /** Creates a {@link WsConfig} from a url and uses default timeout and cache size. */
  public static WsConfig create(String url, long timeout, long cacheSize) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(url), "url is required");
    return new WsConfig(url, timeout, cacheSize);
  }

  /**
   *
   */
  private static Properties loadProperties(Path propertiesPath) {
    Function<Path, InputStream> absolute =
        path -> {
          try {
            return new FileInputStream(path.toFile());
          } catch (Exception ex) {
            String msg = "Properties with absolute path could not be read from " + propertiesPath;
            throw new IllegalArgumentException(msg, ex);
          }
        };

    Function<Path, InputStream> resource =
        path -> Thread.currentThread().getContextClassLoader().getResourceAsStream(path.toString());

    Function<Path, InputStream> function = propertiesPath.isAbsolute() ? absolute : resource;

    Properties props = new Properties();
    try (InputStream in = function.apply(propertiesPath)) {
      // read properties from input stream
      props.load(in);
    } catch (Exception ex) {
      String msg = "Properties with absolute path could not be read from " + propertiesPath;
      throw new IllegalArgumentException(msg, ex);
    }

    return props;
  }
}