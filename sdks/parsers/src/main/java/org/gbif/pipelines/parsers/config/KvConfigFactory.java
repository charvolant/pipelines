package org.gbif.pipelines.parsers.config;

import java.nio.file.Path;
import java.util.Properties;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KvConfigFactory {

  public static final String TAXONOMY_PREFIX = "taxonomy";
  public static final String GEOCODE_PREFIX = "geocode";
  public static final String AUSTRALIA_PREFIX = "australia.spatial";

  // property suffixes
  private static final String WS_BASE_PATH_PROP = "gbif.api.url";
  private static final String ZOOKEEPER_PROP = "zookeeper.url";
  private static final String WS_TIMEOUT_PROP = ".ws.timeout";
  private static final String CACHE_SIZE_PROP = ".ws.cache.sizeMb";
  private static final String NUM_OF_KEY_BUCKETS = ".numOfKeyBuckets";
  private static final String TABLE_NAME = ".tableName";

  // property defaults
  private static final String DEFAULT_TIMEOUT_SEC = "60";
  private static final String DEFAULT_CACHE_SIZE_MB = "64";
  private static final String DEFAULT_NUM_OF_KEY_BUCKETS = "10";

  public static KvConfig create(@NonNull String keyPrefix, @NonNull Path propertiesPath) {
    // load properties or throw exception if cannot be loaded
    Properties props = ConfigFactory.loadProperties(propertiesPath);

    // get the base path or throw exception if not present
    String basePath = ConfigFactory.getKey(props, WS_BASE_PATH_PROP) + "/v1/";
    String zookeeperUrl = ConfigFactory.getKey(props, ZOOKEEPER_PROP);
    String tableName = ConfigFactory.getKey(props, keyPrefix + TABLE_NAME);

    long cacheSize = Long.parseLong(props.getProperty(keyPrefix + CACHE_SIZE_PROP, DEFAULT_CACHE_SIZE_MB));
    long timeout = Long.parseLong(props.getProperty(keyPrefix + WS_TIMEOUT_PROP, DEFAULT_TIMEOUT_SEC));
    int numOfKeyBuckets = Integer.parseInt(props.getProperty(keyPrefix + NUM_OF_KEY_BUCKETS, DEFAULT_NUM_OF_KEY_BUCKETS));

    return KvConfig.create(basePath, timeout, cacheSize, tableName, zookeeperUrl, numOfKeyBuckets);
  }

  public static KvConfig create(String baseApiPath, String zookeeperUrl, int numOfKeyBuckets, String tableName) {
    long timeoutInSec = Long.parseLong(DEFAULT_TIMEOUT_SEC);
    long cacheInMb = Long.parseLong(DEFAULT_CACHE_SIZE_MB);
    return KvConfig.create(baseApiPath, timeoutInSec, cacheInMb, tableName, zookeeperUrl, numOfKeyBuckets);
  }

  public static KvConfig create(String baseApiPath, int numOfKeyBuckets, String tableName) {
    long timeoutInSec = Long.parseLong(DEFAULT_TIMEOUT_SEC);
    long cacheInMb = Long.parseLong(DEFAULT_CACHE_SIZE_MB);
    return KvConfig.create(baseApiPath, timeoutInSec, cacheInMb, tableName, null, numOfKeyBuckets);
  }

  public static KvConfig create(String baseApiPath, long timeoutInSec, long cacheInMb, String zookeeperUrl,
      int numOfKeyBuckets, String tableName) {
    return KvConfig.create(baseApiPath, timeoutInSec, cacheInMb, tableName, zookeeperUrl, numOfKeyBuckets);
  }

}
