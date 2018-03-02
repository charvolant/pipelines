package org.gbif.pipelines.transform;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwca.avro.ExtendedOccurrence;
import org.gbif.pipelines.core.TypeDescriptors;
import org.gbif.pipelines.io.avro.ExtendedRecord;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.beam.sdk.testing.NeedsRunner;
import org.apache.beam.sdk.testing.PAssert;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.Create;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.sdk.values.PCollectionTuple;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ExtendedOccurrenceTransformTest {

  @Rule
  public final transient TestPipeline p = TestPipeline.create();

  @Test
  @Category(NeedsRunner.class)
  public void testTransformation() {

    // State
    final String[] one = {"0", "OBSERVATION", "MALE", "INTRODUCED", "SPOROPHYTE", "HOLOTYPE", "2", "DENMARK", "DK", "EUROPE", "1", "1", "2018", "2018-01-01"};
    final String[] two = {"1", "UNKNOWN", "HERMAPHRODITE", "INTRODUCED", "GAMETE", "HAPANTOTYPE", "1", "JAPAN", "JP", "ASIA", "1", "1", "2018", "2018-01-01"};
    final List<ExtendedRecord> records = createExtendedRecordList(one, two);

    // Expected
    final List<ExtendedOccurrence> interpretedRecords = createInterpretedExtendedRecordList(one, two);

    // When
    ExtendedOccurrenceTransform occurrenceTransform = new ExtendedOccurrenceTransform().withAvroCoders(p);

    PCollection<ExtendedRecord> inputStream = p.apply(Create.of(records));

    PCollectionTuple tuple = inputStream.apply(occurrenceTransform);

    PCollection<ExtendedOccurrence> recordCollection = tuple.get(occurrenceTransform.getDataTag())
      .apply(MapElements.into(TypeDescriptors.extendedOccurrence()).via(KV::getValue));

    // Should
    PAssert.that(recordCollection).containsInAnyOrder(interpretedRecords);
    p.run();

  }

  private List<ExtendedRecord> createExtendedRecordList(String[]... records) {
    return Arrays.stream(records).map(x -> {
      ExtendedRecord record = ExtendedRecord.newBuilder().setId(x[0]).build();
      record.getCoreTerms().put(DwcTerm.basisOfRecord.qualifiedName(), x[1]);
      record.getCoreTerms().put(DwcTerm.sex.qualifiedName(), x[2]);
      record.getCoreTerms().put(DwcTerm.establishmentMeans.qualifiedName(), x[3]);
      record.getCoreTerms().put(DwcTerm.lifeStage.qualifiedName(), x[4]);
      record.getCoreTerms().put(DwcTerm.typeStatus.qualifiedName(), x[5]);
      record.getCoreTerms().put(DwcTerm.individualCount.qualifiedName(), x[6]);
      record.getCoreTerms().put(DwcTerm.country.qualifiedName(), x[7]);
      record.getCoreTerms().put(DwcTerm.countryCode.qualifiedName(), x[8]);
      record.getCoreTerms().put(DwcTerm.continent.qualifiedName(), x[9]);
      record.getCoreTerms().put(DwcTerm.day.qualifiedName(), x[10]);
      record.getCoreTerms().put(DwcTerm.month.qualifiedName(), x[11]);
      record.getCoreTerms().put(DwcTerm.year.qualifiedName(), x[12]);
      record.getCoreTerms().put(DwcTerm.eventDate.qualifiedName(), x[13]);

      return record;
    }).collect(Collectors.toList());
  }

  private List<ExtendedOccurrence> createInterpretedExtendedRecordList(String[]... records) {
    return Arrays.stream(records)
      .map(x -> ExtendedOccurrence.newBuilder()
        .setOccurrenceID(x[0])
        .setBasisOfRecord(x[1])
        .setSex(x[2])
        .setEstablishmentMeans(x[3])
        .setLifeStage(x[4])
        .setTypeStatus(x[5])
        .setIndividualCount(x[6])
        .setCountry(x[7])
        .setCountryCode(x[7])
        .setContinent(x[9])
        .setDay(Integer.valueOf(x[10]))
        .setMonth(Integer.valueOf(x[11]))
        .setYear(Integer.valueOf(x[12]))
        .setEventDate(x[13])
        .build())
      .collect(Collectors.toList());
  }

}