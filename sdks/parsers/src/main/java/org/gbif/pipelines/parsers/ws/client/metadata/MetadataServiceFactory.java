package org.gbif.pipelines.parsers.ws.client.metadata;

import java.util.concurrent.TimeUnit;

import org.gbif.pipelines.parsers.config.WsConfig;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MetadataServiceFactory {

  private static MetadataService instance;

  public static synchronized MetadataService create(WsConfig config) {

    if (instance == null) {
      // create client
      OkHttpClient client =
          new OkHttpClient.Builder()
              .connectTimeout(config.getTimeout(), TimeUnit.SECONDS)
              .readTimeout(config.getTimeout(), TimeUnit.SECONDS)
              .build();

      // create service
      Retrofit retrofit =
          new Retrofit.Builder()
              .client(client)
              .baseUrl(config.getBasePath())
              .addConverterFactory(JacksonConverterFactory.create())
              .validateEagerly(true)
              .build();

      instance = retrofit.create(MetadataService.class);
    }

    return instance;
  }
}
