package org.gbif.pipelines.ingest.java.transforms;

import static org.gbif.pipelines.common.PipelinesVariables.Metrics.AVRO_TO_HDFS_COUNT;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.Builder;
import lombok.NonNull;
import org.gbif.pipelines.common.beam.metrics.IngestMetrics;
import org.gbif.pipelines.core.converters.MultimediaConverter;
import org.gbif.pipelines.io.avro.AudubonRecord;
import org.gbif.pipelines.io.avro.BasicRecord;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.ImageRecord;
import org.gbif.pipelines.io.avro.LocationRecord;
import org.gbif.pipelines.io.avro.MetadataRecord;
import org.gbif.pipelines.io.avro.MultimediaRecord;
import org.gbif.pipelines.io.avro.OccurrenceHdfsRecord;
import org.gbif.pipelines.io.avro.TaxonRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;
import org.gbif.pipelines.io.avro.grscicoll.GrscicollRecord;

@Builder
public class OccurrenceHdfsRecordConverter {

  private final IngestMetrics metrics;

  @NonNull private final MetadataRecord metadata;
  @NonNull private final Map<String, ExtendedRecord> verbatimMap;
  @NonNull private final Map<String, TemporalRecord> temporalMap;
  @NonNull private final Map<String, LocationRecord> locationMap;
  @NonNull private final Map<String, TaxonRecord> taxonMap;
  @NonNull private final Map<String, GrscicollRecord> grscicollMap;
  @NonNull private final Map<String, MultimediaRecord> multimediaMap;
  @NonNull private final Map<String, ImageRecord> imageMap;
  @NonNull private final Map<String, AudubonRecord> audubonMap;

  /** Join all records, convert into OccurrenceHdfsRecord and save as an avro file */
  public Function<BasicRecord, Optional<OccurrenceHdfsRecord>> getFn() {
    return br -> {
      String k = br.getId();
      // Core
      ExtendedRecord er = verbatimMap.getOrDefault(k, ExtendedRecord.newBuilder().setId(k).build());
      TemporalRecord tr = temporalMap.getOrDefault(k, TemporalRecord.newBuilder().setId(k).build());
      LocationRecord lr = locationMap.getOrDefault(k, LocationRecord.newBuilder().setId(k).build());
      TaxonRecord txr = taxonMap.getOrDefault(k, TaxonRecord.newBuilder().setId(k).build());
      GrscicollRecord gr =
          grscicollMap.getOrDefault(k, GrscicollRecord.newBuilder().setId(k).build());
      // Extension
      MultimediaRecord mr =
          multimediaMap.getOrDefault(k, MultimediaRecord.newBuilder().setId(k).build());
      ImageRecord ir = imageMap.getOrDefault(k, ImageRecord.newBuilder().setId(k).build());
      AudubonRecord ar = audubonMap.getOrDefault(k, AudubonRecord.newBuilder().setId(k).build());

      MultimediaRecord mmr = MultimediaConverter.merge(mr, ir, ar);

      metrics.incMetric(AVRO_TO_HDFS_COUNT);

      OccurrenceHdfsRecord hdfsRecord =
          org.gbif.pipelines.core.converters.OccurrenceHdfsRecordConverter.builder()
              .basicRecord(br)
              .metadataRecord(metadata)
              .temporalRecord(tr)
              .locationRecord(lr)
              .taxonRecord(txr)
              .grscicollRecord(gr)
              .multimediaRecord(mmr)
              .extendedRecord(er)
              .build()
              .convert();

      return Optional.of(hdfsRecord);
    };
  }
}
