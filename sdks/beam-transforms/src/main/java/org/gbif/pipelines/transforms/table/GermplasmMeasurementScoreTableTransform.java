package org.gbif.pipelines.transforms.table;

import static org.gbif.pipelines.common.PipelinesVariables.Metrics.MEASUREMENT_SCORE_TABLE_RECORDS_COUNT;
import static org.gbif.pipelines.common.PipelinesVariables.Pipeline.Interpretation.RecordType.GERMPLASM_MEASUREMENT_SCORE_TABLE;

import java.util.Set;
import lombok.Builder;
import org.apache.beam.sdk.values.TupleTag;
import org.gbif.pipelines.core.converters.GermplasmMeasurementScoreTableConverter;
import org.gbif.pipelines.io.avro.BasicRecord;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.extension.germplasm.GermplasmMeasurementScoreTable;

public class GermplasmMeasurementScoreTableTransform
    extends TableTransform<GermplasmMeasurementScoreTable> {

  @Builder
  public GermplasmMeasurementScoreTableTransform(
      TupleTag<ExtendedRecord> extendedRecordTag,
      TupleTag<BasicRecord> basicRecordTag,
      String path,
      Integer numShards,
      Set<String> types) {
    super(
        GermplasmMeasurementScoreTable.class,
        GERMPLASM_MEASUREMENT_SCORE_TABLE,
        GermplasmMeasurementScoreTableTransform.class.getName(),
        MEASUREMENT_SCORE_TABLE_RECORDS_COUNT,
        GermplasmMeasurementScoreTableConverter::convert);
    this.setExtendedRecordTag(extendedRecordTag)
        .setBasicRecordTag(basicRecordTag)
        .setPath(path)
        .setNumShards(numShards)
        .setTypes(types);
  }
}
