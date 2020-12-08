package au.org.ala.pipelines.beam;

import static org.gbif.pipelines.common.PipelinesVariables.Pipeline.AVRO_EXTENSION;

import au.org.ala.kvs.ALAPipelinesConfig;
import au.org.ala.kvs.ALAPipelinesConfigFactory;
import au.org.ala.kvs.cache.ALAAttributionKVStoreFactory;
import au.org.ala.kvs.cache.SDSCheckKVStoreFactory;
import au.org.ala.kvs.cache.SDSReportKVStoreFactory;
import au.org.ala.kvs.client.SDSConservationServiceFactory;
import au.org.ala.pipelines.transforms.ALASensitiveDataRecordTransform;
import au.org.ala.pipelines.transforms.ALATaxonomyTransform;
import au.org.ala.pipelines.transforms.ALAUUIDTransform;
import au.org.ala.pipelines.util.VersionInfo;
import au.org.ala.utils.ALAFsUtils;
import au.org.ala.utils.CombinedYamlConfiguration;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.function.UnaryOperator;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.transforms.join.CoGroupByKey;
import org.apache.beam.sdk.transforms.join.KeyedPCollectionTuple;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.gbif.pipelines.common.beam.metrics.MetricsHandler;
import org.gbif.pipelines.common.beam.options.InterpretationPipelineOptions;
import org.gbif.pipelines.common.beam.options.PipelinesOptionsFactory;
import org.gbif.pipelines.common.beam.utils.PathBuilder;
import org.gbif.pipelines.io.avro.*;
import org.gbif.pipelines.transforms.core.LocationTransform;
import org.gbif.pipelines.transforms.core.TaxonomyTransform;
import org.gbif.pipelines.transforms.core.TemporalTransform;
import org.gbif.pipelines.transforms.core.VerbatimTransform;
import org.slf4j.MDC;

