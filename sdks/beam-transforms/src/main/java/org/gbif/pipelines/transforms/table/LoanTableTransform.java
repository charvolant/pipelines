package org.gbif.pipelines.transforms.table;

import static org.gbif.pipelines.common.PipelinesVariables.Metrics.LOAN_TABLE_RECORDS_COUNT;
import static org.gbif.pipelines.common.PipelinesVariables.Pipeline.Interpretation.RecordType.LOAN_TABLE;

import java.util.Set;
import lombok.Builder;
import org.apache.beam.sdk.values.TupleTag;
import org.gbif.pipelines.core.converters.LoanTableConverter;
import org.gbif.pipelines.io.avro.BasicRecord;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.extension.ggbn.LoanTable;

public class LoanTableTransform extends TableTransform<LoanTable> {

  @Builder
  public LoanTableTransform(
      TupleTag<ExtendedRecord> extendedRecordTag,
      TupleTag<BasicRecord> basicRecordTag,
      String path,
      Integer numShards,
      Set<String> types) {
    super(
        LoanTable.class,
        LOAN_TABLE,
        LoanTableTransform.class.getName(),
        LOAN_TABLE_RECORDS_COUNT,
        LoanTableConverter::convert);
    this.setExtendedRecordTag(extendedRecordTag)
        .setBasicRecordTag(basicRecordTag)
        .setPath(path)
        .setNumShards(numShards)
        .setTypes(types);
  }
}
