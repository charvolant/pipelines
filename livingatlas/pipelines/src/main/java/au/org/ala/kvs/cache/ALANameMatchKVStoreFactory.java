package au.org.ala.kvs.cache;

import au.org.ala.kvs.ALAPipelinesConfig;
import au.org.ala.names.ws.api.NameMatchService;
import au.org.ala.names.ws.api.NameSearch;
import au.org.ala.names.ws.api.NameUsageMatch;
import au.org.ala.names.ws.client.ALANameUsageMatchServiceClient;
import au.org.ala.utils.WsUtils;
import au.org.ala.ws.ClientConfiguration;
import java.io.IOException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.gbif.kvs.KeyValueStore;
import org.gbif.kvs.cache.KeyValueCache;
import org.gbif.kvs.hbase.Command;
import org.gbif.pipelines.core.config.model.WsConfig;
import org.gbif.pipelines.core.functions.SerializableSupplier;

@Slf4j
public class ALANameMatchKVStoreFactory {

  private final KeyValueStore<NameSearch, NameUsageMatch> kvStore;
  private static volatile ALANameMatchKVStoreFactory instance;
  private static final Object MUTEX = new Object();

  @SneakyThrows
  private ALANameMatchKVStoreFactory(ALAPipelinesConfig config) {
    this.kvStore = create(config);
  }

  public static KeyValueStore<NameSearch, NameUsageMatch> getInstance(ALAPipelinesConfig config) {
    if (instance == null) {
      synchronized (MUTEX) {
        if (instance == null) {
          instance = new ALANameMatchKVStoreFactory(config);
        }
      }
    }
    return instance.kvStore;
  }

  /**
   * Returns ala name matching key value store.
   *
   * @return A key value store backed by a {@link ALANameUsageMatchServiceClient}
   * @throws IOException if unable to build the client
   */
  public static KeyValueStore<NameSearch, NameUsageMatch> create(ALAPipelinesConfig config)
      throws IOException {
    WsConfig ws = config.getAlaNameMatch();
    ClientConfiguration clientConfiguration = WsUtils.createConfiguration(ws);

    ALANameUsageMatchServiceClient wsClient =
        new ALANameUsageMatchServiceClient(clientConfiguration);
    Command closeHandler =
        () -> {
          try {
            wsClient.close();
          } catch (Exception e) {
            log.error("Unable to close", e);
            throw new RuntimeException(e);
          }
        };

    return cache2kBackedKVStore(wsClient, closeHandler, config);
  }

  /** Builds a KV Store backed by the rest client. */
  private static KeyValueStore<NameSearch, NameUsageMatch> cache2kBackedKVStore(
      NameMatchService nameMatchService, Command closeHandler, ALAPipelinesConfig config) {

    KeyValueStore<NameSearch, NameUsageMatch> kvs =
        new KeyValueStore<NameSearch, NameUsageMatch>() {
          @Override
          public NameUsageMatch get(NameSearch key) {
            try {
              return nameMatchService.match(key);
            } catch (Exception ex) {
              log.error("Error contacting the species match service", ex);
              throw new RuntimeException(ex);
            }
          }

          @Override
          public void close() {
            closeHandler.execute();
          }
        };
    return KeyValueCache.cache(
        kvs, config.getAlaNameMatch().getCacheSizeMb(), NameSearch.class, NameUsageMatch.class);
  }

  public static SerializableSupplier<KeyValueStore<NameSearch, NameUsageMatch>> getInstanceSupplier(
      ALAPipelinesConfig config) {
    return () -> ALANameMatchKVStoreFactory.getInstance(config);
  }
}
