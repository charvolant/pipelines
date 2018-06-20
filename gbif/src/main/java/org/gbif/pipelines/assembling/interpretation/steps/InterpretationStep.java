package org.gbif.pipelines.assembling.interpretation.steps;

import org.gbif.pipelines.config.InterpretationType;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.issue.OccurrenceIssue;
import org.gbif.pipelines.transform.Kv2Value;
import org.gbif.pipelines.transform.RecordTransform;

import java.util.Objects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.avro.file.CodecFactory;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.io.AvroIO;
import org.apache.beam.sdk.io.FileSystems;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;

/** Defines an interpretation step create a {@link Pipeline}. */
public class InterpretationStep<T> {

  private static final String WRITE_DATA_MSG_PATTERN = "Write %s data to avro";
  private static final String WRITE_ISSUES_MSG_PATTERN = "Write %s issues to avro";

  // type create the interpretation
  private final InterpretationType interpretationType;
  // avro class create the result generated by this step
  private final Class<T> avroClass;
  // PTransformation to transfotm ExtendedRecord to T
  private final RecordTransform<ExtendedRecord, T> transform;
  // path where data will be written to
  private final String dataTargetPath;
  // path where issues will be written to
  private final String issuesTargetPath;
  // temp dir for beam
  private final String tempDir;
  // avro codec
  private final CodecFactory avroCodec;

  private InterpretationStep(Builder<T> builder) {
    this.interpretationType = builder.interpretationType;
    this.avroClass = builder.avroClass;
    this.transform = builder.transform;
    this.dataTargetPath = builder.dataTargetPath;
    this.issuesTargetPath = builder.issuesTargetPath;
    this.tempDir = builder.tempDir;
    this.avroCodec = builder.avroCodec;
  }

  public static <T> InterpretationTypeStep<T> newBuilder() {
    return new Builder<>();
  }

  /**
   * Appends the current create this interpretation step to the desired {@link Pipeline}.
   *
   * @param extendedRecords {@link PCollection} with the records that are gonna be interpreted by
   *     this step.
   */
  public void appendToPipeline(PCollection<ExtendedRecord> extendedRecords, Pipeline pipeline) {
    // add coders
    transform.withAvroCoders(pipeline);

    // apply transformation
    PCollectionTuple interpretedRecordTuple = extendedRecords.apply(transform);

    // Get data and save it to an avro file
    PCollection<T> interpretedRecords =
        interpretedRecordTuple.get(transform.getDataTag()).apply(Kv2Value.create());
    if (interpretedRecords != null) {
      interpretedRecords.apply(
          String.format(WRITE_DATA_MSG_PATTERN, interpretationType.name()),
          createAvroWriter(avroClass, dataTargetPath));
    }

    // Get issues and save them to an avro file
    PCollection<OccurrenceIssue> issues =
        interpretedRecordTuple.get(transform.getIssueTag()).apply(Kv2Value.create());
    if (issues != null) {
      issues.apply(
          String.format(WRITE_ISSUES_MSG_PATTERN, interpretationType.name()),
          createAvroWriter(OccurrenceIssue.class, issuesTargetPath));
    }
  }

  private <U> AvroIO.Write<U> createAvroWriter(Class<U> avroClass, String path) {
    AvroIO.Write<U> writer = AvroIO.write(avroClass).to(path).withSuffix(".avro");

    if (!Strings.isNullOrEmpty(tempDir)) {
      writer = writer.withTempDirectory(FileSystems.matchNewResource(tempDir, true));
    }

    if (!Objects.isNull(avroCodec)) {
      writer = writer.withCodec(avroCodec);
    }

    return writer;
  }

  private static class Builder<T>
      implements Build<T>,
          InterpretationTypeStep<T>,
          AvroClassStep<T>,
          TransformStep<T>,
          DataTargetPathStep<T>,
          IssuesTargetPathStep<T> {

    private InterpretationType interpretationType;
    private Class<T> avroClass;
    private RecordTransform<ExtendedRecord, T> transform;
    private String dataTargetPath;
    private String issuesTargetPath;
    private String tempDir;
    private CodecFactory avroCodec;

    @Override
    public AvroClassStep<T> interpretationType(InterpretationType interpretationType) {
      Objects.requireNonNull(interpretationType, "Interpretation type cannot be null");
      this.interpretationType = interpretationType;
      return this;
    }

    @Override
    public TransformStep<T> avroClass(Class<T> avroClass) {
      Objects.requireNonNull(avroClass, "Avro class cannot be null");
      this.avroClass = avroClass;
      return this;
    }

    @Override
    public DataTargetPathStep<T> transform(RecordTransform<ExtendedRecord, T> transform) {
      Objects.requireNonNull(transform, "RecordTransform cannot be null");
      this.transform = transform;
      return this;
    }

    @Override
    public IssuesTargetPathStep<T> dataTargetPath(String dataTargetPath) {
      Preconditions.checkArgument(
          !Strings.isNullOrEmpty(dataTargetPath), "DataTargetPath is required");
      this.dataTargetPath = dataTargetPath;
      return this;
    }

    @Override
    public Build<T> issuesTargetPath(String issuesTargetPath) {
      Preconditions.checkArgument(
          !Strings.isNullOrEmpty(issuesTargetPath), "IssuesTargetPath is required");
      this.issuesTargetPath = issuesTargetPath;
      return this;
    }

    @Override
    public Build<T> tempDirectory(String tempDir) {
      this.tempDir = tempDir;
      return this;
    }

    @Override
    public Build<T> avroCodec(CodecFactory avroCodec) {
      this.avroCodec = avroCodec;
      return this;
    }

    @Override
    public InterpretationStep<T> build() {
      return new InterpretationStep<>(this);
    }
  }

  public interface InterpretationTypeStep<T> {

    AvroClassStep<T> interpretationType(InterpretationType interpretationType);
  }

  public interface AvroClassStep<T> {

    TransformStep<T> avroClass(Class<T> avroClass);
  }

  public interface TransformStep<T> {

    DataTargetPathStep<T> transform(RecordTransform<ExtendedRecord, T> transform);
  }

  public interface DataTargetPathStep<T> {

    IssuesTargetPathStep<T> dataTargetPath(String dataTargetPath);
  }

  public interface IssuesTargetPathStep<T> {

    Build<T> issuesTargetPath(String issuesTargetPath);
  }

  public interface Build<T> {

    InterpretationStep<T> build();

    Build<T> tempDirectory(String tempDir);

    Build<T> avroCodec(CodecFactory codecFactory);
  }
}