/** ALA Beam pipeline to process sensitive data. */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ALAInterpretedToSensitivePipeline {
  public static final boolean USE_GBIF_TAXONOMY = false;

  public static void main(String[] args) throws IOException {
    VersionInfo.print();
    String[] combinedArgs = new CombinedYamlConfiguration(args).toArgs("general", "sensitive");
    InterpretationPipelineOptions options =
        PipelinesOptionsFactory.createInterpretation(combinedArgs);
    run(options);
  }

  public static void run(InterpretationPipelineOptions options) throws IOException {
    ALAPipelinesConfig config =
        ALAPipelinesConfigFactory.getInstance(
                options.getHdfsSiteConfig(), options.getCoreSiteConfig(), options.getProperties())
            .get();

    String id = Long.toString(LocalDateTime.now().toEpochSecond(ZoneOffset.UTC));

    MDC.put("datasetId", options.getDatasetId());
    MDC.put("attempt", options.getAttempt().toString());
    MDC.put("step", "INTERPRETED_TO_SENSITIVE");

    log.info("Adding step 1: Options");
    UnaryOperator<String> inputPathFn =
        t -> PathBuilder.buildPathInterpretUsingTargetPath(options, t, "*" + AVRO_EXTENSION);
    UnaryOperator<String> outputPathFn =
        t -> PathBuilder.buildPathInterpretUsingTargetPath(options, t, id);
    UnaryOperator<String> identifiersPathFn =
        t -> ALAFsUtils.buildPathIdentifiersUsingTargetPath(options, t, "*" + AVRO_EXTENSION);

    Pipeline p = Pipeline.create(options);

    log.info("Adding step 2: Creating transformations");
    // Core
    VerbatimTransform verbatimTransform = VerbatimTransform.create();
    TemporalTransform temporalTransform = TemporalTransform.builder().create();
    LocationTransform locationTransform = LocationTransform.builder().create();
    TaxonomyTransform taxonomyTransform = TaxonomyTransform.builder().create();

    // ALA specific
    ALAUUIDTransform alaUuidTransform = ALAUUIDTransform.create();
    ALATaxonomyTransform alaTaxonomyTransform = ALATaxonomyTransform.builder().create();
    ALASensitiveDataRecordTransform alaSensitiveDataRecordTransform =
        ALASensitiveDataRecordTransform.builder()
            .datasetId(options.getDatasetId())
            .speciesStoreSupplier(SDSCheckKVStoreFactory.getInstanceSupplier(config))
            .reportStoreSupplier(SDSReportKVStoreFactory.getInstanceSupplier(config))
            .dataResourceStoreSupplier(ALAAttributionKVStoreFactory.getInstanceSupplier(config))
            .conservationServiceSupplier(SDSConservationServiceFactory.getInstanceSupplier(config))
            .erTag(verbatimTransform.getTag())
            .trTag(temporalTransform.getTag())
            .lrTag(locationTransform.getTag())
            .txrTag(USE_GBIF_TAXONOMY ? taxonomyTransform.getTag() : null)
            .atxrTag(alaTaxonomyTransform.getTag())
            .create();

    log.info("Adding step 3: Creating beam pipeline");
    PCollection<KV<String, ExtendedRecord>> inputVerbatimCollection =
        p.apply("Read Verbatim", verbatimTransform.read(inputPathFn))
            .apply("Map Verbatim to KV", verbatimTransform.toKv());

    PCollection<KV<String, TemporalRecord>> inputTemporalCollection =
        p.apply("Read Temporal", temporalTransform.read(inputPathFn))
            .apply("Map Temporal to KV", temporalTransform.toKv());

    PCollection<KV<String, LocationRecord>> inputLocationCollection =
        p.apply("Read Location", locationTransform.read(inputPathFn))
            .apply("Map Location to KV", locationTransform.toKv());

    PCollection<KV<String, TaxonRecord>> inputTaxonCollection = null;
    if (USE_GBIF_TAXONOMY) {
      inputTaxonCollection =
          p.apply("Read Location", taxonomyTransform.read(inputPathFn))
              .apply("Map Location to KV", taxonomyTransform.toKv());
    }

    // ALA Specific
    PCollection<KV<String, ALAUUIDRecord>> inputAlaUUidCollection =
        p.apply("Read Taxon", alaUuidTransform.read(identifiersPathFn))
            .apply("Map Taxon to KV", alaUuidTransform.toKv());

    PCollection<KV<String, ALATaxonRecord>> inputAlaTaxonCollection =
        p.apply("Read Taxon", alaTaxonomyTransform.read(inputPathFn))
            .apply("Map Taxon to KV", alaTaxonomyTransform.toKv());

    KeyedPCollectionTuple<String> inputTuples =
        KeyedPCollectionTuple
            // Core
            .of(verbatimTransform.getTag(), inputVerbatimCollection)
            .and(temporalTransform.getTag(), inputTemporalCollection)
            .and(locationTransform.getTag(), inputLocationCollection)
            // ALA Specific
            .and(alaUuidTransform.getTag(), inputAlaUUidCollection)
            .and(alaTaxonomyTransform.getTag(), inputAlaTaxonCollection);
    if (USE_GBIF_TAXONOMY)
      inputTuples = inputTuples.and(taxonomyTransform.getTag(), inputTaxonCollection);

    log.info("Creating sensitivity records");
    PCollection<ALASensitivityRecord> sensitivityRecords =
        inputTuples
            .apply("Grouping objects", CoGroupByKey.create())
            .apply("Converting to sensitvity records", alaSensitiveDataRecordTransform.interpret());

    log.info("Writing sensitivity records");
    sensitivityRecords.apply(alaSensitiveDataRecordTransform.write(outputPathFn));

    log.info("Running the pipeline");
    PipelineResult result = p.run();
    result.waitUntilFinish();

    MetricsHandler.saveCountersToTargetPathFile(options, result.metrics());

    log.info("Pipeline has been finished");
  }
}
